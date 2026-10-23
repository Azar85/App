package ir.cluphamed.club;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final String BASE = "https://cluphamed.ir/";
    private static final String HOST = "cluphamed.ir";
    private static final int GOLD = 0xFFD8B767;
    private static final int DARK = 0xFF070D1B;
    private static final int FILE_CODE = 9201;

    private WebView browser;
    private TextView offline;
    private ProgressBar loading;
    private SharedPreferences prefs;
    private android.webkit.ValueCallback<Uri[]> fileChoice;

    private int dp(int p) { return (int)(getResources().getDisplayMetrics().density * p + 0.5f); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("hamed_app", MODE_PRIVATE);
        setContentView(buildView());
        setupBrowser();
        if (state == null) browser.loadUrl(BASE);
        else if (browser.restoreState(state) == null) browser.loadUrl(BASE);
    }

    private View buildView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(DARK);
        root.setOnApplyWindowInsetsListener((v, windowInsets) -> {
            int left, top, right, bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = windowInsets.getInsets(android.view.WindowInsets.Type.systemBars());
                left = bars.left; top = bars.top; right = bars.right; bottom = bars.bottom;
            } else {
                left = windowInsets.getSystemWindowInsetLeft();
                top = windowInsets.getSystemWindowInsetTop();
                right = windowInsets.getSystemWindowInsetRight();
                bottom = windowInsets.getSystemWindowInsetBottom();
            }
            v.setPadding(left, top, right, bottom);
            return windowInsets;
        });
        TextView header = new TextView(this);
        header.setText("✦  کلوپ حامد");
        header.setTextColor(GOLD);
        header.setTypeface(null, 1);
        header.setTextSize(18);
        header.setGravity(Gravity.CENTER);
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(54)));

        loading = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        loading.setMax(100);
        loading.setProgressTintList(ColorStateList.valueOf(GOLD));
        root.addView(loading, new LinearLayout.LayoutParams(-1, dp(3)));

        FrameLayout page = new FrameLayout(this);
        root.addView(page, new LinearLayout.LayoutParams(-1, 0, 1));
        browser = new WebView(this);
        page.addView(browser, new FrameLayout.LayoutParams(-1, -1));

        offline = new TextView(this);
        offline.setText("ارتباط با سایت برقرار نشد.\nاینترنت را بررسی کن و برای تلاش دوباره اینجا بزن.");
        offline.setTextSize(17);
        offline.setTextColor(Color.WHITE);
        offline.setGravity(Gravity.CENTER);
        offline.setPadding(dp(28), 0, dp(28), 0);
        offline.setBackgroundColor(DARK);
        offline.setVisibility(View.GONE);
        offline.setOnClickListener(v -> { offline.setVisibility(View.GONE); browser.reload(); });
        page.addView(offline, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout bar = new LinearLayout(this);
        bar.setBackgroundColor(0xFF132138);
        root.addView(bar, new LinearLayout.LayoutParams(-1, dp(62)));
        button(bar, "خانه", () -> browser.loadUrl(BASE));
        button(bar, "نشانک", this::toggleFavorite);
        button(bar, "ذخیره‌ها", this::showFavorites);
        button(bar, "اشتراک", this::share);
        button(bar, "تازه‌سازی", () -> browser.reload());
        return root;
    }

    private void button(LinearLayout bar, String text, Runnable action) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(GOLD);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setPadding(0, 0, 0, 0);
        button.setBackgroundColor(Color.TRANSPARENT);
        bar.addView(button, new LinearLayout.LayoutParams(0, -1, 1));
        button.setOnClickListener(v -> action.run());
    }

    private void setupBrowser() {
        WebSettings settings = browser.getSettings();
        settings.setJavaScriptEnabled(true); // required for site browsing and cart
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        if (Build.VERSION.SDK_INT >= 26) settings.setSafeBrowsingEnabled(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(browser, false);

        browser.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleLink(request.getUrl());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleLink(Uri.parse(url));
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                offline.setVisibility(View.GONE);
            }
            @Override public void onPageFinished(WebView view, String url) {
                CookieManager.getInstance().flush();
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, android.webkit.WebResourceError error) {
                if (request.isForMainFrame()) offline.setVisibility(View.VISIBLE);
            }
        });
        browser.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int progress) {
                loading.setProgress(progress);
                loading.setVisibility(progress >= 100 ? View.INVISIBLE : View.VISIBLE);
            }
            @Override public boolean onShowFileChooser(WebView view,
                android.webkit.ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileChoice != null) fileChoice.onReceiveValue(null);
                fileChoice = callback;
                try {
                    startActivityForResult(params.createIntent(), FILE_CODE);
                    return true;
                } catch (Exception error) {
                    fileChoice = null;
                    return false;
                }
            }
        });
        browser.setDownloadListener((url, userAgent, disposition, mime, length) -> {
            Uri uri = Uri.parse(url);
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                Toast.makeText(this, "لینک دانلود امن نیست", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                String name = android.webkit.URLUtil.guessFileName(url, disposition, mime);
                DownloadManager.Request req = new DownloadManager.Request(uri);
                req.setTitle(name);
                req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                req.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, name);
                req.addRequestHeader("User-Agent", userAgent);
                String cookie = CookieManager.getInstance().getCookie(url);
                if (cookie != null) req.addRequestHeader("Cookie", cookie);
                ((DownloadManager)getSystemService(DOWNLOAD_SERVICE)).enqueue(req);
                Toast.makeText(this, "دریافت فایل آغاز شد", Toast.LENGTH_SHORT).show();
            } catch (Exception error) {
                Toast.makeText(this, "دانلود انجام نشد", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private boolean isOurs(Uri url) {
        if (url == null || !"https".equalsIgnoreCase(url.getScheme())) return false;
        String host = url.getHost();
        return host != null && (host.equalsIgnoreCase(HOST) || host.toLowerCase(java.util.Locale.ROOT).endsWith("." + HOST));
    }

    private boolean handleLink(Uri uri) {
        if (isOurs(uri)) return false;
        if (uri == null) return true;
        String scheme = uri.getScheme();
        if (scheme == null) return true;
        if (scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http")
            || scheme.equalsIgnoreCase("tel") || scheme.equalsIgnoreCase("mailto")
            || scheme.equalsIgnoreCase("sms")) {
            try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
            catch (ActivityNotFoundException error) {
                Toast.makeText(this, "برنامه مناسب پیدا نشد", Toast.LENGTH_SHORT).show();
            }
        }
        return true;
    }

    private JSONArray favorites() {
        try { return new JSONArray(prefs.getString("favorites", "[]")); }
        catch (JSONException error) { return new JSONArray(); }
    }

    private void toggleFavorite() {
        Uri uri = Uri.parse(browser.getUrl() == null ? "" : browser.getUrl());
        if (!isOurs(uri)) {
            Toast.makeText(this, "ابتدا یکی از صفحات سایت را باز کن", Toast.LENGTH_SHORT).show();
            return;
        }
        String url = uri.toString();
        JSONArray old = favorites();
        JSONArray revised = new JSONArray();
        boolean wasSaved = false;
        for (int i=0; i<old.length(); i++) {
            JSONObject row = old.optJSONObject(i);
            if (row != null) {
                if (url.equals(row.optString("url"))) wasSaved = true;
                else revised.put(row);
            }
        }
        if (!wasSaved) {
            JSONObject row = new JSONObject();
            try {
                row.put("url", url);
                row.put("title", browser.getTitle() == null ? "کلوپ حامد" : browser.getTitle());
                revised.put(row);
            } catch (JSONException ignored) {}
        }
        prefs.edit().putString("favorites", revised.toString()).apply();
        Toast.makeText(this, wasSaved ? "از نشانک‌ها حذف شد" : "صفحه ذخیره شد", Toast.LENGTH_SHORT).show();
    }

    private void showFavorites() {
        JSONArray rows = favorites();
        if (rows.length() == 0) {
            new AlertDialog.Builder(this).setTitle("صفحه‌های ذخیره‌شده")
              .setMessage("هنوز صفحه‌ای ذخیره نکرده‌ای.")
              .setPositiveButton("باشه", null).show();
            return;
        }
        String[] titles = new String[rows.length()];
        for (int i=0; i<rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            titles[i] = row == null ? "صفحه" : row.optString("title", "صفحه");
        }
        new AlertDialog.Builder(this).setTitle("صفحه‌های ذخیره‌شده")
          .setItems(titles, (dialog, index) -> {
              JSONObject row = rows.optJSONObject(index);
              if (row != null) {
                  Uri url = Uri.parse(row.optString("url", ""));
                  if (isOurs(url)) browser.loadUrl(url.toString());
              }
          }).setNegativeButton("بستن", null).show();
    }

    private void share() {
        Uri url = Uri.parse(browser.getUrl() == null ? "" : browser.getUrl());
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, "کلوپ حامد\n" + (isOurs(url) ? url.toString() : BASE));
        startActivity(Intent.createChooser(intent, "اشتراک‌گذاری"));
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == FILE_CODE && fileChoice != null) {
            fileChoice.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result, data));
            fileChoice = null;
        }
    }
    @Override public void onBackPressed() {
        if (browser != null && browser.canGoBack()) browser.goBack();
        else new AlertDialog.Builder(this).setMessage("از برنامه خارج می‌شوی؟")
          .setPositiveButton("خروج", (d, w) -> finish())
          .setNegativeButton("ماندن", null).show();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        if (browser != null) browser.saveState(state);
        super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() {
        if (browser != null) {
            browser.destroy();
            browser = null;
        }
        super.onDestroy();
    }
}
