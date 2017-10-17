package com.medavox.library.mutime;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;

/**Handles caching of offsets to disk using {@link SharedPreferences},
 * and listening for device reboots and clock changes by the user.
 * Register this class as a broadcast receiver for {@link Intent#ACTION_BOOT_COMPLETED BOOT_COMPLETED}
 * and {@link Intent#ACTION_TIME_CHANGED TIME_CHANGED},
 * to allow MuTime to correct its offsets against these events.
 *
 * This class now keeps both the in-memory copy of the time data, plus
  arbitrates access to the SharedPrefs copy, all under one API.
 all the API user has to do is call {@link #getTimeData()} ,
 and this class will give them in the in-memory copy if it has any,
 retrieve it from shared preferences,
 or finally return null.
 * */
class Persistence extends BroadcastReceiver implements SntpClient.SntpResponseListener {
//
    private static final String SHARED_PREFS_KEY = "com.medavox.library.mutime.shared_preferences";
    private static final String KEY_SYSTEM_CLOCK_TIME = "cached_system_clock_time";
    private static final String KEY_DEVICE_UPTIME = "cached_device_uptime";
    private static final String KEY_SNTP_TIME = "cached_sntp_time";

    private static final String TAG = Persistence.class.getSimpleName();

    private static SharedPreferences sharedPrefs = null;

    private static TimeData sntpResponse = null;

    private IntentFilter timeChangeFilter = new IntentFilter(Intent.ACTION_TIME_CHANGED);

    public Persistence(Context context) {
        sharedPrefs = context.getSharedPreferences(SHARED_PREFS_KEY, Context.MODE_PRIVATE);
        Log.i(TAG, "instance:"+this);
    }

    public TimeData getTimeData() {
        if (sntpResponse != null) {
            return sntpResponse;
        }
        else {
            Log.i(TAG, "no TimeData in memory, attempting to retrieve from SharedPreferences...");
            //Log.i(TAG, "is SharedPrefs null:"+(sharedPrefs == null));
            long sntpTime = sharedPrefs.getLong(KEY_SNTP_TIME, -1);
            long upTime = sharedPrefs.getLong(KEY_DEVICE_UPTIME, -1);
            long clockTime = sharedPrefs.getLong(KEY_SYSTEM_CLOCK_TIME, -1);

            if(sntpTime == -1 || upTime == -1 || clockTime == -1) {
                return null;
            }
            return new TimeData.Builder()
                    .sntpTime(sntpTime)
                    .uptimeAtSntpTime(upTime)
                    .systemClockAtSntpTime(clockTime)
                    .build();
        }
    }

    public boolean hasTimeData() {
        return getTimeData() != null;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        TimeData old = getTimeData();
        Log.i(TAG, "action \""+intent.getAction()+"\" detected. Repairing TimeData...");
        switch(intent.getAction()) {
            case Intent.ACTION_BOOT_COMPLETED:
                //uptime can no longer be trusted

                long newTrustedUptimeAtSntpTime =
                        SystemClock.elapsedRealtime() - (System.currentTimeMillis() - old.getSystemClockAtSntpTime());
                TimeData fixedUptime = new TimeData.Builder(old)
                        .systemClockAtSntpTime(newTrustedUptimeAtSntpTime)
                        .build();
                onSntpTimeData(fixedUptime);

                break;

            case Intent.ACTION_TIME_CHANGED:
                //system clock can no longer be trusted
                /*so if the uptime was x at a KNOWN true time,
                * and we know the uptime is y nowAsDate,
                * then we know it's been z since the known true time.
                *
                * get the new system clock value as of now (w),
                * then subtract z from it.
                *
                * THAT is the new systemclock time as of the sntp response
                */

                long newTrustedSystemClockAtSntpTime =
            System.currentTimeMillis() - (SystemClock.elapsedRealtime() - old.getUptimeAtSntpTime());
                TimeData fixedSystemClockTime = new TimeData.Builder(old)
                        .systemClockAtSntpTime(newTrustedSystemClockAtSntpTime)
                        .build();
                onSntpTimeData(fixedSystemClockTime);
        }
    }

    /**Saves the received {@link TimeData}, both locally as instance variables,
     * and into SharedPreferences.*/
    @Override
    public void onSntpTimeData(TimeData data) {
        sntpResponse = data;

        if (sharedPreferencesUnavailable()) {
            return;
        }

        //long bootTime = sntpTime - deviceUptime;

        Log.d(TAG, String.format("Saving true time info to disk: " +
                                  "(sntp: [%s]; device: [%s]; clock: [%s])",
                data.getSntpTime(),
                data.getUptimeAtSntpTime(),
                data.getSystemClockAtSntpTime()));

        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putLong(KEY_SYSTEM_CLOCK_TIME, data.getSystemClockAtSntpTime());
        editor.putLong(KEY_DEVICE_UPTIME, data.getUptimeAtSntpTime());
        editor.putLong(KEY_SNTP_TIME, data.getSntpTime());
        editor.apply();
    }

    // -----------------------------------------------------------------------------------

    private boolean sharedPreferencesUnavailable() {
        if (sharedPrefs == null) {
            Log.e(TAG, "SharedPreferences was null; Cannot cache NTP offset for MuTime. ");
            return true;
        }
        return false;
    }
}
