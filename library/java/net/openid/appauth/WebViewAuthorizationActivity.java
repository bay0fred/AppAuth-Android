package net.openid.appauth;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONException;
import org.json.JSONObject;

public class WebViewAuthorizationActivity extends AppCompatActivity {

    private static final String EXTRA_AUTH_URL = "authUrl";
    private static final String EXTRA_REDIRECT_URI = "redirectUri";
    private static final String EXTRA_AUTH_REQUEST = "authRequest";
    public static final String ACTION_AUTHORIZATION = "net.openid.appauth.HANDLE_AUTHORIZATION_RESPONSE";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WebView webView = new WebView(this);
        setContentView(webView);

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        String json = getIntent().getStringExtra(EXTRA_AUTH_REQUEST);
        AuthorizationRequest authRequest = null;
        try {
            authRequest = AuthorizationRequest.jsonDeserialize(json);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        if (authRequest == null) {
            finish();
            return;
        }

        final AuthorizationRequest finalAuthRequest = authRequest;

        // Configure WebView settings
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);

        // Set up WebViewClient to intercept redirect URL
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    uri = request.getUrl();
                }

                // Check if the URL matches the redirect URI
                if (uri.toString().startsWith(finalAuthRequest.redirectUri.toString())) {
                    // Extract response parameters
                    Intent responseIntent = new Intent();
                    responseIntent.setData(uri);

                    // Create response from redirect URI
                    AuthorizationResponse response = new AuthorizationResponse.Builder(finalAuthRequest)
                        .fromUri(uri)
                        .build();
                    //AuthorizationResponse response = AuthorizationResponse.fromUri(uri);
                    AuthorizationException ex = AuthorizationException.fromOAuthRedirect(uri);

                    if (response != null) {
                        responseIntent.putExtra(AuthorizationResponse.EXTRA_RESPONSE, response.toIntent());
                    }

                    if (ex != null) {
                        responseIntent.putExtra(AuthorizationException.EXTRA_EXCEPTION, ex);
                    }

                    setResult(RESULT_OK, responseIntent);
                    finish();
                    return true;
                }
                return super.shouldOverrideUrlLoading(view, request);
            }
        });

        // Load authorization URL
        webView.loadUrl(authRequest.toUri().toString());
    }

    public static Intent createStartIntent(Activity activity, AuthorizationRequest request) {
        Intent intent = new Intent(activity, WebViewAuthorizationActivity.class);
        intent.putExtra(EXTRA_AUTH_REQUEST, request.jsonSerializeString());
        return intent;
    }
}
