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
