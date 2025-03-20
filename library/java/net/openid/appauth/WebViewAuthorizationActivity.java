package net.openid.appauth;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;

public class WebViewAuthorizationActivity extends AppCompatActivity {

    private WebView webView;
    public static final String AUTH_URL_KEY = "AUTH_URL";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        setContentView(webView);

        String authUrl = getIntent().getStringExtra(AUTH_URL_KEY);
        if (authUrl != null) {
            webView.getSettings().setJavaScriptEnabled(true);
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    String url = null;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        url = request.getUrl().toString();
                    }
                    if (url.startsWith("<YOUR_REDIRECT_URI>")) {
                        handleRedirect(url);
                        return true;
                    }
                    return false;
                }
            });
            webView.loadUrl(authUrl);
        } else {
            finish();
        }
    }

    private void handleRedirect(String url) {
        Intent resultIntent = new Intent();
        resultIntent.setData(Uri.parse(url));
        setResult(RESULT_OK, resultIntent);
        finish();
    }
}
