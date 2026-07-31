package au.com.huntmap.morisset;

import android.util.Base64;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.File;

/**
 * Bridges the web app's offline tile store to the filesystem so the Android
 * Auto surface renderer can use the same downloaded tiles.
 */
@CapacitorPlugin(name = "TileStore")
public class TileStorePlugin extends Plugin {

    @PluginMethod
    public void put(PluginCall call) {
        String key = call.getString("url");
        String data = call.getString("data");
        if (key == null || data == null) { call.reject("url and data required"); return; }
        byte[] bytes;
        try { bytes = Base64.decode(data, Base64.DEFAULT); }
        catch (Exception e) { call.reject("bad base64"); return; }
        boolean ok = TileFiles.write(TileFiles.fileFor(getContext(), key), bytes);
        if (ok) call.resolve(); else call.reject("write failed");
    }

    @PluginMethod
    public void get(PluginCall call) {
        String key = call.getString("url");
        JSObject ret = new JSObject();
        if (key != null) {
            byte[] bytes = TileFiles.read(TileFiles.fileFor(getContext(), key));
            if (bytes != null) ret.put("data", Base64.encodeToString(bytes, Base64.NO_WRAP));
        }
        call.resolve(ret);
    }

    @PluginMethod
    public void carDiag(PluginCall call) {
        JSObject ret = new JSObject();
        StringBuilder sb = new StringBuilder();
        android.content.Context ctx = getContext();
        try {
            sb.append("Hunt Map ").append(ctx.getPackageManager()
                .getPackageInfo(ctx.getPackageName(), 0).versionName)
              .append(" · Android ").append(android.os.Build.VERSION.RELEASE)
              .append(" · ").append(android.os.Build.MODEL).append("\n");
        } catch (Exception e) { sb.append("(version lookup failed)\n"); }

        // Is Android Auto installed, and which version?
        try {
            android.content.pm.PackageInfo aa = ctx.getPackageManager()
                .getPackageInfo("com.google.android.projection.gearhead", 0);
            sb.append("Android Auto installed: v").append(aa.versionName).append("\n");
        } catch (Exception e) {
            sb.append("Android Auto NOT VISIBLE/INSTALLED\n");
        }

        // Does the phone resolve car-app services (ours and others)?
        try {
            android.content.Intent it = new android.content.Intent("androidx.car.app.CarAppService");
            java.util.List<android.content.pm.ResolveInfo> all =
                ctx.getPackageManager().queryIntentServices(it, 0);
            boolean self = false;
            sb.append("Car app services visible to the phone:\n");
            for (android.content.pm.ResolveInfo ri : all) {
                sb.append("  - ").append(ri.serviceInfo.packageName)
                  .append("/").append(ri.serviceInfo.name).append("\n");
                if (ctx.getPackageName().equals(ri.serviceInfo.packageName)) self = true;
            }
            if (all.isEmpty()) sb.append("  (none)\n");
            sb.append(self ? "OUR service RESOLVES correctly.\n"
                           : "OUR service DOES NOT RESOLVE — manifest problem.\n");
        } catch (Exception e) {
            sb.append("service query failed: ").append(e.getMessage()).append("\n");
        }

        String log = CarLog.read(ctx);
        sb.append("\n--- Car connection log ---\n")
          .append(log.isEmpty()
            ? "(empty — Android Auto has NEVER bound to the app.\n If the checks above are OK, Android Auto itself is\n filtering the app out: recheck Unknown sources +\n Application mode: Developer, then replug.)"
            : log);
        ret.put("report", sb.toString());
        call.resolve(ret);
    }

    @PluginMethod
    public void deleteMany(PluginCall call) {
        JSArray urls = call.getArray("urls");
        int deleted = 0;
        if (urls != null) {
            for (int i = 0; i < urls.length(); i++) {
                try {
                    File f = TileFiles.fileFor(getContext(), urls.getString(i));
                    if (f.exists() && f.delete()) deleted++;
                } catch (Exception ignored) {}
            }
        }
        JSObject ret = new JSObject();
        ret.put("deleted", deleted);
        call.resolve(ret);
    }
}
