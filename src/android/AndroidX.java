package com.ex.androidx;

import android.app.Activity;
import android.util.Base64;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.safetynet.SafetyNet;
import com.google.android.gms.safetynet.SafetyNetApi;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;

public class AndroidX extends CordovaPlugin {
    public static final String STATUS_FIELD = "status";
    public static final String STATUS_SUCCESS = "success";
    public static final String STATUS_FAILURE = "failure";
    public static final String STATUS_ERROR = "error";
    public static final String RESPONSE_FIELD = "data";

    private final String ATTEST_ACTION = "start";
    private final Random mRandom = new SecureRandom();

    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
    }

    public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) throws JSONException {
        if (checkGooglePlayServicesAvailability().equals(STATUS_SUCCESS) && action.equals(ATTEST_ACTION)) {
            String nonceData = "androidx support : " + System.currentTimeMillis();
            byte[] nonce = getRequestNonce(nonceData);
            final String API_KEY = args.getString(0);

            this.handleAttestRequest(nonce, API_KEY, AndroidX.this.cordova.getActivity(), callbackContext);
        } else {
            callbackContext.error("Play Services not supported");
        }
        return true;
    }

    private byte[] getRequestNonce(String data) {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        byte[] bytes = new byte[24];
        mRandom.nextBytes(bytes);
        try {
            byteStream.write(bytes);
            byteStream.write(data.getBytes());
        } catch (IOException e) {
            Log.d("Error", e.getMessage());
            return new byte[0];
        }
        return byteStream.toByteArray();
    }

    private void handleAttestRequest(byte[] nonce, String key, Activity activity, CallbackContext callbackContext) {
        try {
            SafetyNet.getClient(activity).attest(nonce, key)
                    .addOnSuccessListener(activity,
                            new OnSuccessListener<SafetyNetApi.AttestationResponse>() {
                                @Override
                                public void onSuccess(SafetyNetApi.AttestationResponse response) {
                                    callbackContext.success(createJsonResponse(STATUS_SUCCESS, response.getJwsResult()));
                                }
                            })
                    .addOnFailureListener(activity,
                            new OnFailureListener() {
                                @Override
                                public void onFailure(Exception e) {
                                    callbackContext.success(createJsonResponse(STATUS_FAILURE, e.getLocalizedMessage()));
                                }
                            });
        } catch (Exception e) {
            callbackContext.error(createJsonResponse(STATUS_FAILURE, e.getLocalizedMessage()));
        }
    }

    private byte[] responseDataExtraction(final String jwsResult) {
        try {
            if (jwsResult != null) {
                final String[] jwsResultParts = jwsResult.split("[.]");
                final byte[] data = Base64.decode(jwsResultParts[1], Base64.NO_WRAP);
                return data;
            } else {
                return new byte[0];
            }
        } catch (Exception e) {
            Log.d("Error:", e.getMessage());
            return new byte[0];
        }
    }

    private JSONObject createJsonResponse(String status, String response) {

        byte[] responseData = responseDataExtraction(response);
        String decodedResponse = new String(responseData);

        Map<String, String> data = new HashMap<>();
        data.put(STATUS_FIELD, status);
        data.put(RESPONSE_FIELD, decodedResponse);
        return new JSONObject(data);
    }

    private String checkGooglePlayServicesAvailability() {
        switch (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this.cordova.getActivity())) {
            case ConnectionResult.SUCCESS:
                return STATUS_SUCCESS;
            case ConnectionResult.SERVICE_MISSING:
                return "service_missing";
            case ConnectionResult.SERVICE_UPDATING:
                return "service_updating";
            case ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED:
                return "service_version_update_required";
            case ConnectionResult.SERVICE_DISABLED:
                return "service_disabled";
            case ConnectionResult.SERVICE_INVALID:
                return "service_invalid";
            default:
                return STATUS_ERROR;
        }
    }
}