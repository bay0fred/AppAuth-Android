package net.openid.appauth;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.credentials.CreatePublicKeyCredentialResponse;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.PublicKeyCredential;
import androidx.credentials.exceptions.CreateCredentialException;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.webkit.JavaScriptReplyProxy;
import androidx.webkit.WebMessageCompat;
import androidx.webkit.WebViewCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class PasskeyWebListener implements  WebViewCompat.WebMessageListener {

    // Implementation details
    private Activity activity;

    // Handles get/create methods meant for Java:
    private CredentialManagerHandler credentialManagerHandler;

    private boolean havePendingRequest = false;
    private boolean pendingRequestIsDoomed = false;
    private ReplyChannel replyChannel;

    public PasskeyWebListener(
        Activity activity,
        CredentialManagerHandler credentialManagerHandler
    ) {
        this.activity = activity;
        this.credentialManagerHandler = credentialManagerHandler;
    }

    /**
     * Called by the page during a WebAuthn request.
     *
     * @param view Creates the WebView.
     * @param message The message sent from the client using injected JavaScript.
     * @param sourceOrigin The origin of the HTTPS request. Should not be null.
     * @param isMainFrame Should be set to true. Embedded frames are not
    supported.
     * @param replyProxy Passed in by JavaScript. Allows replying when wrapped in
    the Channel.
     * @return The message response.
     */
    @UiThread
    public void onPostMessage(
        @org.jspecify.annotations.NonNull WebView view,
        @org.jspecify.annotations.NonNull WebMessageCompat message,
        @org.jspecify.annotations.NonNull Uri sourceOrigin,
        boolean isMainFrame,
        @org.jspecify.annotations.NonNull JavaScriptReplyProxy replyProxy) {
        String messageData = message.getData();
        if (messageData == null) {
            return;
        }
        onRequest(
            messageData,
            sourceOrigin,
            isMainFrame,
            new JavaScriptReplyChannel(replyProxy)
        );
    }

    private void onRequest(
        String msg,
        Uri sourceOrigin,
        boolean isMainFrame,
        ReplyChannel reply
    ) {
        if (msg != null) {
            try {
                JSONObject jsonObj = new JSONObject(msg);
                String type = jsonObj.getString(TYPE_KEY);
                String message = jsonObj.getString(REQUEST_KEY);

                boolean isCreate = type.equals(CREATE_UNIQUE_KEY);
                boolean isGet = type.equals(GET_UNIQUE_KEY);

                if (havePendingRequest) {
                    postErrorMessage(reply, "The request already in progress", type);
                    return;
                }
                replyChannel = reply;
                if (!isMainFrame) {
                    reportFailure("Requests from subframes are not supported", type);
                    return;
                }
                String originScheme = sourceOrigin.getScheme();
                if (originScheme == null || !originScheme.toLowerCase().equals("https")) {
                    reportFailure("WebAuthn not permitted for current URL", type);
                    return;
                }

                // Verify that origin belongs to your website,
                // Requests of unknown origin may gain access to credential info.
//                if (isUnknownOrigin(originScheme)) {
//                    return;
//                }

                havePendingRequest = true;
                pendingRequestIsDoomed = false;

                // Use a temporary "replyCurrent" variable to send the data back,
                // while resetting the main "replyChannel" variable to null so it’s
                // ready for the next request.

                ReplyChannel replyCurrent = replyChannel;
                if (replyCurrent == null) {
                    Log.i(TAG, "The reply channel was null, cannot continue");
                    return;
                }

                if (isCreate) {
                    handleCreateFlow(credentialManagerHandler, message, replyCurrent);
                } else if (isGet) {
                    handleGetFlow(credentialManagerHandler, message, replyCurrent);
                } else {
                    Log.i(TAG, "Incorrect request json");
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    @SuppressLint("RestrictedApi")
    private void handleGetFlow(CredentialManagerHandler credentialManagerHandler, String message, ReplyChannel reply) {

        havePendingRequest = false;
        pendingRequestIsDoomed = false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            credentialManagerHandler.getPasskey(message).thenAccept(getCredentialResponse -> {
                try {
                        PublicKeyCredential credential = (PublicKeyCredential) getCredentialResponse.getCredential();
                        List<Object> successArray = new ArrayList<>();
                        successArray.add("success");
                        successArray.add(new JSONObject(credential.getAuthenticationResponseJson()));
                        successArray.add(GET_UNIQUE_KEY);
                        reply.send(new JSONArray(successArray).toString());
                        replyChannel = null;
                } catch (Throwable t) {
                        reportFailure("Error: " + t.getMessage(), GET_UNIQUE_KEY);
                }
            })
            .exceptionally(ex -> {
                reportFailure("Error: " + ex.getMessage(), GET_UNIQUE_KEY);
                return null;
            });
        }
    }

    @SuppressLint("RestrictedApi")
    private void handleCreateFlow(CredentialManagerHandler credentialManagerHandler, String message, ReplyChannel reply) {

        havePendingRequest = false;
        pendingRequestIsDoomed = false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            credentialManagerHandler.createPasskey(message).thenAccept(createPublicKeyCredentialResponse -> {
                try {
                    String response = createPublicKeyCredentialResponse.getRegistrationResponseJson();
                    List<Object> successArray = new ArrayList<>();
                    successArray.add("success");
                    successArray.add(new JSONObject(response));
                    successArray.add(CREATE_UNIQUE_KEY);
                    reply.send(new JSONArray(successArray).toString());
                    replyChannel = null;
                } catch (Throwable t) {
                    reportFailure("Error: " + t.getMessage(), GET_UNIQUE_KEY);
                }
            }).exceptionally(ex -> {
                reportFailure("Error: " + ex.getMessage(), CREATE_UNIQUE_KEY);
                return null;
            });
        }
    }

    public void onPageStarted() {
        if (havePendingRequest) {
            pendingRequestIsDoomed = true;
        }
    }

    private void reportFailure(String message, String type) {
        havePendingRequest = false;
        pendingRequestIsDoomed = false;
        ReplyChannel reply = replyChannel;
        replyChannel = null;
        postErrorMessage(reply, message, type);
    }

    private void postErrorMessage(ReplyChannel reply, String errorMessage, String type) {
        Log.i(TAG, "Sending error message back to the page via replyChannel " + errorMessage);
        List<Object> array = new ArrayList<>();
        array.add("error");
        array.add(errorMessage);
        array.add(type);
        reply.send(new JSONArray(array).toString());
        Toast.makeText(activity.getApplicationContext(), errorMessage, Toast.LENGTH_SHORT).show();
    }

    // The setup for the reply channel allows communication with JavaScript.
    private static class JavaScriptReplyChannel implements ReplyChannel {
        private final JavaScriptReplyProxy reply;

        JavaScriptReplyChannel(JavaScriptReplyProxy reply) {
            this.reply = reply;
        }

        @SuppressLint("RequiresFeature")
        @Override
        public void send(String message) {
            reply.postMessage(message);
        }
    }

    // ReplyChannel is the interface where replies to the embedded site are
    // sent. This allows for testing since AndroidX bans mocking its objects.
    interface ReplyChannel {
        void send(String message);
    }

    private static final String TAG = "PasskeyWebListener";
    private static final String CREATE_UNIQUE_KEY = "create";
    private static final String GET_UNIQUE_KEY = "get";
    private static final String TYPE_KEY = "type";
    private static final String REQUEST_KEY = "request";

    /** INTERFACE_NAME is the name of the MessagePort that must be injected into pages. */
    public static final String INTERFACE_NAME = "__webauthn_interface__";

    /** INJECTED_VAL is the minified version of the JavaScript code described at this class
     * heading. The non minified form is found at credmanweb/javascript/encode.js.*/
    public static final String INJECTED_VAL =
        "var __webauthn_interface__,__webauthn_hooks__;" +
            "!function(e){" +
            "console.log(\"In the hook.\")," +
            "__webauthn_interface__.addEventListener(\"message\",function e(n){" +
            "var r=JSON.parse(n.data),t=r[2];" +
            "\"get\"===t?o(r):\"create\"===t?u(r):console.log(\"Incorrect response format for reply\")});" +
            "var n=null,r=null,t=null,a=null;" +
            "function o(e){" +
            "if(null!==n&&null!==t){" +
            "if(\"success\"!=e[0]){" +
            "var r=t;n=null,t=null,r(new DOMException(e[1],\"NotAllowedError\"));return}" +
            "var a=i(e[1]),o=n;n=null,t=null,o(a)}}" +
            "function l(e){" +
            "var n=e.length%4;" +
            "return Uint8Array.from(atob(e.replace(/-/g,\"+\").replace(/_/g,\"/\").padEnd(e.length+(0===n?0:4-n),\"=\")),function(e){return e.charCodeAt(0)}).buffer}" +
            "function s(e){return btoa(Array.from(new Uint8Array(e),function(e){return String.fromCharCode(e)}).join(\"\"))" +
            ".replace(/\\+/g,\"-\").replace(/\\//g,\"_\").replace(/=+$/,\"\")}" +
            "function u(e){" +
            "if(null===r||null===a){console.log(\"Here: \"+r+\" and reject: \"+a);return}" +
            "if(console.log(\"Output back: \"+e),\"success\"!=e[0]){" +
            "var n=a;r=null,a=null,n(new DOMException(e[1],\"NotAllowedError\"));return}" +
            "var t=i(e[1]),o=r;r=null,a=null,o(t)}" +
            "function i(e){" +
            "console.log(\"Here is the response from credential manager: \"+e)," +
            "e.rawId=l(e.rawId),e.response.clientDataJSON=l(e.response.clientDataJSON)," +
            "e.response.hasOwnProperty(\"attestationObject\")&&(e.response.attestationObject=l(e.response.attestationObject))," +
            "e.response.hasOwnProperty(\"authenticatorData\")&&(e.response.authenticatorData=l(e.response.authenticatorData))," +
            "e.response.hasOwnProperty(\"signature\")&&(e.response.signature=l(e.response.signature))," +
            "e.response.hasOwnProperty(\"userHandle\")&&(e.response.userHandle=l(e.response.userHandle))," +
            "e.getClientExtensionResults=function e(){return{}}," +
            "e}" +
            "e.create=function n(t){" +
            "if(!(\"publicKey\"in t))return e.originalCreateFunction(t);" +
            "var o=new Promise(function(e,n){r=e,a=n}),l=t.publicKey;" +
            "if(l.hasOwnProperty(\"challenge\")){" +
            "var u=s(l.challenge);l.challenge=u}" +
            "if(l.hasOwnProperty(\"user\")&&l.user.hasOwnProperty(\"id\")){" +
            "var i=s(l.user.id);l.user.id=i}" +
            "var c=JSON.stringify({type:\"create\",request:l});" +
            "__webauthn_interface__.postMessage(c),o}," +
            "e.get=function r(a){" +
            "if(!(\"publicKey\"in a))return e.originalGetFunction(a);" +
            "var o=new Promise(function(e,r){n=e,t=r}),l=a.publicKey;" +
            "if(l.hasOwnProperty(\"challenge\")){" +
            "var u=s(l.challenge);l.challenge=u}" +
            "var i=JSON.stringify({type:\"get\",request:l});" +
            "__webauthn_interface__.postMessage(i),o}," +
            "e.onReplyGet=o,e.CM_base64url_decode=l,e.CM_base64url_encode=s,e.onReplyCreate=u}" +
            "(__webauthn_hooks__||(__webauthn_hooks__={})),__webauthn_hooks__.originalGetFunction=navigator.credentials.get," +
            "__webauthn_hooks__.originalCreateFunction=navigator.credentials.create," +
            "navigator.credentials.get=__webauthn_hooks__.get," +
            "navigator.credentials.create=__webauthn_hooks__.create," +
            "window.PublicKeyCredential=function(){}," +
            "window.PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable=function(){return Promise.resolve(!1)};";
}
