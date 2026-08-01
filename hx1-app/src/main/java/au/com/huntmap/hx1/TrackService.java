package au.com.huntmap.hx1;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/** Foreground trip recorder for the HX-1 (API 19), same file format as the phone app. */
public class TrackService extends Service {

    public static final String ACTION_START = "hx1.TRACK_START";
    public static final String ACTION_STOP = "hx1.TRACK_STOP";
    public static volatile boolean RUNNING = false;

    private static final float ACC_MAX = 35f;
    private static final float MIN_STEP = 5f;
    private static final float SPEED_MAX = 55f;

    private LocationManager lm;
    private Location last;
    private Location pending;

    public static File trackFile(Context c) {
        return new File(c.getFilesDir(), "track-active.csv");
    }

    public static List<double[]> readPoints(Context c) {
        List<double[]> out = new ArrayList<double[]>();
        byte[] b = TileNet.read(trackFile(c));
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
        public void onLocationChanged(Location loc) {
            if (!loc.hasAccuracy() || loc.getAccuracy() > ACC_MAX) return;
            if (last != null) {
                float d = last.distanceTo(loc);
                if (d < MIN_STEP) return;
                double dt = (loc.getTime() - last.getTime()) / 1000.0;
                if (dt > 0 && d / dt > SPEED_MAX) {
                    if (pending != null && pending.distanceTo(loc) < 100) {
                        append(pending);
                        last = pending;
                        pending = null;
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
        @Override public void onStatusChanged(String p, int s, Bundle e) {}
        @Override public void onProviderEnabled(String p) {}
        @Override public void onProviderDisabled(String p) {}
    };

    private void append(Location loc) {
        try {
            FileOutputStream out = new FileOutputStream(trackFile(this), true);
            out.write(String.format(java.util.Locale.US, "%.6f,%.6f,%d%n",
                loc.getLatitude(), loc.getLongitude(), loc.getTime()).getBytes("UTF-8"));
            out.close();
        } catch (Exception ignored) {}
    }

    @Override
    @SuppressWarnings("deprecation")
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopRecording();
            stopSelf();
            return START_NOT_STICKY;
        }
        Notification n = new Notification.Builder(this)
            .setContentTitle("Hunt Map HX")
            .setContentText("Trip recording")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build();
        startForeground(4242, n);
        if (!RUNNING) {
            RUNNING = true;
            last = null;
            pending = null;
            try {
                lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0,
                    listener, Looper.getMainLooper());
            } catch (SecurityException e) {
                stopSelf();
            }
        }
        return START_STICKY;
    }

    private void stopRecording() {
        RUNNING = false;
        try { if (lm != null) lm.removeUpdates(listener); } catch (Exception ignored) {}
    }

    @Override
    public void onDestroy() {
        stopRecording();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
