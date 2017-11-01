package com.phonegap.plugins.twiliovoice.gcm;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.net.Uri;
import android.service.notification.StatusBarNotification;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import com.twilio.voice.CallInvite;
import com.twilio.voice.MessageException;
import com.twilio.voice.MessageListener;
import com.twilio.voice.Voice;
import com.phonegap.plugins.twiliovoice.SoundPoolManager;


import com.google.android.gms.gcm.GcmListenerService;

import com.phonegap.plugins.twiliovoice.TwilioVoicePlugin;

import java.util.List;

import static android.R.attr.data;

/*
 * Based on https://github.com/twilio/voice-quickstart-android/blob/master/app/src/main/java/com/twilio/voice/quickstart/gcm/VoiceGCMListenerService.java
 * From Twilio
 */

public class VoiceGCMListenerService extends GcmListenerService {

    private static final String TAG = TwilioVoicePlugin.TAG;

    /*
     * Notification related keys
     */
    private static final String NOTIFICATION_ID_KEY = "NOTIFICATION_ID";
    private static final String CALL_SID_KEY = "CALL_SID";

    private NotificationManager notificationManager;

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    }

    @Override
    public void onMessageReceived(String from, Bundle bundle) {
        Log.d(TAG, "onMessageReceived " + from);

        Log.d(TAG, "Received onMessageReceived()");
        Log.d(TAG, "From: " + from);
        Log.d(TAG, "Bundle data: " + bundle.toString());

        final int notificationId = (int) System.currentTimeMillis();
        Voice.handleMessage(this, bundle, new MessageListener() {
        
            @Override
            public void onCallInvite(CallInvite callInvite) {
                sendCallInviteToPlugin(callInvite, notificationId);
                showNotification(callInvite, notificationId);
            }

            @Override
            public void onError(MessageException messageException) {
                Log.e(TAG, messageException.getLocalizedMessage());
            }
        });
    }

    /*
     * Show the notification in the Android notification drawer
     */
    // @TargetApi(Build.VERSION_CODES.KITKAT_WATCH)
    private void showNotification(CallInvite callInvite, final int notificationId) {
        String callSid = callInvite.getCallSid();
        Notification notification = null;

        Log.d(TAG, "showNotification()");
        if (callInvite.getState() == CallInvite.State.PENDING) {
            /*
             * Create a PendingIntent to specify the action when the notification is
             * selected in the notification drawer
             */

            //start up the launch activity for the app (Cordova)
            Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
            intent.setAction(TwilioVoicePlugin.ACTION_INCOMING_CALL);
            intent.putExtra(TwilioVoicePlugin.INCOMING_CALL_NOTIFICATION_ID, notificationId);
            intent.putExtra(TwilioVoicePlugin.INCOMING_CALL_INVITE, callInvite);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, notificationId, intent, PendingIntent.FLAG_ONE_SHOT);

            Log.d(TAG, "showNotification(): Created pending intent");


            /*
             * Pass the notification id and call sid to use as an identifier to cancel the
             * notification later
             */
            Bundle extras = new Bundle();
            extras.putInt(NOTIFICATION_ID_KEY, notificationId);
            extras.putString(CALL_SID_KEY, callSid);

            /*
             * Create the notification shown in the notification drawer
             */
            int iconIdentifier = getResources().getIdentifier("icon", "mipmap", getPackageName());
            int ringingResourceId =  getResources().getIdentifier("ringing", "raw", getPackageName());
            int incomingCallAppNameId = getResources().getIdentifier("incoming_call_app_name", "string", getPackageName());
            Log.d(TAG, "Incoming Call App Name Id: " + incomingCallAppNameId);
            String contentTitle = getString(incomingCallAppNameId);
            Log.d(TAG, "Content Title: " + contentTitle);
            if (contentTitle == null) {
                contentTitle = "Incoming Call";
            }
            final String from = callInvite.getFrom() + " ";

            Log.d(TAG, "Call Invite from: " + from);

            NotificationCompat.Builder notificationBuilder =
                    new NotificationCompat.Builder(this)
                            .setSmallIcon(iconIdentifier)
                            .setContentTitle(contentTitle)
                            .setContentText(from)
                            .setAutoCancel(true)
                            .setSound(Uri.parse("android.resource://"
                                + getPackageName() + "/" + ringingResourceId))
                            .setExtras(extras)
                            .setContentIntent(pendingIntent)
                            .setGroup("voice_app_notification")
                            .setColor(Color.rgb(225, 225, 225));

            Log.d(TAG, "showNotification(): building notification");

            notificationManager.notify(notificationId, notificationBuilder.build());

            /**
            * http://stackoverflow.com/questions/39385616/how-to-set-that-screen-on-device-is-on-and-vibrate-when-notification-comes
            * Turn on the screen when a notification arrives
            */
            int seconds = 60;
            PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
            boolean isScreenOn = pm.isScreenOn();
            if( !isScreenOn )
            {
                PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK |PowerManager.ACQUIRE_CAUSES_WAKEUP |PowerManager.ON_AFTER_RELEASE,"VoicePluginLock");
                wl.acquire(seconds*1000);
                PowerManager.WakeLock wl_cpu = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"VoicePluginCpuLock");
                wl_cpu.acquire(seconds*1000);
            }

            Log.d(TAG, "showNotification(): show notification");
        } else {
            SoundPoolManager.getInstance(this).stopRinging();
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                /*
                 * If the incoming call was cancelled then remove the notification by matching
                 * it with the call sid from the list of notifications in the notification drawer.
                 */
                StatusBarNotification[] activeNotifications = notificationManager.getActiveNotifications();
                for (StatusBarNotification statusBarNotification : activeNotifications) {
                    notification = statusBarNotification.getNotification();
                    Bundle extras = notification.extras;
                    String notificationCallSid = extras.getString(CALL_SID_KEY);
                    if (callSid.equals(notificationCallSid)) {
                        notificationManager.cancel(extras.getInt(NOTIFICATION_ID_KEY));
                    }
                }
            } else {
                /*
                 * Prior to Android M the notification manager did not provide a list of
                 * active notifications so we lazily clear all the notifications when
                 * receiving a cancelled call.
                 *
                 * In order to properly cancel a notification using
                 * NotificationManager.cancel(notificationId) we should store the call sid &
                 * notification id of any incoming calls using shared preferences or some other form
                 * of persistent storage.
                 */
                notificationManager.cancelAll();
            }
        }
    }

    /*
     * Send the IncomingCallMessage to the Plugin
     */
    private void sendCallInviteToPlugin(CallInvite incomingCallMessage, int notificationId) {

        Intent intent = new Intent(TwilioVoicePlugin.ACTION_INCOMING_CALL);
        intent.putExtra(TwilioVoicePlugin.INCOMING_CALL_INVITE, incomingCallMessage);
        intent.putExtra(TwilioVoicePlugin.INCOMING_CALL_NOTIFICATION_ID, notificationId);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

}
