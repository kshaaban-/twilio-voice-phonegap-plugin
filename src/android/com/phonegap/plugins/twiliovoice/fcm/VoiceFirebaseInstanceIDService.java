package com.phonegap.plugins.twiliovoice.fcm;

import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.FirebaseInstanceIdService;
import com.twilio.voice.quickstart.VoiceActivity;

import com.phonegap.plugins.twiliovoice.TwilioVoicePlugin;


/*
 * Based on https://github.com/twilio/voice-quickstart-android/blob/master/app/src/main/java/com/twilio/voice/quickstart/gcm/VoiceInstanceIDListenerService.java
 * From Twilio
 */

public class VoiceFirebaseInstanceIDService extends FirebaseInstanceIdService {

    private static final String TAG = TwilioVoicePlugin.TAG;

    @Override
    public void onTokenRefresh() {

        String refreshedToken = FirebaseInstanceId.getInstance().getToken();

        Log.d(TAG, "onTokenRefresh");

        Intent intent = new Intent(VoiceActivity.ACTION_FCM_TOKEN);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
}