package com.medavox.library.mutime

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Log

private const val SHARED_PREFS_KEY = "com.medavox.library.mutime.shared_preferences"
private const val KEY_SYSTEM_CLOCK_OFFSET = "system clock offset"
private const val KEY_UPTIME_OFFSET = "uptime offset"
private const val KEY_ROUND_TRIP_DELAY = "round trip delay"

private const val TAG = "Persistence"



private var sharedPrefs :SharedPreferences? = null
private var timeData: TimeData? = null

/**Handles caching of offsets to disk using {@link SharedPreferences},
 *
 * This class keeps both an in-memory copy of the time data, plus
 * arbitrates access to the SharedPrefs copy, all under one API.
 * all the API user has to do is call {@link #getTimeData()}.
 * */
internal class Persistence(context: Context? = null) : SntpClient.SntpResponseListener {

    init{
        if(context == null) {
            Log.w(TAG, "Not providing a Context prevents recovery from clock changes!")
        }else {
            sharedPrefs = context.getSharedPreferences(SHARED_PREFS_KEY, Context.MODE_PRIVATE)
        }
        Log.i(TAG, "instance:"+this)
    }

    fun enableDiskCache(c:Context ) {
        sharedPrefs = c.getSharedPreferences(SHARED_PREFS_KEY, Context.MODE_PRIVATE)
    }

    fun disableDiskCache() {
        Log.w(TAG, "Not providing a Context prevents recovery from clock changes!")
        sharedPrefs = null
    }
    
    /**Retrieve time offset information.
     * Gets in the in-memory copy if it exists,
     * or retrieves it from shared preferences,
     * or finally returns null.
     * @return a {@link TimeData} which can be used to compute the real time,
     * or null if no data is available.*/
    @Throws(MissingTimeDataException::class)
    fun getTimeData():TimeData {
        val td = timeData
        if (td == null) {
            Log.w(TAG, "no time data in memory, attempting to retrieve from SharedPreferences...")
            if (!sharedPreferencesAvailable()) {
                throw MissingTimeDataException("Disk Cache not enabled, can't check SharedPreferences!")
            }

            //Log.i(TAG, "is SharedPrefs null:"+(sharedPrefs == null));
            val roundTripDelay = sharedPrefs!!.getLong(KEY_ROUND_TRIP_DELAY, -1L)
            val upTime = sharedPrefs!!.getLong(KEY_UPTIME_OFFSET, -1L)
            val clockTime = sharedPrefs!!.getLong(KEY_SYSTEM_CLOCK_OFFSET, -1L)

            if (roundTripDelay == -1L || upTime == -1L || clockTime == -1L) {
                throw MissingTimeDataException("no time data in SharedPreferences either. " +
                        "Has MuTime been run at least once?")
            }
            //copy the SharedPreferences data into the in-memory variables
            timeData = TimeData(
                    roundTripDelay=roundTripDelay,
                    uptimeOffset=upTime,
                    systemClockOffset=clockTime)
        }

        val td2:TimeData = timeData!!

        //At this point, we already had or have successfully retrieved TimeData.
        //Check that the data is still valid:
        //check that the difference between the the uptime and system clock timestamps
        //is the same in the TimeData and right now
        val storedClocksDiff:Long = Math.abs(td2.systemClockOffset - td2.uptimeOffset)
        val liveClocksDiff:Long = Math.abs(System.currentTimeMillis() - SystemClock.elapsedRealtime())

        //if there's more than a 10ms difference between the stored value and the live one,
        //of the difference between the system and uptime clock
        if(Math.abs(storedClocksDiff - liveClocksDiff) > 10/*milliseconds*/) {
            Log.e(TAG, "Time Data was found to be invalid when checked! " +
                    //"A fresh network request is required to compute the correct time. " +
                    "stored clock offset: "+td2.systemClockOffset+"; stored uptime offset: "+
                    td2.uptimeOffset+"; live clock: "+System.currentTimeMillis()+
                    "; live uptime: "+SystemClock.elapsedRealtime()+
                    "; Stored Clock difference: "+storedClocksDiff+"; live Clock difference: "+liveClocksDiff);
            //clear the invalid data
            //correct data > slightly wrong data > no data > very wrong data
            timeData = null
            val editor:SharedPreferences.Editor? = sharedPrefs?.edit()
            if(editor != null) {
                editor.remove(KEY_ROUND_TRIP_DELAY)
                editor.remove(KEY_SYSTEM_CLOCK_OFFSET)
                editor.remove(KEY_UPTIME_OFFSET)
                editor.apply()
            }
        }

        return td2
    }
    /**Whether this class has any time data to give*/
    fun hasTimeData():Boolean {
        return getTimeData() != null
    }

    /**Whether this class has a valid reference to a {@link SharedPreferences} instance*/
    fun diskCacheEnabled():Boolean {
        return sharedPrefs != null;
    }


    /**Saves the received {@link TimeData}, both locally as instance variables,
     * and into SharedPreferences.*/
    override fun onSntpTimeData(data:TimeData) {
        //Log.i(TAG, "got time info:"+data);
        timeData = data

        if (!sharedPreferencesAvailable()) {
            return;
        }

        Log.d(TAG, "Saving true time info to disk: $data");

        val editor:SharedPreferences.Editor? = sharedPrefs?.edit();
        if(editor != null) {
            editor.putLong(KEY_ROUND_TRIP_DELAY, data.roundTripDelay)
            editor.putLong(KEY_SYSTEM_CLOCK_OFFSET, data.systemClockOffset)
            editor.putLong(KEY_UPTIME_OFFSET, data.uptimeOffset)
            editor.apply()
        }
    }

    // -----------------------------------------------------------------------------------

    private fun sharedPreferencesAvailable():Boolean {
        if (sharedPrefs == null) {
            Log.w(TAG, "SharedPreferences is null, can't interact with any time data on-disk!")
            return false
        }
        return true
    }
}
