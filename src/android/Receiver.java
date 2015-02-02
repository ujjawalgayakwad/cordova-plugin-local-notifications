/*
    Copyright 2013-2014 appPlant UG

    Licensed to the Apache Software Foundation (ASF) under one
    or more contributor license agreements.  See the NOTICE file
    distributed with this work for additional information
    regarding copyright ownership.  The ASF licenses this file
    to you under the Apache License, Version 2.0 (the
    "License"); you may not use this file except in compliance
    with the License.  You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing,
    software distributed under the License is distributed on an
    "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
    KIND, either express or implied.  See the License for the
    specific language governing permissions and limitations
    under the License.
*/

package de.appplant.cordova.plugin.localnotification;

import java.util.Calendar;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.KeyguardManager;
import android.app.KeyguardManager.KeyguardLock;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Vibrator;
import android.media.*;
import android.net.Uri;
import android.util.Log;

import android.support.v4.app.NotificationCompat.Builder;

import de.appplant.cordova.plugin.notification.*;
import de.appplant.cordova.plugin.notification.Options;

/**
 * The alarm receiver is triggered when a scheduled alarm is fired. This class
 * reads the information in the intent and displays this information in the
 * Android notification bar. The notification uses the default notification
 * sound and it vibrates the phone.
 */
public class Receiver extends BroadcastReceiver {

    public static final String OPTIONS = "LOCAL_NOTIFICATION_OPTIONS";
    public static MediaPlayer mMediaPlayer;
    private static final String TAG = "clarkActivity";

    private Options options;

    @Override
    public void onReceive (Context context, Intent intent) {
    	NotificationWrapper nWrapper = new NotificationWrapper(context,
    			Receiver.class,LocalNotification.PLUGIN_NAME,OPTIONS);
        Options options = null;
        Bundle bundle   = intent.getExtras();
        JSONObject args;

        try {
            args    = new JSONObject(bundle.getString(OPTIONS));
            options = new Options(context).parse(args);
        } catch (JSONException e) {
            return;
        }
        this.options = options;

        boolean launchScreen = options.getLaunchScreen();

    	NotificationBuilder builder = new NotificationBuilder(options,context,OPTIONS,
    			DeleteIntentReceiver.class,ReceiverActivity.class);

        // The context may got lost if the app was not running before
        LocalNotification.setContext(context);

        fireTriggerEvent();

        if (options.getInterval() == 0) {
        } else if (isFirstAlarmInFuture()) {
            return;
        } else {
        	JSONArray data = new JSONArray().put(options.getJSONObject());
        	LocalNotification.fireEvent("updateCall", options.getId(), options.getJSON(),data);
            nWrapper.schedule(options.moveDate());
        }
        Log.v(TAG, "launch= " + launchScreen);
        if (launchScreen) {
            PowerManager pm = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
            WakeLock wakeLock = pm.newWakeLock((PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP), "TAG");
            wakeLock.acquire();
            
            KeyguardManager keyguardManager = (KeyguardManager)context.getSystemService(Context.KEYGUARD_SERVICE); 
            KeyguardLock keyguardLock =  keyguardManager.newKeyguardLock("TAG");
            keyguardLock.disableKeyguard();
            
            Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            long[] pattern = {1000, 2000, 1000, 2000};
            v.vibrate(pattern, 0);
            
            try {
                Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                mMediaPlayer = new MediaPlayer();
                mMediaPlayer.setDataSource(context, alarmSound);
                final AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                if (audioManager.getStreamVolume(AudioManager.STREAM_ALARM) != 0) {
                    mMediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
                    mMediaPlayer.setLooping(true);
                    mMediaPlayer.prepare();
                    mMediaPlayer.start();
                }
             } catch (Exception e) {
                 e.printStackTrace();
             }
            intent = new Intent();
            intent.setAction("de.appplant.cordova.plugin.localnotification.ALARM");
            intent.setPackage(context.getPackageName());
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } else if (!LocalNotification.isInBackground && options.getForegroundMode()){
        	if (options.getInterval() == 0) {
        		LocalNotification.unpersist(options.getId());
        	}
        	nWrapper.showNotificationToast(options);
        	fireTriggerEvent();
        } else {
        	Builder notification = builder.buildNotification();

        	nWrapper.showNotification(notification, options);
        }
    }

    /*
     * If you set a repeating alarm at 11:00 in the morning and it
     * should trigger every morning at 08:00 o'clock, it will
     * immediately fire. E.g. Android tries to make up for the
     * 'forgotten' reminder for that day. Therefore we ignore the event
     * if Android tries to 'catch up'.
     */
    private Boolean isFirstAlarmInFuture () {
        if (options.getInterval() > 0) {
            Calendar now    = Calendar.getInstance();
            Calendar alarm  = options.getCalendar();

            int alarmHour   = alarm.get(Calendar.HOUR_OF_DAY);
            int alarmMin    = alarm.get(Calendar.MINUTE);
            int currentHour = now.get(Calendar.HOUR_OF_DAY);
            int currentMin  = now.get(Calendar.MINUTE);

            if (currentHour != alarmHour && currentMin != alarmMin) {
                return true;
            }
        }

        return false;
    }

    /**
     * Fires ontrigger event.
     */
    private void fireTriggerEvent () {
    	JSONArray data = new JSONArray().put(options.getJSONObject());
        LocalNotification.fireEvent("trigger", options.getId(), options.getJSON(),data);
    }
}
