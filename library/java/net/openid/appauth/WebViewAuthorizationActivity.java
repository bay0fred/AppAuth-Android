package net.openid.appauth;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LifecycleOwnerKt;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONException;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import kotlinx.coroutines.CoroutineScope;

public class WebViewAuthorizationActivity extends AppCompatActivity {

    private static final String EXTRA_AUTH_URL = "authUrl";
    private static final String EXTRA_REDIRECT_URI = "redirectUri";
    private static final String EXTRA_AUTH_REQUEST = "authRequest";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_webview_authorization);

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

        // Initialize WebView
        WebView webView = findViewById(R.id.webView);

        // Configure WebView settings
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);

        boolean listenerSupported = WebViewFeature.isFeatureSupported(
            WebViewFeature.WEB_MESSAGE_LISTENER
        );
        if (listenerSupported) {

            // Inject local JavaScript that calls Credential Manager.
            CoroutineScope coroutineScope = LifecycleOwnerKt.getLifecycleScope(this);
            CredentialManagerHandler credentialManagerHandler = new CredentialManagerHandler(this);
            PasskeyWebListener passkeyWebListener = new PasskeyWebListener(
                this,
                coroutineScope,
                credentialManagerHandler
            );

            webView.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    Uri uri = null;
                    uri = request.getUrl();

                    // Check if the URL matches the redirect URI
                    if (uri.toString().startsWith(finalAuthRequest.redirectUri.toString())) {

                        // Create response from redirect URI
                        AuthorizationResponse response = new AuthorizationResponse.Builder(finalAuthRequest)
                            .fromUri(uri)
                            .build();

                        setResult(RESULT_OK, response.toIntent());
                        finish();
                        return true;
                    }
                    return super.shouldOverrideUrlLoading(view, request);
                }

                @Override
                public void onPageStarted(WebView view, String url, Bitmap favicon) {
                    super.onPageStarted(view, url, favicon);
                    passkeyWebListener.onPageStarted();
                    webView.evaluateJavascript(PasskeyWebListener.INJECTED_VAL, null);
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                }
            });

            Set<String> rules = new HashSet<>(Arrays.asList("*"));
            if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                WebViewCompat.addWebMessageListener(
                    webView, PasskeyWebListener.INTERFACE_NAME, rules, passkeyWebListener
                );
            }

        } else {

            // Setup web view without WebAuthn hook
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    Uri uri = null;
                    uri = request.getUrl();

                    // Check if the URL matches the redirect URI
                    if (uri.toString().startsWith(finalAuthRequest.redirectUri.toString())) {

                        // Create response from redirect URI
                        AuthorizationResponse response = new AuthorizationResponse.Builder(finalAuthRequest)
                            .fromUri(uri)
                            .build();

                        setResult(RESULT_OK, response.toIntent());
                        finish();
                        return true;
                    }
                    return super.shouldOverrideUrlLoading(view, request);
                }
            });
        }

        // Load authorization URL
        webView.loadUrl(authRequest.toUri().toString());
    }

    public static Intent createStartIntent(Activity activity, AuthorizationRequest request) {
        Intent intent = new Intent(activity, WebViewAuthorizationActivity.class);
        intent.putExtra(EXTRA_AUTH_REQUEST, request.jsonSerializeString());
        return intent;
    }
}
