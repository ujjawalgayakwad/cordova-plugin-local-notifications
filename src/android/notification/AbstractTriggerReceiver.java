/*
 * Copyright (c) 2013-2015 by appPlant UG. All rights reserved.
 *
 * @APPPLANT_LICENSE_HEADER_START@
 *
 * This file contains Original Code and/or Modifications of Original Code
 * as defined in and that are subject to the Apache License
 * Version 2.0 (the 'License'). You may not use this file except in
 * compliance with the License. Please obtain a copy of the License at
 * http://opensource.org/licenses/Apache-2.0/ and read it before using this
 * file.
 *
 * The Original Code and all software distributed under the License are
 * distributed on an 'AS IS' basis, WITHOUT WARRANTY OF ANY KIND, EITHER
 * EXPRESS OR IMPLIED, AND APPLE HEREBY DISCLAIMS ALL SUCH WARRANTIES,
 * INCLUDING WITHOUT LIMITATION, ANY WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE, QUIET ENJOYMENT OR NON-INFRINGEMENT.
 * Please see the License for the specific language governing rights and
 * limitations under the License.
 *
 * @APPPLANT_LICENSE_HEADER_END@
 */

package de.appplant.cordova.plugin.notification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Vibrator;
import android.media.*;
import android.net.Uri;
import android.app.KeyguardManager;
import android.app.KeyguardManager.KeyguardLock;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;

/**
 * Abstract broadcast receiver for local notifications. Creates the
 * notification options and calls the event functions for further proceeding.
 */
abstract public class AbstractTriggerReceiver extends BroadcastReceiver {

    public static MediaPlayer mMediaPlayer;
    /**
     * Called when an alarm was triggered.
     *
     * @param context
     *      Application context
     * @param intent
     *      Received intent with content data
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle bundle  = intent.getExtras();
        Options options;

        try {
            String data = bundle.getString(Options.EXTRA);
            JSONObject dict = new JSONObject(data);

            options = new Options(context).parse(dict);
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }

        if (options == null)
            return;

        if (isFirstAlarmInFuture(options))
            return;

        if (options.getLaunchScreen()) {
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
        }
        else {
            Builder builder = new Builder(options);
            Notification notification = buildNotification(builder);
            boolean updated = notification.isUpdate();
            
            onTrigger(notification, updated);
        }
    }

    /**
     * Called when a local notification was triggered.
     *
     * @param notification
     *      Wrapper around the local notification
     * @param updated
     *      If an update has triggered or the original
     */
    abstract public void onTrigger (Notification notification, boolean updated);

    /**
     * Build notification specified by options.
     *
     * @param builder
     *      Notification builder
     */
    abstract public Notification buildNotification (Builder builder);

    /*
     * If you set a repeating alarm at 11:00 in the morning and it
     * should trigger every morning at 08:00 o'clock, it will
     * immediately fire. E.g. Android tries to make up for the
     * 'forgotten' reminder for that day. Therefore we ignore the event
     * if Android tries to 'catch up'.
     */
    private Boolean isFirstAlarmInFuture (Options options) {
        Notification notification = new Builder(options).build();

        if (!notification.isRepeating())
            return false;

        Calendar now    = Calendar.getInstance();
        Calendar alarm  = Calendar.getInstance();

        alarm.setTime(notification.getOptions().getTriggerDate());

        int alarmHour   = alarm.get(Calendar.HOUR_OF_DAY);
        int alarmMin    = alarm.get(Calendar.MINUTE);
        int currentHour = now.get(Calendar.HOUR_OF_DAY);
        int currentMin  = now.get(Calendar.MINUTE);

        return (currentHour != alarmHour && currentMin != alarmMin);
    }

}
