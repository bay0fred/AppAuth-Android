package net.openid.appauth;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.credentials.CredentialManager;
import androidx.credentials.CreatePublicKeyCredentialRequest;
import androidx.credentials.CreatePublicKeyCredentialResponse;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.GetPublicKeyCredentialOption;
import androidx.credentials.exceptions.CreateCredentialException;
import androidx.credentials.exceptions.GetCredentialException;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

/**
 * A class that encapsulates the credential manager object and provides simplified APIs for
 * creating and retrieving public key credentials. For other types of credentials follow the
 * documentation https://developer.android.com/training/sign-in/passkeys
 */
public class CredentialManagerHandler {
    private final Activity activity;
    private final CredentialManager mCredMan;
    private static final String TAG = "CredentialManagerHandler";

    public CredentialManagerHandler(Activity activity) {
        this.activity = activity;
        this.mCredMan = CredentialManager.create(activity.getApplicationContext());
    }

    /**
     * Encapsulates the create passkey API for credential manager in a less error-prone manner.
     *
     * @param request a create public key credential request JSON required by {@link CreatePublicKeyCredentialRequest}.
     * @return a {@link CompletableFuture} that resolves to {@link CreatePublicKeyCredentialResponse}.
     */
    @SuppressLint({"LongLogTag", "RestrictedApi"})
    @RequiresApi(api = Build.VERSION_CODES.N)
    public CompletableFuture<CreatePublicKeyCredentialResponse> createPasskey(String request) {
        CreatePublicKeyCredentialRequest createRequest = new CreatePublicKeyCredentialRequest(request);
        return CompletableFuture.supplyAsync(() -> {
            return (CreatePublicKeyCredentialResponse) mCredMan.createCredential(activity, createRequest, null);
        });
    }

    /**
     * Encapsulates the get passkey API for credential manager in a less error-prone manner.
     *
     * @param request a get public key credential request JSON required by {@link GetCredentialRequest}.
     * @return a {@link CompletableFuture} that resolves to {@link GetCredentialResponse}.
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    @SuppressLint("LongLogTag")
    public CompletableFuture<GetCredentialResponse> getPasskey(String request) {
        GetCredentialRequest getRequest = new GetCredentialRequest(
            Collections.singletonList(new GetPublicKeyCredentialOption(request, null))
        );
        return CompletableFuture.supplyAsync(() -> {
            return (GetCredentialResponse) mCredMan.getCredential(activity, getRequest, null);
        });
    }
}
