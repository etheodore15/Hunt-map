package au.com.huntmap.morisset;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Tiny persistent event log for the Android Auto integration, so the phone UI
 * can show whether the head unit ever bound to the car app service — without
 * needing adb.
 */
public final class CarLog {

    private CarLog() {}

    private static File file(Context c) {
        return new File(c.getFilesDir(), "carlog.txt");
    }

    public static synchronized void log(Context c, String msg) {
        try {
            File f = file(c);
            if (f.length() > 100_000) {                       // keep the tail
                byte[] all = TileFiles.read(f);
                byte[] tail = new byte[50_000];
                System.arraycopy(all, all.length - tail.length, tail, 0, tail.length);
                TileFiles.write(f, tail);
            }
            String line = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(new Date())
                + "  " + msg + "\n";
            try (FileOutputStream out = new FileOutputStream(f, true)) {
                out.write(line.getBytes("UTF-8"));
            }
        } catch (Exception ignored) {}
    }

    public static String read(Context c) {
        byte[] b = TileFiles.read(file(c));
        try { return b == null ? "" : new String(b, "UTF-8"); }
        catch (Exception e) { return ""; }
    }
}
