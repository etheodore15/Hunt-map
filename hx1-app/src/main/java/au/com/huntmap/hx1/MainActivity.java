package au.com.huntmap.hx1;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Minimal WebView shell for the Hema HX-1 (Android 4.4). The 2013 WebView
 * can't be trusted with modern TLS or big JS, so tiles are intercepted and
 * served/fetched natively, and trips are recorded by a native service.
 */
public class MainActivity extends Activity {

    private WebView web;
    private final AtomicBoolean dlCancel = new AtomicBoolean(false);

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setGeolocationEnabled(true);
        s.setGeolocationDatabasePath(getFilesDir().getPath());
        s.setAllowFileAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);

        web.addJavascriptInterface(new Bridge(), "HXBridge");

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb) {
                cb.invoke(origin, true, false);
            }
        });

        web.setWebViewClient(new WebViewClient() {
            @Override
            @SuppressWarnings("deprecation")
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                String key = TileNet.canonKey(url);
                if (key == null) return null;
                byte[] bytes = TileNet.read(TileNet.fileFor(MainActivity.this, key));
                if (bytes == null) {
                    bytes = TileNet.httpGet(url);   // native TLS 1.2 fetch
                }
                if (bytes == null) return null;
                return new WebResourceResponse(TileNet.mimeFor(key), null,
                    new ByteArrayInputStream(bytes));
            }
        });

        web.loadUrl("file:///android_asset/www/index.html");
    }

    private void js(final String script) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                try { web.evaluateJavascript(script, null); } catch (Exception ignored) {}
            }
        });
    }

    private class Bridge {

        @JavascriptInterface
        public String version() { return "hx1-1.0"; }

        @JavascriptInterface
        public void trackStart() {
            TrackService.trackFile(MainActivity.this).delete();
            Intent it = new Intent(MainActivity.this, TrackService.class);
            it.setAction(TrackService.ACTION_START);
            startService(it);
        }

        private String pointsJson(boolean running) {
            try {
                JSONObject o = new JSONObject();
                JSONArray arr = new JSONArray();
                for (double[] p : TrackService.readPoints(MainActivity.this)) {
                    JSONArray row = new JSONArray();
                    row.put(p[0]); row.put(p[1]); row.put((long) p[2]);
                    arr.put(row);
                }
                o.put("running", running);
                o.put("points", arr);
                return o.toString();
            } catch (Exception e) {
                return "{\"running\":false,\"points\":[]}";
            }
        }

        @JavascriptInterface
        public String trackStop() {
            Intent it = new Intent(MainActivity.this, TrackService.class);
            it.setAction(TrackService.ACTION_STOP);
            startService(it);
            String out = pointsJson(false);
            TrackService.trackFile(MainActivity.this).delete();
            return out;
        }

        @JavascriptInterface
        public String trackPoll() {
            return pointsJson(TrackService.RUNNING);
        }

        @JavascriptInterface
        public void deleteKeys(String json) {
            try {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    File f = TileNet.fileFor(MainActivity.this, arr.getString(i));
                    if (f.exists()) f.delete();
                }
            } catch (Exception ignored) {}
        }

        @JavascriptInterface
        public void cancelDownload() { dlCancel.set(true); }

        @JavascriptInterface
        public void downloadArea(final String keysJson) {
            dlCancel.set(false);
            new Thread(new Runnable() {
                @Override public void run() {
                    int done = 0, failed = 0;
                    long bytes = 0;
                    JSONArray keys;
                    try { keys = new JSONArray(keysJson); }
                    catch (Exception e) { js("__hxProgress(0,0,0,0,true)"); return; }
                    int total = keys.length();
                    for (int i = 0; i < total; i++) {
                        if (dlCancel.get()) break;
                        try {
                            String key = keys.getString(i);
                            File f = TileNet.fileFor(MainActivity.this, key);
                            if (!f.exists()) {
                                String url = TileNet.urlFor(key);
                                byte[] b = url == null ? null : TileNet.httpGet(url);
                                if (b == null) { failed++; }
                                else { TileNet.write(f, b); bytes += b.length; }
                            } else {
                                bytes += f.length();
                            }
                        } catch (Exception e) { failed++; }
                        done++;
                        if (done % 10 == 0) {
                            js("__hxProgress(" + done + "," + total + "," + failed + "," + bytes + ",false)");
                        }
                    }
                    js("__hxProgress(" + done + "," + total + "," + failed + "," + bytes + ",true)");
                }
            }).start();
        }
    }
}
