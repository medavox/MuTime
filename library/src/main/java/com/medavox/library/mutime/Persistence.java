package com.medavox.library.mutime;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

/**Handles caching of offsets to disk using {@link SharedPreferences},
 * and listening for device reboots and clock changes by the user.
 * Register this class as a broadcast receiver for {@link Intent#ACTION_BOOT_COMPLETED BOOT_COMPLETED}
 * and {@link Intent#ACTION_TIME_CHANGED TIME_CHANGED},
 * to allow MuTime to correct its offsets against these events.*/
public class Persistence extends BroadcastReceiver implements SntpClient.SntpResponseListener {
//todo: this class now keeps both the in-memory copy of the time data, plus
//todo: arbitrates access to the SharedPrefs copy, all under one API.
    //all the API user has to do is ask for the data,
    //and persistence will give them in the in-memory copy if it has any,
// retrieves it from shared preferences,
    //or finally makes a network request
    private static final String SHARED_PREFS_KEY = "com.medavox.library.mutime.shared_preferences";
    private static final String KEY_CACHED_BOOT_TIME = "cached_boot_time";
    private static final String KEY_CACHED_DEVICE_UPTIME = "cached_device_uptime";
    private static final String KEY_CACHED_SNTP_TIME = "cached_sntp_time";

    private static final String TAG = Persistence.class.getSimpleName();

    private SharedPreferences sharedPrefs = null;

    private static TimeData sntpResponse = null;

    public Persistence(Context context) {
        sharedPrefs = context.getSharedPreferences(SHARED_PREFS_KEY, Context.MODE_PRIVATE);
    }

    /**Can be null*/
    public static TimeData getSntpTimeData() {
        return sntpResponse;
    }

    //todo
    @Override
    public void onReceive(Context context, Intent intent) {
        switch(intent.getAction()) {
            case Intent.ACTION_BOOT_COMPLETED:
                Log.i(TAG, "clearing MuTime disk cache as we've detected a boot");
                clearCachedInfo();
                break;

            case Intent.ACTION_TIME_CHANGED:
                Log.i(TAG, "manual system clock change detected; clearing MuTime disk cache");
                clearCachedInfo();
        }
    }

    @Override
    public void onSntpTimeData(TimeData data) {
        sntpResponse = data;
        saveTrueTimeInfoToDisk(data);
    }

    private void saveTrueTimeInfoToDisk(TimeData data) {
        if (sharedPreferencesUnavailable()) {
            return;
        }

        long sntpTime = data.getSntpTime();
        long deviceUptime = data.getUptimeAtSntpTime();
        long bootTime = sntpTime - deviceUptime;

        Log.d(TAG, String.format("Caching true time info to disk: " +
                                  "(sntp: [%s]; device: [%s]; boot: [%s])",
                                sntpTime,
                                deviceUptime,
                                bootTime));

        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putLong(KEY_CACHED_BOOT_TIME, bootTime);
        editor.putLong(KEY_CACHED_DEVICE_UPTIME, deviceUptime);
        editor.putLong(KEY_CACHED_SNTP_TIME, sntpTime);
        editor.apply();
    }
/*
    boolean isTrueTimeCachedFromAPreviousBoot() {
        if (sharedPreferencesUnavailable()) {
            return false;
        }

        long cachedBootTime = sharedPrefs.getLong(KEY_CACHED_BOOT_TIME, 0L);
        if (cachedBootTime == 0) {
            return false;
        }

        // has boot time changed (simple check)
        boolean bootTimeChanged = SystemClock.elapsedRealtime() < getCachedDeviceUptime();
        Log.i(TAG, "---- boot time changed " + bootTimeChanged);
        return !bootTimeChanged;
    }

    long getCachedDeviceUptime() {
        if (sharedPreferencesUnavailable()) {
            return 0L;
        }

        return sharedPrefs.getLong(KEY_CACHED_DEVICE_UPTIME, 0L);
    }

    long getCachedSntpTime() {
        if (sharedPreferencesUnavailable()) {
            return 0L;
        }

        return sharedPrefs.getLong(KEY_CACHED_SNTP_TIME, 0L);
    }
*/
    // -----------------------------------------------------------------------------------

    private void clearCachedInfo() {
        if (sharedPreferencesUnavailable()) {
            return;
        }
        sharedPrefs.edit().clear().apply();
    }

    private boolean sharedPreferencesUnavailable() {
        if (sharedPrefs == null) {
            Log.e(TAG, "SharedPreferences was null; Cannot cache NTP offset for MuTime. ");
            return true;
        }
        return false;
    }
}
