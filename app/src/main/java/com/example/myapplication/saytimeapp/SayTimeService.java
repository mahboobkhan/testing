/*
    This file is part of Say Time.

    Say Time is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Say Time is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with Say Time.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.example.myapplication.saytimeapp;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.os.AsyncTask;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnUtteranceCompletedListener;
import android.util.Log;
import android.widget.Toast;

import com.example.myapplication.R;

import java.util.Calendar;
import java.util.HashMap;

public class SayTimeService extends Service {

    public static final String SAYTIME_ACTION = "org.nsdev.saytime.SayTimeService.SayTime";
    public static final String CONFIGURATION_ACTION = "org.nsdev.saytime.SayTimeService.Configure";
    public static final boolean TESTING = false; // DO NOT CHECK IN IF TRUE
    private static final String TAG = "SayTimeService";
    private static long mLastTime = 0;
    private WakeLock mWakeLock;
    //    private AudioManager mAudioManager;
    //    private AsyncTask<Void, Void, Void> mKeepAliveTask;
    private ComponentName mComponentName;
    private Object mKeepAliveSync = new Object();
    private OnAudioFocusChangeListener mAudioFocusListener;
    private boolean mSleepRequested = false;
    private boolean mAlarmSet = false;

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        boolean hourlyChime = prefs.getBoolean("trigger_hourly", true);
        AlarmManager alarmManager = (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
        Intent alarmIntent = new Intent(getApplicationContext(), AlarmIntentReceiver.class);
        PendingIntent alarmPendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, alarmIntent, PendingIntent.FLAG_IMMUTABLE);
        if (intent != null && intent.getAction() != null && intent.getAction().equals(CONFIGURATION_ACTION)) {
            if (hourlyChime && !mAlarmSet) {
                Log.d(TAG, "Arming hourly chime.");

                Calendar c = Calendar.getInstance();
                if (!TESTING) {
                    c.clear(Calendar.MILLISECOND);
                    c.add(Calendar.SECOND, 10);
                    c.clear(Calendar.MINUTE);
                    c.clear(Calendar.HOUR);
                }

                alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), 60 * 1000, alarmPendingIntent);
                Log.d(TAG, "Arming REP.");
                mAlarmSet = true;

            } else if (!hourlyChime) {

                Log.d(TAG, "Dis-arming hourly chime.");

                alarmManager.cancel(alarmPendingIntent);

                // Skip this hourly chime entirely
                if (intent.getBooleanExtra("hourly_chime", false))
                    mAlarmSet = false;
            }
        }


        if (mAudioFocusListener == null) {
            mAudioFocusListener = focusChange -> {
                if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                    Log.d(TAG, "Focus Loss Detected: " + focusChange);
                } else {
                    Log.d(TAG, "Focus Changed Detected: " + focusChange);
                }
            };
        }

        if (mComponentName == null)
            mComponentName = new ComponentName(getPackageName(), MediaButtonIntentReceiver.class.getName());


        if (mWakeLock == null) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, this.getClass().getName());
            if (mWakeLock != null)
                mWakeLock.setReferenceCounted(false);
        }

        if (intent != null && intent.getAction() != null && intent.getAction().equals(SAYTIME_ACTION)) {
            boolean skipInterval = intent.getBooleanExtra("skip_interval", false);

            try {
                sayTime(skipInterval);
            } catch (Throwable ex) {
                Log.d(TAG, "Unexpected Error: " + ex.toString());
            }
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void sayTime(final boolean skipInterval) {

        synchronized (mKeepAliveSync) {
            mSleepRequested = true;
            mKeepAliveSync.notifyAll();
        }

        Log.d(TAG, "Preparing to speak...");


        SayTimeApp.mTTSEngineManager.getTTS().setOnUtteranceCompletedListener(new OnUtteranceCompletedListener() {
            @Override
            public void onUtteranceCompleted(String utteranceId) {
                Log.d(TAG, "Utterance Completed Callback");

            }
        });

        String currentTime = formatCurrentTime(skipInterval);
        Log.d(TAG, "Saying: " + currentTime);
        HashMap<String, String> params = new HashMap<String, String>();
        params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "42");

        Toast toast = Toast.makeText(getApplicationContext(), getResources().getString(R.string.SayingTime), Toast.LENGTH_SHORT);
        toast.show();

        SayTimeApp.mTTSEngineManager.getTTS().speak(currentTime, TextToSpeech.QUEUE_FLUSH, params);
    }

    @SuppressLint("StringFormatMatches")
    private String formatCurrentTime(boolean skipInterval) {

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        boolean calculateIntervals = prefs.getBoolean("calculateIntervals", true);

        if (skipInterval)
            calculateIntervals = false;

        boolean terse = prefs.getBoolean("terse", false);

        String am = getResources().getString(R.string.Time_AM);
        String pm = getResources().getString(R.string.Time_PM);
        String inAfternoon = getResources().getString(R.string.Time_InAfternoon);
        String inEvening = getResources().getString(R.string.Time_InEvening);
        String inMorning = getResources().getString(R.string.Time_InMorning);
        String verboseTime = getResources().getString(R.string.Verbose_Time);
        String terseTime = getResources().getString(R.string.Terse_Time);
        String verboseTimeHour = getResources().getString(R.string.Verbose_Time_Hour);
        String terseTimeHour = getResources().getString(R.string.Terse_Time_Hour);

        String itHasBeenStr = getResources().getString(R.string.it_has_been);
        String hourStr = getResources().getString(R.string.hour);
        String hoursStr = getResources().getString(R.string.hours);
        String minuteStr = getResources().getString(R.string.minute);
        String minutesStr = getResources().getString(R.string.minutes);
        String secondStr = getResources().getString(R.string.second);
        String secondsStr = getResources().getString(R.string.seconds);
        String andStr = getResources().getString(R.string.and);
        String sinceYouLastAskedStr = getResources().getString(R.string.since_you_last_asked);

        StringBuffer buf = new StringBuffer();

        Calendar c = Calendar.getInstance();
        long currentTime = c.getTimeInMillis();

        if (currentTime - mLastTime < 5000)
            return "";

        int hour = c.get(Calendar.HOUR);
        int hourOfDay = c.get(Calendar.HOUR_OF_DAY);
        int min = c.get(Calendar.MINUTE);

        if (hour == 0)
            hour = 12;

        String amPm = c.get(Calendar.AM_PM) == Calendar.AM ? am : pm;

        String daySegment = c.get(Calendar.AM_PM) == Calendar.AM ? inMorning : (hourOfDay > 11 && hourOfDay < 18) ? inAfternoon : inEvening;

        String[] t = {"" + hour, "" + min, daySegment, amPm, "" + hourOfDay};

        if (terse && min != 0) {
            buf.append(String.format(terseTime, (Object[]) t));
        } else if (!terse && min != 0) {
            buf.append(String.format(verboseTime, (Object[]) t));
        } else if (terse && min == 0) {
            buf.append(String.format(terseTimeHour, (Object[]) t));
        } else {
            buf.append(String.format(verboseTimeHour, (Object[]) t));
        }

        if (mLastTime != 0 && calculateIntervals) {
            long difference = currentTime - mLastTime;

            int hours = (int) (difference / (1000 * 60 * 60));
            difference -= hours * 1000 * 60 * 60;
            int minutes = (int) (difference / (1000 * 60));
            difference -= minutes * 1000 * 60;
            int seconds = (int) (difference / 1000);

            if (seconds > 0 || minutes > 0 || hours > 0) {

                buf.append(" ");
                buf.append(itHasBeenStr);
                buf.append(" ");

                if (hours > 1)
                    buf.append(hours + " " + hoursStr);
                else if (hours == 1)
                    buf.append(hours + " " + hourStr);

                if (hours > 1 && seconds == 0)
                    buf.append(" " + andStr + " ");
                else
                    buf.append(", ");

                if (minutes > 1)
                    buf.append(minutes + " " + minutesStr);
                else if (minutes == 1)
                    buf.append(minutes + " " + minuteStr);
                else
                    buf.append(", ");

                if ((hours > 0) || (minutes > 0) && seconds != 0)
                    buf.append(" " + andStr + " ");

                if (seconds > 1)
                    buf.append(seconds + " " + secondsStr);
                if (seconds == 1)
                    buf.append(seconds + " " + secondStr);

                if (terse) {
                    buf.append(".");
                } else {
                    buf.append(" " + sinceYouLastAskedStr);
                }

            }
        }

        if (!skipInterval)
            mLastTime = currentTime;

        return buf.toString();
    }
}
