package ru.fitprogress.app;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.webkit.JavascriptInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.graphics.Color;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** Offline personal training journal. No network permissions or data collection. */
public final class MainActivity extends Activity {
    private static final int EXPORT_JSON = 1601;
    private static final int IMPORT_JSON = 1602;
    private WebView web;
    private String pendingJson;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(10,18,32));
        getWindow().setNavigationBarColor(Color.rgb(18,29,48));
        getWindow().getDecorView().setSystemUiVisibility(0);
        web = new WebView(this);
        web.setBackgroundColor(Color.rgb(10,18,32));
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setLoadsImagesAutomatically(true);
        settings.setDefaultTextEncodingName("UTF-8");
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(), "Native");
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }

    public final class Bridge {
        @JavascriptInterface public void exportJson(final String json, final String name) {
            runOnUiThread(new Runnable() { @Override public void run() {
                pendingJson = json;
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                intent.putExtra(Intent.EXTRA_TITLE, name);
                startActivityForResult(intent, EXPORT_JSON);
            }});
        }
        @JavascriptInterface public void pickBackup() {
            runOnUiThread(new Runnable() { @Override public void run() {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                startActivityForResult(intent, IMPORT_JSON);
            }});
        }
        @JavascriptInterface public void shareText(final String text) {
            runOnUiThread(new Runnable() { @Override public void run() {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_SUBJECT, "Отчёт АТЛЕТ");
                send.putExtra(Intent.EXTRA_TEXT, text);
                startActivity(Intent.createChooser(send, "Отправить отчёт"));
            }});
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == EXPORT_JSON && pendingJson != null) {
            try (OutputStream stream = getContentResolver().openOutputStream(uri)) {
                if (stream != null) stream.write(pendingJson.getBytes(StandardCharsets.UTF_8));
                pendingJson = null;
            } catch (Exception ignored) {
                runJs("alert('Не удалось сохранить отчёт. Попробуй снова.')");
            }
        } else if (requestCode == IMPORT_JSON) {
            try (InputStream stream = getContentResolver().openInputStream(uri);
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                if (stream == null) throw new IllegalStateException("No input");
                byte[] buffer = new byte[8192];
                int n;
                while ((n = stream.read(buffer)) > 0) {
                    out.write(buffer, 0, n);
                    if (out.size() > 8000000) throw new IllegalStateException("Backup file too large");
                }
                runJs("window.restoreFromNative(" + JSONObject.quote(out.toString("UTF-8")) + ")");
            } catch (Exception ignored) {
                runJs("alert('Не удалось открыть резервную копию.')");
            }
        }
    }

    private void runJs(final String js) {
        runOnUiThread(new Runnable() { @Override public void run() {
            web.evaluateJavascript(js, null);
        }});
    }
    @Override public void onConfigurationChanged(Configuration config) { super.onConfigurationChanged(config); }
    @Override public void onBackPressed() { super.onBackPressed(); }
    @Override protected void onDestroy() {
        if (web != null) { web.destroy(); web = null; }
        super.onDestroy();
    }
}
