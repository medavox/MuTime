package com.medavox.library.mutime;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;

/**Handles caching of offsets to disk using {@link SharedPreferences},
 *
 * This class keeps both an in-memory copy of the time data, plus
 * arbitrates access to the SharedPrefs copy, all under one API.
 * all the API user has to do is call {@link #getTimeData()}.
 * */
final class Persistence implements SntpClient.SntpResponseListener {
    private static final String SHARED_PREFS_KEY = "com.medavox.library.mutime.shared_preferences";
    private static final String KEY_SYSTEM_CLOCK_TIME = "cached_system_clock_time";
    private static final String KEY_DEVICE_UPTIME = "cached_device_uptime";
    private static final String KEY_SNTP_TIME = "cached_sntp_time";

    private static final String TAG = Persistence.class.getSimpleName();

    private static SharedPreferences sharedPrefs = null;

    private static TimeData timeData = null;

    public Persistence(Context context) {
        sharedPrefs = context.getSharedPreferences(SHARED_PREFS_KEY, Context.MODE_PRIVATE);
        Log.i(TAG, "instance:"+this);
    }
    
    /**Retrieve time offset information.
     * Gets in the in-memory copy if it exists,
     * or retrieves it from shared preferences,
     * or finally returns null.
     * @return a {@link TimeData} which can be used to compute the real time,
     * or null if no data is available.*/
    public TimeData getTimeData() {
        if (timeData == null) {
            Log.i(TAG, "no time data in memory, attempting to retrieve from SharedPreferences...");
            if (sharedPreferencesUnavailable()) {
                return null;
            }

            //Log.i(TAG, "is SharedPrefs null:"+(sharedPrefs == null));
            long sntpTime = sharedPrefs.getLong(KEY_SNTP_TIME, -1);
            long upTime = sharedPrefs.getLong(KEY_DEVICE_UPTIME, -1);
            long clockTime = sharedPrefs.getLong(KEY_SYSTEM_CLOCK_TIME, -1);

            if (sntpTime == -1 || upTime == -1 || clockTime == -1) {
                return null;
            }
            //copy the SharedPreferences data into the in-memory variables
            timeData = new TimeData.Builder()
                    .sntpTime(sntpTime)
                    .uptimeAtSntpTime(upTime)
                    .systemClockAtSntpTime(clockTime)
                    .build();
        }

        //check that the difference between the the uptime and system clock timestamps
        //is the same in the TimeData and right now
        //(this checks to make sure the data is still valid)
        long differenceBetweenStoredClocks = timeData.getSystemClockAtSntpTime() - timeData.getUptimeAtSntpTime();
        long differenceBetweenLiveClocks = System.currentTimeMillis() - SystemClock.elapsedRealtime();

        if(Math.abs(differenceBetweenStoredClocks - differenceBetweenLiveClocks) > 10/*milliseconds*/) {
            Log.e(TAG, "Time Data was found to be invalid when checked! " +
                    "A fresh network request is required to compute the correct time.");
            //clear the invalid data
            timeData = null;
            SharedPreferences.Editor e = sharedPrefs.edit();
            e.remove(KEY_SNTP_TIME);
            e.remove(KEY_SYSTEM_CLOCK_TIME);
            e.remove(KEY_DEVICE_UPTIME);
            e.apply();
        }

        return timeData;
    }
    /**Whether this class has any time data to give*/
    public boolean hasTimeData() {
        return getTimeData() != null;
    }


    /**Saves the received {@link TimeData}, both locally as instance variables,
     * and into SharedPreferences.*/
    @Override
    public void onSntpTimeData(TimeData data) {
        Log.d(TAG, "got time info:"+data);
        timeData = data;

        if (sharedPreferencesUnavailable()) {
            return;
        }

        Log.d(TAG, String.format("Saving true time info to disk: " +
                                  "(sntp: [%s]; device: [%s]; clock: [%s])",
                data.getSntpTime(),
                data.getUptimeAtSntpTime(),
                data.getSystemClockAtSntpTime()));

        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putLong(KEY_SNTP_TIME, data.getSntpTime());
        editor.putLong(KEY_SYSTEM_CLOCK_TIME, data.getSystemClockAtSntpTime());
        editor.putLong(KEY_DEVICE_UPTIME, data.getUptimeAtSntpTime());
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
