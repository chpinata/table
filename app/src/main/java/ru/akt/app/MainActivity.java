package ru.akt.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import java.io.OutputStream;

public class MainActivity extends Activity {

    private static final String DOCX_MIME =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private WebView web;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("akt", MODE_PRIVATE);

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new Bridge(), "Android");
        web.loadUrl("file:///android_asset/index.html");
        setContentView(web);
    }

    /** Методы, доступные странице как window.Android.* */
    private class Bridge {

        /** Записи хранятся в памяти приложения и переживают перезапуск. */
        @JavascriptInterface
        public String load() {
            return prefs.getString("data", "");
        }

        @JavascriptInterface
        public void save(String json) {
            prefs.edit().putString("data", json).commit();
        }

        /** Кладёт DOCX в общую папку «Загрузки» и открывает его. */
        @JavascriptInterface
        public boolean saveDocx(String fileName, String base64) {
            ContentResolver resolver = getContentResolver();
            Uri uri = null;
            try {
                byte[] bytes = Base64.decode(base64, Base64.DEFAULT);

                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                values.put(MediaStore.Downloads.MIME_TYPE, DOCX_MIME);
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                values.put(MediaStore.Downloads.IS_PENDING, 1);

                uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new IllegalStateException("insert failed");

                try (OutputStream out = resolver.openOutputStream(uri)) {
                    if (out == null) throw new IllegalStateException("no stream");
                    out.write(bytes);
                }

                values.clear();
                values.put(MediaStore.Downloads.IS_PENDING, 0);
                resolver.update(uri, values, null, null);

                final Uri saved = uri;
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this,
                            "Сохранено в «Загрузки»: " + fileName, Toast.LENGTH_LONG).show();
                    openFile(saved);
                });
                return true;
            } catch (Exception e) {
                if (uri != null) {
                    try { resolver.delete(uri, null, null); } catch (Exception ignored) { }
                }
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "Не удалось сохранить файл: " + e.getMessage(), Toast.LENGTH_LONG).show());
                return false;
            }
        }
    }

    private void openFile(Uri uri) {
        Intent view = new Intent(Intent.ACTION_VIEW);
        view.setDataAndType(uri, DOCX_MIME);
        view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(view, "Открыть акт"));
        } catch (ActivityNotFoundException ignored) {
            // Нет Word/Docs — файл всё равно лежит в «Загрузках»
        }
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
