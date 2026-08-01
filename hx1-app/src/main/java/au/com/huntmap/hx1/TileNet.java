package au.com.huntmap.hx1;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Tile networking + storage for the HX-1 build. KitKat ships with TLS 1.2
 * disabled for client sockets, so all fetching happens natively through a
 * socket factory that force-enables it; the WebView never talks to tile hosts
 * directly (requests are intercepted and served from here).
 */
public final class TileNet {

    private TileNet() {}

    private static final String TOPO = "https://maps.six.nsw.gov.au/arcgis/rest/services/public/NSW_Topo_Map/MapServer/tile/";
    private static final String SAT = "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/";
    private static final String LOTS_JSON = "[{\"id\":108,\"source\":{\"type\":\"mapLayer\",\"mapLayerId\":8},"
        + "\"drawingInfo\":{\"renderer\":{\"type\":\"simple\",\"symbol\":{\"type\":\"esriSFS\",\"style\":\"esriSFSNull\","
        + "\"outline\":{\"type\":\"esriSLS\",\"style\":\"esriSLSSolid\",\"color\":[255,145,0,255],\"width\":1.2}}}}}]";
    private static final double WORLD = 2 * Math.PI * 6378137;
    private static final Pattern BBOX = Pattern.compile("[&?]bbox=([-0-9.eE,]+)");

    private static SSLSocketFactory tlsFactory;

    /** SSLSocketFactory that enables every protocol the platform knows. */
    public static synchronized SSLSocketFactory tls12Factory() {
        if (tlsFactory != null) return tlsFactory;
        try {
            SSLContext sc = SSLContext.getInstance("TLSv1.2");
            sc.init(null, null, null);
            final SSLSocketFactory base = sc.getSocketFactory();
            tlsFactory = new SSLSocketFactory() {
                private Socket patch(Socket s) {
                    if (s instanceof SSLSocket) {
                        ((SSLSocket) s).setEnabledProtocols(((SSLSocket) s).getSupportedProtocols());
                    }
                    return s;
                }
                @Override public String[] getDefaultCipherSuites() { return base.getDefaultCipherSuites(); }
                @Override public String[] getSupportedCipherSuites() { return base.getSupportedCipherSuites(); }
                @Override public Socket createSocket(Socket s, String h, int p, boolean a) throws IOException { return patch(base.createSocket(s, h, p, a)); }
                @Override public Socket createSocket(String h, int p) throws IOException { return patch(base.createSocket(h, p)); }
                @Override public Socket createSocket(String h, int p, InetAddress l, int lp) throws IOException { return patch(base.createSocket(h, p, l, lp)); }
                @Override public Socket createSocket(InetAddress h, int p) throws IOException { return patch(base.createSocket(h, p)); }
                @Override public Socket createSocket(InetAddress h, int p, InetAddress l, int lp) throws IOException { return patch(base.createSocket(h, p, l, lp)); }
            };
        } catch (Exception e) {
            tlsFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        }
        return tlsFactory;
    }

    public static File dir(Context c) {
        File d = new File(c.getFilesDir(), "tiles");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public static String hash(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] b = md.digest(key.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte x : b) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(key.hashCode());
        }
    }

    public static File fileFor(Context c, String key) {
        return new File(dir(c), hash(key));
    }

    /** URL -> canonical key ("topo/z/y/x", "sat/z/y/x", "lots/z/x/y") or null. */
    public static String canonKey(String url) {
        if (url == null) return null;
        int i = url.indexOf("NSW_Topo_Map/MapServer/tile/");
        if (i >= 0) return "topo/" + url.substring(i + "NSW_Topo_Map/MapServer/tile/".length());
        i = url.indexOf("World_Imagery/MapServer/tile/");
        if (i >= 0) return "sat/" + url.substring(i + "World_Imagery/MapServer/tile/".length());
        if (url.contains("NSW_Land_Parcel_Property_Theme/MapServer/export")) {
            Matcher m = BBOX.matcher(url);
            if (m.find()) {
                try {
                    String[] p = m.group(1).split(",");
                    double x0 = Double.parseDouble(p[0]);
                    double y1 = Double.parseDouble(p[3]);
                    double res = Double.parseDouble(p[2]) - x0;
                    int z = (int) Math.round(Math.log(WORLD / res) / Math.log(2));
                    long x = Math.round((x0 + WORLD / 2) / res);
                    long y = Math.round((WORLD / 2 - y1) / res);
                    return "lots/" + z + "/" + x + "/" + y;
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    /** canonical key -> network URL. */
    public static String urlFor(String key) {
        try {
            String[] p = key.split("/");
            if (key.startsWith("topo/")) return TOPO + p[1] + "/" + p[2] + "/" + p[3];
            if (key.startsWith("sat/")) return SAT + p[1] + "/" + p[2] + "/" + p[3];
            if (key.startsWith("lots/")) {
                int z = Integer.parseInt(p[1]);
                long x = Long.parseLong(p[2]);
                long y = Long.parseLong(p[3]);
                double res = WORLD / Math.pow(2, z);
                double x0 = -WORLD / 2 + x * res;
                double y1 = WORLD / 2 - y * res;
                return "https://portal.spatial.nsw.gov.au/server/rest/services/NSW_Land_Parcel_Property_Theme/MapServer/export"
                    + "?bboxSR=3857&imageSR=3857&size=256,256&transparent=true&format=png32&f=image&dynamicLayers="
                    + URLEncoder.encode(LOTS_JSON, "UTF-8")
                    + "&bbox=" + fmt(x0) + "," + fmt(y1 - res) + "," + fmt(x0 + res) + "," + fmt(y1);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static String fmt(double v) {
        return new java.math.BigDecimal(v).setScale(4, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    public static String mimeFor(String key) {
        return key.startsWith("lots/") ? "image/png" : "image/jpeg";
    }

    public static byte[] httpGet(String url) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            if (c instanceof HttpsURLConnection) {
                ((HttpsURLConnection) c).setSSLSocketFactory(tls12Factory());
            }
            c.setConnectTimeout(10000);
            c.setReadTimeout(20000);
            c.setRequestProperty("User-Agent", "HuntMap-HX1");
            if (c.getResponseCode() != 200) return null;
            InputStream in = c.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    public static byte[] read(File f) {
        if (!f.exists()) return null;
        try {
            FileInputStream in = new FileInputStream(f);
            byte[] buf = new byte[(int) f.length()];
            int off = 0, n;
            while (off < buf.length && (n = in.read(buf, off, buf.length - off)) > 0) off += n;
            in.close();
            return buf;
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean write(File f, byte[] data) {
        try {
            FileOutputStream out = new FileOutputStream(f);
            out.write(data);
            out.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
