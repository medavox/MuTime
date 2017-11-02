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
    private static final String KEY_SYSTEM_CLOCK_OFFSET = "system clock offset";
    private static final String KEY_UPTIME_OFFSET = "uptime offset";
    private static final String KEY_ROUND_TRIP_DELAY = "round trip delay";

    private static final String TAG = Persistence.class.getSimpleName();

    private static SharedPreferences sharedPrefs = null;

    private static TimeData timeData = null;

    public Persistence(Context context) {
        sharedPrefs = context.getSharedPreferences(SHARED_PREFS_KEY, Context.MODE_PRIVATE);
        Log.i(TAG, "instance:"+this);
    }

    public Persistence() {
        Log.w(TAG, "not providing a Context to access SharedPreferences disables most of Persistence's features!");
        Log.i(TAG, "instance:"+this);
    }


    void enabledDiskCache(Context c) {
        sharedPrefs = c.getSharedPreferences(SHARED_PREFS_KEY, Context.MODE_PRIVATE);
    }

    void disableDiskCache() {
        sharedPrefs = null;
    }
    
    /**Retrieve time offset information.
     * Gets in the in-memory copy if it exists,
     * or retrieves it from shared preferences,
     * or finally returns null.
     * @return a {@link TimeData} which can be used to compute the real time,
     * or null if no data is available.*/
    TimeData getTimeData() {
        if (timeData == null) {
            Log.w(TAG, "no time data in memory, attempting to retrieve from SharedPreferences...");
            if (!sharedPreferencesAvailable()) {
                return null;
            }

            //Log.i(TAG, "is SharedPrefs null:"+(sharedPrefs == null));
            long roundTripDelay = sharedPrefs.getLong(KEY_ROUND_TRIP_DELAY, -1);
            long upTime = sharedPrefs.getLong(KEY_UPTIME_OFFSET, -1);
            long clockTime = sharedPrefs.getLong(KEY_SYSTEM_CLOCK_OFFSET, -1);

            if (roundTripDelay == -1 || upTime == -1 || clockTime == -1) {
                return null;
            }
            //copy the SharedPreferences data into the in-memory variables
            timeData = new TimeData.Builder()
                    .roundTripDelay(roundTripDelay)
                    .uptimeOffset(upTime)
                    .systemClockOffset(clockTime)
                    .build();
        }

        //check that the difference between the the uptime and system clock timestamps
        //is the same in the TimeData and right now
        //(this checks to make sure the data is still valid)
        long storedClocksDiff = Math.abs(timeData.getClockOffset() - timeData.getUptimeOffset());
        long liveClocksDiff = Math.abs(System.currentTimeMillis() - SystemClock.elapsedRealtime());

        if(Math.abs(storedClocksDiff - liveClocksDiff) > 10/*milliseconds*/) {
            Log.e(TAG, "Time Data was found to be invalid when checked! " +
                    //"A fresh network request is required to compute the correct time. " +
                    "stored clock offset: "+timeData.getClockOffset()+"; stored uptime offset: "+
                    timeData.getUptimeOffset()+"; live clock: "+System.currentTimeMillis()+
                    "; live uptime: "+SystemClock.elapsedRealtime()+
                    "; Stored Clock difference: "+storedClocksDiff+"; live Clock difference: "+liveClocksDiff);
            //clear the invalid data
            timeData = null;
            SharedPreferences.Editor e = sharedPrefs.edit();
            e.remove(KEY_ROUND_TRIP_DELAY);
            e.remove(KEY_SYSTEM_CLOCK_OFFSET);
            e.remove(KEY_UPTIME_OFFSET);
            e.apply();
        }

        return timeData;
    }
    /**Whether this class has any time data to give*/
    boolean hasTimeData() {
        return getTimeData() != null;
    }

    /**Whether this class has a valid reference to a {@link SharedPreferences} instance*/
    boolean diskCacheEnabled() {
        return sharedPrefs != null;
    }


    /**Saves the received {@link TimeData}, both locally as instance variables,
     * and into SharedPreferences.*/
    @Override
    public void onSntpTimeData(TimeData data) {
        if(data != null) {
            //Log.i(TAG, "got time info:"+data);
            timeData = data;

            if (!sharedPreferencesAvailable()) {
                return;
            }

            Log.d(TAG, "Saving true time info to disk: " + data);

            SharedPreferences.Editor editor = sharedPrefs.edit();
            editor.putLong(KEY_ROUND_TRIP_DELAY, data.getRoundTripDelay());
            editor.putLong(KEY_SYSTEM_CLOCK_OFFSET, data.getClockOffset());
            editor.putLong(KEY_UPTIME_OFFSET, data.getUptimeOffset());
            editor.apply();
        }
    }

    // -----------------------------------------------------------------------------------

    private boolean sharedPreferencesAvailable() {
        if (sharedPrefs == null) {
            Log.w(TAG, "SharedPreferences is null, cannot interact with SharedPreferences time data on-disk.");
            return false;
        }
        return true;
    }
}
