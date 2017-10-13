package com.instacart.library.truetime;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;

class DiskCacheClient {

    private static final String SHARED_PREFS_KEY = "com.instacart.library.truetime.shared_preferences";
    private static final String KEY_CACHED_BOOT_TIME = "com.instacart.library.truetime.cached_boot_time";
    private static final String KEY_CACHED_DEVICE_UPTIME = "com.instacart.library.truetime.cached_device_uptime";
    private static final String KEY_CACHED_SNTP_TIME = "com.instacart.library.truetime.cached_sntp_time";

    private static final String TAG = DiskCacheClient.class.getSimpleName();

    private SharedPreferences sharedPrefs = null;

    public DiskCacheClient(Context context) {
        sharedPrefs = context.getSharedPreferences(SHARED_PREFS_KEY, Context.MODE_PRIVATE);
    }

    void clearCachedInfo() {
        if (sharedPreferencesUnavailable()) {
            return;
        }
        sharedPrefs.edit().clear().apply();
    }

    void cacheTrueTimeInfo(SntpClient sntpClient) {
        if (sharedPreferencesUnavailable()) {
            return;
        }

        long cachedSntpTime = sntpClient.getCachedSntpTime();
        long cachedDeviceUptime = sntpClient.getCachedDeviceUptime();
        long bootTime = cachedSntpTime - cachedDeviceUptime;

        Log.d(TAG,
                  String.format("Caching true time info to disk: (sntp: [%s]; device: [%s]; boot: [%s])",
                                cachedSntpTime,
                                cachedDeviceUptime,
                                bootTime));

        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putLong(KEY_CACHED_BOOT_TIME, bootTime);
        editor.putLong(KEY_CACHED_DEVICE_UPTIME, cachedDeviceUptime);
        editor.putLong(KEY_CACHED_SNTP_TIME, cachedSntpTime);
        editor.apply();

    }

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

    // -----------------------------------------------------------------------------------

    private boolean sharedPreferencesUnavailable() {
        if (sharedPrefs == null) {
            Log.e(TAG, "SharedPreferences was null; Cannot cache NTP offset for TrueTime. ");
            return true;
        }
        return false;
    }
}
