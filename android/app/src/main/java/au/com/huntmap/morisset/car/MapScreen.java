package au.com.huntmap.morisset.car;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.car.app.AppManager;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.SurfaceCallback;
import androidx.car.app.SurfaceContainer;
import androidx.car.app.model.Action;
import androidx.car.app.model.ActionStrip;
import androidx.car.app.model.Template;
import androidx.car.app.navigation.model.NavigationTemplate;
import androidx.core.content.ContextCompat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import au.com.huntmap.morisset.TileFiles;

/**
 * Draws the Hunt Map layers on the Android Auto surface: satellite or NSW
 * topographic base tiles (offline store first, then network), optional
 * cadastral lot lines, and the live GPS position. Pan and pinch come from the
 * head unit's touch surface; the action strip has zoom, re-centre and a
 * layer cycle (Sat / Topo / Sat+Lots / Topo+Lots).
 */
public class MapScreen extends Screen implements SurfaceCallback {

    private static final String SAT = "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/%d/%d/%d";
    private static final String TOPO = "https://maps.six.nsw.gov.au/arcgis/rest/services/public/NSW_Topo_Map/MapServer/tile/%d/%d/%d";
    private static final String LOTS_JSON = "[{\"id\":108,\"source\":{\"type\":\"mapLayer\",\"mapLayerId\":8},"
        + "\"drawingInfo\":{\"renderer\":{\"type\":\"simple\",\"symbol\":{\"type\":\"esriSFS\",\"style\":\"esriSFSNull\","
        + "\"outline\":{\"type\":\"esriSLS\",\"style\":\"esriSLSSolid\",\"color\":[255,145,0,255],\"width\":1.2}}}}}]";
    private static final double WORLD = 2 * Math.PI * 6378137;

    private SurfaceContainer surface;
    private double centerLat = -33.06, centerLng = 151.37;
    private double zoom = 12;
    private boolean follow = true;
    private int base = 0;                    // 0 = satellite, 1 = NSW topo
    private boolean lots = false;
    private Location fix;

    private final LruCache<String, Bitmap> mem = new LruCache<>(96);
    private final Set<String> loading = Collections.synchronizedSet(new HashSet<String>());
    private final Set<String> failed = Collections.synchronizedSet(new HashSet<String>());
    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private final Handler main = new Handler(Looper.getMainLooper());
    private String lotsExportPrefix;

    private final LocationListener locListener = new LocationListener() {
        @Override public void onLocationChanged(@NonNull Location l) {
            fix = l;
            if (follow) { centerLat = l.getLatitude(); centerLng = l.getLongitude(); }
            draw();
        }
        @Override public void onProviderEnabled(@NonNull String p) {}
        @Override public void onProviderDisabled(@NonNull String p) {}
    };

    public MapScreen(@NonNull CarContext ctx) {
        super(ctx);
        try {
            lotsExportPrefix = "https://portal.spatial.nsw.gov.au/server/rest/services/NSW_Land_Parcel_Property_Theme/MapServer/export"
                + "?bboxSR=3857&imageSR=3857&size=256,256&transparent=true&format=png32&f=image&dynamicLayers="
                + URLEncoder.encode(LOTS_JSON, "UTF-8");
        } catch (Exception e) {
            lotsExportPrefix = null;
        }
        ctx.getCarService(AppManager.class).setSurfaceCallback(this);
        startLocation();
        au.com.huntmap.morisset.CarLog.log(ctx, "map screen created");
    }

    private void startLocation() {
        CarContext ctx = getCarContext();
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;   // granted on the phone app's first GPS use
        try {
            LocationManager lm = (LocationManager) ctx.getSystemService(CarContext.LOCATION_SERVICE);
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, locListener, Looper.getMainLooper());
            Location last = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (last != null) locListener.onLocationChanged(last);
        } catch (SecurityException ignored) {}
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        String layerLabel = (base == 0 ? "Sat" : "Topo") + (lots ? "+Lots" : "");
        ActionStrip strip = new ActionStrip.Builder()
            .addAction(new Action.Builder().setTitle("+").setOnClickListener(() -> { zoomBy(1); }).build())
            .addAction(new Action.Builder().setTitle("−").setOnClickListener(() -> { zoomBy(-1); }).build())
            .addAction(new Action.Builder().setTitle("◎").setOnClickListener(() -> {
                follow = true;
                if (fix != null) { centerLat = fix.getLatitude(); centerLng = fix.getLongitude(); }
                draw();
            }).build())
            .addAction(new Action.Builder().setTitle(layerLabel).setOnClickListener(() -> {
                // cycle Sat -> Topo -> Sat+Lots -> Topo+Lots
                if (!lots && base == 0) { base = 1; }
                else if (!lots) { base = 0; lots = true; }
                else if (base == 0) { base = 1; }
                else { base = 0; lots = false; }
                invalidate();
                draw();
            }).build())
            .build();
        return new NavigationTemplate.Builder()
            .setActionStrip(strip)
            .setPanModeListener(inPan -> {})
            .build();
    }

    private void zoomBy(double d) {
        zoom = Math.max(5, Math.min(17, zoom + d));
        draw();
    }

    // --- SurfaceCallback ---
    @Override public void onSurfaceAvailable(@NonNull SurfaceContainer c) {
        surface = c;
        au.com.huntmap.morisset.CarLog.log(getCarContext(), "surface available " + c.getWidth() + "x" + c.getHeight());
        draw();
    }
    @Override public void onSurfaceDestroyed(@NonNull SurfaceContainer c) { surface = null; }
    @Override public void onVisibleAreaChanged(@NonNull Rect r) { draw(); }
    @Override public void onStableAreaChanged(@NonNull Rect r) { draw(); }

    @Override
    public void onScroll(float dx, float dy) {
        int iz = (int) Math.floor(zoom);
        double frac = Math.pow(2, zoom - iz);
        double tilePx = 256 * frac;
        double n = Math.pow(2, iz);
        double cx = (centerLng + 180) / 360 * n;
        double cy = (1 - Math.log(Math.tan(Math.toRadians(centerLat)) + 1 / Math.cos(Math.toRadians(centerLat))) / Math.PI) / 2 * n;
        cx += dx / tilePx;
        cy += dy / tilePx;
        centerLng = cx / n * 360 - 180;
        centerLat = Math.toDegrees(Math.atan(Math.sinh(Math.PI * (1 - 2 * cy / n))));
        follow = false;
        draw();
    }

    @Override
    public void onScale(float fx, float fy, float factor) {
        zoom = Math.max(5, Math.min(17, zoom + Math.log(factor) / Math.log(2)));
        draw();
    }

    // --- tile pipeline ---
    private String baseKey(int z, int x, int y) {
        return (base == 0 ? "sat/" : "topo/") + z + "/" + y + "/" + x;
    }
    private String baseUrl(int z, int x, int y) {
        return String.format(base == 0 ? SAT : TOPO, z, y, x);
    }
    private String lotsKey(int z, int x, int y) { return "lots/" + z + "/" + x + "/" + y; }
    private String lotsUrl(int z, int x, int y) {
        if (lotsExportPrefix == null) return null;
        double res = WORLD / Math.pow(2, z);
        double x0 = -WORLD / 2 + x * res;
        double y1 = WORLD / 2 - y * res;
        return lotsExportPrefix + "&bbox=" + fmt(x0) + "," + fmt(y1 - res) + "," + fmt(x0 + res) + "," + fmt(y1);
    }
    private static String fmt(double v) {
        // plain decimal, no scientific notation (server-side parse only)
        return new java.math.BigDecimal(v).setScale(4, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private Bitmap tile(String key, String url) {
        Bitmap bm = mem.get(key);
        if (bm != null) return bm;
        if (failed.contains(key) || loading.contains(key)) return null;
        loading.add(key);
        pool.submit(() -> {
            Bitmap loaded = null;
            try {
                byte[] bytes = TileFiles.read(TileFiles.fileFor(getCarContext(), key));
                if (bytes == null) {
                    File cached = new File(TileFiles.cacheDir(getCarContext()), TileFiles.hash(key));
                    bytes = TileFiles.read(cached);
                    if (bytes == null && url != null) {
                        bytes = httpGet(url);
                        if (bytes != null) TileFiles.write(cached, bytes);
                    }
                }
                if (bytes != null) loaded = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            } catch (Exception ignored) {}
            loading.remove(key);
            if (loaded != null) {
                mem.put(key, loaded);
                main.post(this::draw);
            } else {
                failed.add(key);
                if (failed.size() > 512) failed.clear();
            }
        });
        return null;
    }

    private static byte[] httpGet(String url) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(12000);
            c.setRequestProperty("User-Agent", "HuntMap-Auto");
            if (c.getResponseCode() != 200) return null;
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                return out.toByteArray();
            }
        } catch (Exception e) {
            return null;
        }
    }

    // --- drawing ---
    private void draw() {
        SurfaceContainer sc = surface;
        if (sc == null || sc.getSurface() == null || !sc.getSurface().isValid()) return;
        Canvas c;
        try { c = sc.getSurface().lockCanvas(null); }
        catch (Exception e) { return; }
        try { render(c, sc.getWidth(), sc.getHeight()); }
        finally {
            try { sc.getSurface().unlockCanvasAndPost(c); } catch (Exception ignored) {}
        }
    }

    private void render(Canvas c, int w, int h) {
        c.drawColor(Color.rgb(18, 20, 22));
        int iz = (int) Math.floor(zoom);
        double frac = Math.pow(2, zoom - iz);
        double tilePx = 256 * frac;
        double n = Math.pow(2, iz);
        double cx = (centerLng + 180) / 360 * n;
        double cy = (1 - Math.log(Math.tan(Math.toRadians(centerLat)) + 1 / Math.cos(Math.toRadians(centerLat))) / Math.PI) / 2 * n;

        int tx0 = (int) Math.floor(cx - w / 2.0 / tilePx);
        int tx1 = (int) Math.floor(cx + w / 2.0 / tilePx);
        int ty0 = (int) Math.floor(cy - h / 2.0 / tilePx);
        int ty1 = (int) Math.floor(cy + h / 2.0 / tilePx);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
        for (int tx = tx0; tx <= tx1; tx++) {
            for (int ty = ty0; ty <= ty1; ty++) {
                if (ty < 0 || ty >= n) continue;
                int wx = (int) (((tx % (long) n) + (long) n) % (long) n);
                float sx = (float) (w / 2.0 + (tx - cx) * tilePx);
                float sy = (float) (h / 2.0 + (ty - cy) * tilePx);
                RectF dst = new RectF(sx, sy, (float) (sx + tilePx), (float) (sy + tilePx));
                Bitmap bm = tile(baseKey(iz, wx, ty), baseUrl(iz, wx, ty));
                if (bm != null) c.drawBitmap(bm, null, dst, p);
                if (lots && iz >= 13) {
                    Bitmap lb = tile(lotsKey(iz, wx, ty), lotsUrl(iz, wx, ty));
                    if (lb != null) c.drawBitmap(lb, null, dst, p);
                }
            }
        }

        // GPS puck
        if (fix != null) {
            double fx = (fix.getLongitude() + 180) / 360 * n;
            double fy = (1 - Math.log(Math.tan(Math.toRadians(fix.getLatitude())) + 1 / Math.cos(Math.toRadians(fix.getLatitude()))) / Math.PI) / 2 * n;
            float px = (float) (w / 2.0 + (fx - cx) * tilePx);
            float py = (float) (h / 2.0 + (fy - cy) * tilePx);
            Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
            ring.setColor(Color.argb(50, 33, 150, 243));
            float accPx = (float) (fix.getAccuracy() / (WORLD * Math.cos(Math.toRadians(fix.getLatitude())) / (n * tilePx)));
            if (accPx > 8 && accPx < w) c.drawCircle(px, py, accPx, ring);
            Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
            halo.setColor(Color.WHITE);
            c.drawCircle(px, py, 13, halo);
            Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
            dot.setColor(Color.rgb(33, 150, 243));
            c.drawCircle(px, py, 9, dot);
            if (fix.hasBearing()) {
                Paint dir = new Paint(Paint.ANTI_ALIAS_FLAG);
                dir.setColor(Color.rgb(33, 150, 243));
                dir.setStrokeWidth(6);
                double b = Math.toRadians(fix.getBearing());
                c.drawLine(px, py, px + (float) (Math.sin(b) * 26), py - (float) (Math.cos(b) * 26), dir);
            }
        }
    }
}
