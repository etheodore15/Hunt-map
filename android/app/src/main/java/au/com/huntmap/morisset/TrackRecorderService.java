package au.com.huntmap.morisset;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Native trip recorder. The WebView's JavaScript is suspended whenever the app
 * loses focus, so trip points must be captured here — a foreground service that
 * appends every accepted GPS fix to a file. The web layer polls/merges that
 * file when it resumes, and reads it in full when the trip is stopped.
 */
public class TrackRecorderService extends Service {

    public static final String ACTION_START = "au.com.huntmap.morisset.TRACK_START";
    public static final String ACTION_STOP = "au.com.huntmap.morisset.TRACK_STOP";
    private static final String CHANNEL = "huntmap_track";
    private static final int NOTIF_ID = 4242;

    private static final float ACC_MAX = 35f;      // m — reject coarse fixes
    private static final float MIN_STEP = 5f;      // m — thin jitter
    private static final float SPEED_MAX = 55f;    // m/s — teleport gate

    public static volatile boolean RUNNING = false;

    private LocationManager lm;
    private Location last;
    private Location pending;

    public static File trackFile(Context c) {
        return new File(c.getFilesDir(), "track-active.csv");
    }

    /** Parse the active/finished track file into [lat, lng, ts] triples. */
    public static List<double[]> readPoints(Context c) {
        List<double[]> out = new ArrayList<>();
        byte[] b = TileFiles.read(trackFile(c));
        if (b == null) return out;
        try {
            for (String line : new String(b, "UTF-8").split("\n")) {
                String[] p = line.split(",");
                if (p.length >= 3) {
                    out.add(new double[]{ Double.parseDouble(p[0]),
                        Double.parseDouble(p[1]), Double.parseDouble(p[2]) });
                }
            }
        } catch (Exception ignored) {}
        return out;
    }

    private final LocationListener listener = new LocationListener() {
        @Override
        public void onLocationChanged(@NonNull Location loc) {
            if (!loc.hasAccuracy() || loc.getAccuracy() > ACC_MAX) return;
            if (last != null) {
                float d = last.distanceTo(loc);
                if (d < MIN_STEP) return;
                double dt = (loc.getTime() - last.getTime()) / 1000.0;
                if (dt > 0 && d / dt > SPEED_MAX) {
                    // impossible jump — hold until a second fix agrees
                    if (pending != null && pending.distanceTo(loc) < 100) {
                        append(pending);
                        last = pending;
                        pending = null;
                        // fall through to also record this fix
                    } else {
                        pending = loc;
                        return;
                    }
                }
            }
            pending = null;
            append(loc);
            last = loc;
        }
        @Override public void onProviderEnabled(@NonNull String p) {}
        @Override public void onProviderDisabled(@NonNull String p) {}
    };

    private void append(Location loc) {
        try (FileOutputStream out = new FileOutputStream(trackFile(this), true)) {
            String line = String.format(java.util.Locale.US, "%.6f,%.6f,%d%n",
                loc.getLatitude(), loc.getLongitude(), loc.getTime());
            out.write(line.getBytes("UTF-8"));
        } catch (Exception ignored) {}
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopRecording();
            stopSelf();
            return START_NOT_STICKY;
        }
        startInForeground();
        if (!RUNNING) {
            RUNNING = true;
            last = null;
            pending = null;
            try {
                lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0,
                    listener, Looper.getMainLooper());
                CarLog.log(this, "track recorder started");
            } catch (SecurityException e) {
                CarLog.log(this, "track recorder: no location permission");
                stopSelf();
            }
        }
        return START_STICKY;   // restart after a system kill; the file persists
    }

    private void startInForeground() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL,
                "Trip recording", NotificationManager.IMPORTANCE_LOW));
        }
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        Notification n = b.setContentTitle("Hunt Map")
            .setContentText("Trip recording — the track keeps logging with the app in the background")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    private void stopRecording() {
        RUNNING = false;
        try { if (lm != null) lm.removeUpdates(listener); } catch (Exception ignored) {}
        CarLog.log(this, "track recorder stopped");
    }

    @Override
    public void onDestroy() {
        stopRecording();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
