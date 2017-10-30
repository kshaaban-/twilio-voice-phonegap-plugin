package com.phonegap.plugins.twiliovoice;

import android.content.Context;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.util.Log;

import static android.content.Context.AUDIO_SERVICE;

public class SoundPoolManager {

    private boolean playing = false;
    private boolean loaded = false;
    private float actualVolume;
    private float maxVolume;
    private float volume;
    private AudioManager audioManager;
    private SoundPool soundPool;
    private int ringingSoundId;
    private int ringingStreamId;
    private int disconnectSoundId;
    private static SoundPoolManager instance;

    public final static String TAG = "TwilioVoicePlugin";

    private SoundPoolManager(Context context) {
        // AudioManager audio settings for adjusting the volume
        Log.d(TAG, "Creating SoundPoolManager()");
        audioManager = (AudioManager) context.getSystemService(AUDIO_SERVICE);
        actualVolume = (float) audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        maxVolume = (float) audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        volume = actualVolume / maxVolume;

        // Load the sounds
        int maxStreams = 1;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Log.d(TAG, "getInstance() of SoundPoolManager");
            soundPool = new SoundPool.Builder()
                    .setMaxStreams(maxStreams)
                    .build();
        } else {
            Log.d(TAG, "SoundPoolManager --> Creating new SoundPool");
            soundPool = new SoundPool(maxStreams, AudioManager.STREAM_MUSIC, 0);
        }

        soundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
            @Override
            public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
                loaded = true;
            }

        });

        int ringingResourceId =  context.getResources().getIdentifier("ringing", "raw", context.getPackageName());
        ringingSoundId = soundPool.load(context, ringingResourceId, 1);
        //disconnectSoundId = soundPool.load(context, R.raw.disconnect, 1);
    }

    public static SoundPoolManager getInstance(Context context) {
        Log.d(TAG, "getInstance() of SoundPoolManager");
        if (instance == null) {
            Log.d(TAG, "SoundPoolManager context instance == null");
            instance = new SoundPoolManager(context);
        }
        return instance;
    }

    public void playRinging() {
        if (loaded && !playing) {
            ringingStreamId = soundPool.play(ringingSoundId, volume, volume, 1, -1, 1f);
            playing = true;
        }
    }

    public void stopRinging() {
        if (playing) {
            soundPool.stop(ringingStreamId);
            playing = false;
        }
    }

    public void playDisconnect() {
        if (loaded && !playing) {
            soundPool.play(disconnectSoundId, volume, volume, 1, 0, 1f);
            playing = false;
        }
    }

    public void release() {
        if (soundPool != null) {
            soundPool.unload(ringingSoundId);
            soundPool.unload(disconnectSoundId);
            soundPool.release();
            soundPool = null;
        }
        instance = null;
    }

}