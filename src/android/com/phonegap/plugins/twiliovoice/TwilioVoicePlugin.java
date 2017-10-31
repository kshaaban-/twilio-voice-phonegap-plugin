package com.phonegap.plugins.twiliovoice;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.DialogInterface;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Build;
import android.os.SystemClock;
import android.support.annotation.NonNull;

import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Chronometer;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.phonegap.plugins.twiliovoice.gcm.GCMRegistrationService;

import com.twilio.voice.Call;
import com.twilio.voice.CallException;
import com.twilio.voice.CallInvite;
import com.twilio.voice.CallState;
import com.twilio.voice.RegistrationException;
import com.twilio.voice.RegistrationListener;
import com.twilio.voice.Voice;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.apache.cordova.PluginResult.Status;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import android.widget.Toast;

//import android.R;


public class TwilioVoicePlugin extends CordovaPlugin {

	public final static String TAG = "TwilioVoicePlugin";

	// Empty HashMap, never populated for the Quickstart
	HashMap<String, String> twiMLParams = new HashMap<String, String>() {{
        put("PhoneNumber", "+12158736513");
        put("To", "+12158736513");
        put("From", "12055122669");
        put("country_iso", "US");
        put("ToCountry", "US");
    }};

	private CallbackContext mInitCallbackContext;
	private int mCurrentNotificationId = 1;
	private String mCurrentNotificationText;

	// Twilio Voice Member Variables
	private Call mCall;
	private CallInvite mCallInvite;

	// Access Token
	private String mAccessToken;

	// GCM Token
    private String mGCMToken;

	// Has the plugin been initialized
	private boolean mInitialized = false;

	// An incoming call intent to process (can be null)
	private Intent mIncomingCallIntent;

	// Marshmallow Permissions
	public static final String RECORD_AUDIO = Manifest.permission.RECORD_AUDIO;
	public static final int RECORD_AUDIO_REQ_CODE = 0;
    
    // Google Play Services Request Magic Number
    private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;

	private AudioManager audioManager;
    private int savedAudioMode = AudioManager.MODE_INVALID;

    // Constants for Intents and Broadcast Receivers
    public static final String ACTION_SET_GCM_TOKEN = "SET_GCM_TOKEN";
    public static final String INCOMING_CALL_INVITE = "INCOMING_CALL_INVITE";
    public static final String INCOMING_CALL_NOTIFICATION_ID = "INCOMING_CALL_NOTIFICATION_ID";
    public static final String ACTION_INCOMING_CALL = "INCOMING_CALL";

    public static final String KEY_GCM_TOKEN = "GCM_TOKEN";


	private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(ACTION_SET_GCM_TOKEN)) {
                String gcmToken = intent.getStringExtra(KEY_GCM_TOKEN);
                Log.i(TAG, "GCM Token : " + gcmToken);
                mGCMToken = gcmToken;
                if(gcmToken == null) {
                    javascriptErrorback(0, "Did not receive GCM Token - unable to receive calls", mInitCallbackContext);
                }
                //callActionFab.show();
                if (mGCMToken != null) {
                    register();
                }
            } else if (action.equals(ACTION_INCOMING_CALL)) {
                /*
                 * Handle the incoming call invite
                 */
                // handleIncomingCallIntent(intent);
            }
		}
	};

	@Override
	public void initialize(CordovaInterface cordova, CordovaWebView webView) {
		super.initialize(cordova, webView);
		Log.d(TAG, "initialize()");

        // initialize sound SoundPoolManager
		SoundPoolManager.getInstance(cordova.getActivity());
		
		/*
         * Needed for setting/abandoning audio focus during a call
         */
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		
		/*
         * Enable changing the volume using the up/down keys during a conversation
        */
        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
		
		/*
         * Ensure the microphone permission is enabled
         */
        if (!checkPermissionForMicrophone()) {
			Log.d(TAG, "!checkPermissionForMicrophone()");
            requestPermissionForMicrophone();
        } 
	}

	@Override
	public void onRestoreStateForActivityResult(Bundle state, CallbackContext callbackContext) {
		super.onRestoreStateForActivityResult(state, callbackContext);
		Log.d(TAG, "onRestoreStateForActivityResult()");
		mInitCallbackContext = callbackContext;
	}
	
	/**
	 * Android Cordova Action Router
	 * 
	 * Executes the request.
	 * 
	 * This method is called from the WebView thread. To do a non-trivial amount
	 * of work, use: cordova.getThreadPool().execute(runnable);
	 * 
	 * To run on the UI thread, use:
	 * cordova.getActivity().runOnUiThread(runnable);
	 * 
	 * @param action
	 *            The action to execute.
	 * @param args
	 *            The exec() arguments in JSON form.
	 * @param callbackContext
	 *            The callback context used when calling back into JavaScript.
	 * @return Whether the action was valid.
	 */
	@Override
	public boolean execute(final String action, final JSONArray args,
			final CallbackContext callbackContext) throws JSONException {
		if ("initializeWithAccessToken".equals(action)) {
            Log.d(TAG, "Initializing with Access Token");

			mAccessToken = args.optString(0);

			mInitCallbackContext = callbackContext;

            IntentFilter intentFilter = new IntentFilter();
			intentFilter.addAction(ACTION_SET_GCM_TOKEN);
            intentFilter.addAction(ACTION_INCOMING_CALL);
            LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(cordova.getActivity());
            lbm.registerReceiver(mBroadcastReceiver, intentFilter);


			if(cordova.hasPermission(RECORD_AUDIO))
			{
				startGCMRegistration();
			}
			else
			{
				cordova.requestPermission(this, RECORD_AUDIO_REQ_CODE, RECORD_AUDIO);
			}

			if (mIncomingCallIntent != null) {
				Log.d(TAG, "initialize(): Handle an incoming call");
			 	handleIncomingCallIntent(mIncomingCallIntent);
				mIncomingCallIntent = null;
			}

			javascriptCallback("onclientinitialized",mInitCallbackContext);

			return true;

		} else if ("call".equals(action)) {
			call(args, callbackContext);
			return true;
		} else if ("acceptCallInvite".equals(action)) {
			acceptCallInvite(args, callbackContext);
			return true;
		} else if ("disconnect".equals(action)) {
			disconnect(args, callbackContext);
			return true;
		} else if ("setSpeaker".equals(action)) {
			setSpeaker(args,callbackContext);
			return true;
		}

		return false; 
	}

	private void call(final JSONArray arguments, final CallbackContext callbackContext) {
		cordova.getThreadPool().execute(new Runnable(){
			public void run() {
				String accessToken = arguments.optString(0,mAccessToken);
				JSONObject options = arguments.optJSONObject(1);
				if (mCall != null && mCall.getState().equals(CallState.CONNECTED)) {
					mCall.disconnect();
				}
				mCall = Voice.call(cordova.getActivity(),accessToken, twiMLParams, mCallListener);
				Log.d(TAG, "Placing call with params: " + twiMLParams.toString());
			}
		});	
	}

	private void acceptCallInvite(JSONArray arguments, final CallbackContext callbackContext) {
		if (mCallInvite == null) {
			callbackContext.sendPluginResult(new PluginResult(
					PluginResult.Status.ERROR));
			return;
		}
		cordova.getThreadPool().execute(new Runnable(){
			public void run() {
				mCallInvite.accept(cordova.getActivity(),mCallListener);
				callbackContext.success(); 
			}
		});
		
	}
	
	private void rejectCallInvite(JSONArray arguments, final CallbackContext callbackContext) {
		if (mCallInvite == null) {
			callbackContext.sendPluginResult(new PluginResult(
					PluginResult.Status.ERROR));
			return;
		}
		cordova.getThreadPool().execute(new Runnable(){
			public void run() {
				mCallInvite.reject(cordova.getActivity());
				callbackContext.success(); 
			}
		});
	}
	
	private void disconnect(JSONArray arguments, final CallbackContext callbackContext) {
		if (mCall == null) {
			callbackContext.sendPluginResult(new PluginResult(
					PluginResult.Status.ERROR));
			return;
		}
		cordova.getThreadPool().execute(new Runnable(){
			public void run() {
				mCall.disconnect();
				callbackContext.success(); 
			}
		});
	}
	
	/**
	 * 	Changes sound from earpiece to speaker and back
	 * 
	 * 	@param mode	Speaker Mode
	 * */
	public void setSpeaker(final JSONArray arguments, final CallbackContext callbackContext) {
		cordova.getThreadPool().execute(new Runnable(){
			public void run() {
				Context context = cordova.getActivity().getApplicationContext();
				AudioManager m_amAudioManager;
				m_amAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
				String mode = arguments.optString(0);
				if(mode.equals("on")) {
					Log.d(TAG, "SPEAKER");
					m_amAudioManager.setMode(AudioManager.MODE_NORMAL);
					m_amAudioManager.setSpeakerphoneOn(true);        	
				}
				else {
					Log.d(TAG, "EARPIECE");
					m_amAudioManager.setMode(AudioManager.MODE_IN_COMMUNICATION); 
					m_amAudioManager.setSpeakerphoneOn(false);
				}
			}
		});
	}

	// Plugin-to-Javascript communication methods
	private void javascriptCallback(String event, JSONObject arguments,
			CallbackContext callbackContext) {
		if (callbackContext == null) {
			return;
		}
		JSONObject options = new JSONObject();
		try {
			options.putOpt("callback", event);
			options.putOpt("arguments", arguments);
		} catch (JSONException e) {
			callbackContext.sendPluginResult(new PluginResult(
					PluginResult.Status.JSON_EXCEPTION));
			return;
		}
		PluginResult result = new PluginResult(Status.OK, options);
		result.setKeepCallback(true);
		callbackContext.sendPluginResult(result);

	}

	private void javascriptCallback(String event,
			CallbackContext callbackContext) {
		javascriptCallback(event, null, callbackContext);
	}

	
	private void javascriptErrorback(int errorCode, String errorMessage, CallbackContext callbackContext) {
		JSONObject object = new JSONObject();
		try {
			object.putOpt("message", errorMessage);
		} catch (JSONException e) {
			callbackContext.sendPluginResult(new PluginResult(
					PluginResult.Status.JSON_EXCEPTION));
			return;
		}
		PluginResult result = new PluginResult(Status.ERROR, object);
		result.setKeepCallback(true);
		callbackContext.sendPluginResult(result);
	}


	@Override
	public void onDestroy() {
		//lifecycle events
        SoundPoolManager.getInstance(cordova.getActivity()).release();
		LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(cordova
				.getActivity());
		lbm.unregisterReceiver(mBroadcastReceiver);
        super.onDestroy();
	}


	public void onRequestPermissionResult(int requestCode, String[] permissions,
										  int[] grantResults) throws JSONException
	{
		for(int r:grantResults)
		{
			if(r == PackageManager.PERMISSION_DENIED)
			{
				mInitCallbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, "Permission denied"));
				return;
			}
		}
		switch(requestCode)
		{
			case RECORD_AUDIO_REQ_CODE:
                startGCMRegistration();
				break;
		}
	}

    /*
     * Register your GCM token with Twilio to enable receiving incoming calls via GCM
     */
    private void register() {
		Voice.register(cordova.getActivity().getApplicationContext(), mAccessToken, mGCMToken, mRegistrationListener);
    }

	 // Process incoming call invites
    private void handleIncomingCallIntent(Intent intent) {
        Log.d(TAG, "handleIncomingCallIntent()");
        if (intent != null && intent.getAction() != null && intent.getAction().equals(ACTION_INCOMING_CALL)) {
            mCallInvite = intent.getParcelableExtra(INCOMING_CALL_INVITE);
            Log.d(TAG, "Call Invite: " + mCallInvite.toString());
            if (mCallInvite != null && (mCallInvite.getState() == CallInvite.State.PENDING)) {
                SoundPoolManager.getInstance(cordova.getActivity()).playRinging();
                NotificationManager mNotifyMgr = 
		        (NotificationManager) cordova.getActivity().getSystemService(Activity.NOTIFICATION_SERVICE);
                mNotifyMgr.cancel(intent.getIntExtra(INCOMING_CALL_NOTIFICATION_ID, 0));
                JSONObject callInviteProperties = new JSONObject();
                try {
                    callInviteProperties.putOpt("from", mCallInvite.getFrom());
                    callInviteProperties.putOpt("to", mCallInvite.getTo());
                    callInviteProperties.putOpt("callSid", mCallInvite.getCallSid());
                    String callInviteState = getCallInviteState(mCallInvite.getState());
                    callInviteProperties.putOpt("state",callInviteState);
                } catch (JSONException e) {
                    Log.e(TAG,e.getMessage(),e);
                }
				Log.d(TAG,"oncallinvitereceived");
                javascriptCallback("oncallinvitereceived", callInviteProperties, mInitCallbackContext); 
            } else {
                SoundPoolManager.getInstance(cordova.getActivity()).stopRinging();
				Log.d(TAG,"oncallinvitecanceled");
                javascriptCallback("oncallinvitecanceled",mInitCallbackContext); 
            }
        }
    }


	// Twilio Voice Registration Listener
	private RegistrationListener mRegistrationListener = new RegistrationListener() {
		@Override
		public void onRegistered(String accessToken, String gcmToken) {
            Log.d(TAG, "Registered Voice Client");
		}

		@Override
		public void onError(RegistrationException exception, String accessToken, String gcmToken) {
            Log.e(TAG, "Error registering Voice Client: " + exception.getMessage(), exception);
		}
	};

	// Twilio Voice Call Listener
	private Call.Listener mCallListener = new Call.Listener() {
		@Override
		public void onConnectFailure(Call call, CallException exception) {
			setAudioFocus(false);
			mCall = null;
			javascriptErrorback(exception.getErrorCode(), exception.getMessage(), mInitCallbackContext);
		}

		@Override
		public void onConnected(Call call) {
			setAudioFocus(true);
			mCall = call;

			JSONObject callProperties = new JSONObject();
			try {
				callProperties.putOpt("from", call.getFrom());
				callProperties.putOpt("to", call.getTo());
				String callState = getCallState(call.getState());
				callProperties.putOpt("state",callState);
			} catch (JSONException e) {
				Log.e(TAG,e.getMessage(),e);
			}
			javascriptCallback("oncalldidconnect",callProperties,mInitCallbackContext);
		}

		@Override
		public void onDisconnected(Call call, CallException error) {
			setAudioFocus(false);
			mCall = null;
			javascriptCallback("oncalldiddisconnect",mInitCallbackContext);
		}
	};

	private void setAudioFocus(boolean setFocus) {
        if (audioManager != null) {
            if (setFocus) {
                savedAudioMode = audioManager.getMode();
                // Request audio focus before making any device switch.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    AudioAttributes playbackAttributes = new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build();
                    AudioFocusRequest focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                            .setAudioAttributes(playbackAttributes)
                            .setAcceptsDelayedFocusGain(true)
                            .setOnAudioFocusChangeListener(new AudioManager.OnAudioFocusChangeListener() {
                                @Override
                                public void onAudioFocusChange(int i) { }
                            })
                            .build();
                    audioManager.requestAudioFocus(focusRequest);
                } else {
                    audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL,
                            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
                }
                /*
                 * Start by setting MODE_IN_COMMUNICATION as default audio mode. It is
                 * required to be in this mode when playout and/or recording starts for
                 * best possible VoIP performance. Some devices have difficulties with speaker mode
                 * if this is not set.
                 */
                audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            } else {
                audioManager.setMode(savedAudioMode);
                audioManager.abandonAudioFocus(null);
            }
        }
    }

	private String getCallState(CallState callState) {
		if (callState == CallState.CONNECTED) {
			return "connected";
		} else if (callState == CallState.CONNECTING) {
			return "connecting";
		} else if (callState == CallState.DISCONNECTED) {
			return "disconnected";
		}
		return null;
	}

	private boolean checkPermissionForMicrophone() {
        int resultMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);
        return resultMic == PackageManager.PERMISSION_GRANTED;
    }

}
