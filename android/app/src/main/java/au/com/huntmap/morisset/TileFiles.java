package au.com.huntmap.morisset;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.MessageDigest;

/**
 * Shared on-disk tile store. The WebView writes downloaded offline tiles here
 * (via TileStorePlugin) and the Android Auto renderer reads the same files.
 * Keys are canonical tile identifiers like "topo/15/19575/30158".
 */
public final class TileFiles {

    private TileFiles() {}

    public static File dir(Context c) {
        File d = new File(c.getFilesDir(), "tiles");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /** Network-fetched head-unit tiles that are not part of a saved area. */
    public static File cacheDir(Context c) {
        File d = new File(c.getCacheDir(), "cartiles");
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

    public static byte[] read(File f) {
        if (!f.exists()) return null;
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[(int) f.length()];
            int off = 0, n;
            while (off < buf.length && (n = in.read(buf, off, buf.length - off)) > 0) off += n;
            return buf;
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean write(File f, byte[] data) {
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(data);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
