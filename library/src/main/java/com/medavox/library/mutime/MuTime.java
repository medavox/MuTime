package com.medavox.library.mutime;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.SystemClock;
import android.util.Log;

import java.io.IOException;

/**Base class for accessing the MuTime API.
 * To get the actual time:
 *<pre>
 * {@code
 * MuTime.requestTimeFromServer("time.google.com").send();//use any ntp server address here, eg "time.apple.com"
 *
 *  //get the real time in unix epoch format (milliseconds since midnight on 1 january 1970)
 * try {
 *     long trueTime = MuTime.now();//throws MissingTimeDataException if we don't know the time
 * }
 * catch (Exception e) {
 *     Log.e("MuTime", "failed to get the actual time:+e.getMessage());
 * }
 * }</pre>*/
public class MuTime {
    static final Persistence persistence = new Persistence();

    private static TimeDataPreserver preserver = null;
    private static final String TAG = MuTime.class.getSimpleName();

    private MuTime() {
        throw new AssertionError("this class should not be instantiated");
    }

    /**Call this at least once to get reliable time from an NTP server.*/
    public static void requestTimeFromServer(String ntpHost) throws IOException {
        new SntpRequest(ntpHost, persistence).send();
    }

    public static SntpRequest buildCustomSntpRequest(String ntpHost) {
        return new SntpRequest(ntpHost, persistence);
    }

    /**Whether or not MuTime knows the actual time.
     * @return Whether or not an immediate call to {@link #now()} would throw a
     * {@link MissingTimeDataException}.*/
    public static boolean hasTheTime() {
        return persistence.hasTimeData();
    }

    /**Get the True Time in Unix Epoch format: milliseconds since midnight on 1 January, 1970.
     * The current time in the default Timezone.
     *
     *<p>NOTE: this method makes a synchronous network call,
     * so you must call it from a background thread (not the the main/UI thread)
     * or you will get a {@link android.os.NetworkOnMainThreadException}.
     * </p>
     * <p>
     * Try something like this:
     * </p>
     * <pre>
     * {@code
     *  new Thread(){
            @Override
            public void run() {
                Log.i(TAG, "trying to get the time...");
                MuTime.enableDiskCaching(MainActivity.this);
                try {
                    MuTime.requestTimeFromServer("time.google.com");
                }
                catch(IOException ioe) {
                    Log.e(TAG, "network error while getting the time:"+ioe);
                }
                //...
                try {
                    Log.i(TAG, "time gotten:"+new Date(MuTime.now()));
                }
                catch (MissingTimeDataException mtde) {
                    Log.e(TAG, mtde.toString());
                }
            }
        }.start();
        //...
        //from an activity:
        makeRequest.execute(this);

     * }</pre>
     * @return a Unix Epoch-format timestamp of the actual current time, in the default timezone.*/
    public static long now() throws MissingTimeDataException {
        /*3 possible states:
            1. We have fresh SNTP data from a recently-made request, store (atm) in SntpClient
            2. We have cached SNTP data from SharedPreferences
            3. We have no/invalid SNTP data,
             and won't know the correct time until we make a network request
*/
        TimeData timeData = persistence.getTimeData();//throws NullPointerException
        if(timeData == null) {
            throw new MissingTimeDataException("time data is missing or invalid. " +
                    "Please make an NTP network request by calling " +
                    "MuTime.requestTimeFromServer(String) to refresh the true time");
        }

        //these values should be identical, or near as dammit
        long timeFromUptime = SystemClock.elapsedRealtime() + timeData.getUptimeOffset();
        long timeFromClock = System.currentTimeMillis() + timeData.getClockOffset();

        //10ms is probably quite lenient
        if(Math.abs(timeFromClock - timeFromUptime) > 10) {
            throw new MissingTimeDataException("offsets for clocks did not agree on the time - " +
                    "offset from uptime makes it "+timeFromUptime+", " +
                    "but the offset from the clock makes it "+timeFromClock);


        }
        return timeFromClock;
    }

    /**Enable the use of {@link android.content.SharedPreferences}
     * to store time data across app closes, system reboots and system clock meddling.
     * @param c a Context object which is needed for accessing SharedPreferences*/
    public static void enableDiskCaching(Context c) {
        persistence.enabledDiskCache(c);
    }

    /**Disable storing of Time Data on-disk.
     * This method is provided for the sake of API completeness; why would you actually want to???*/
    public static void disableDiskCaching() {
        persistence.disableDiskCache();
    }

    /**Check whether disk caching has been enabled.
     * @return whether disk caching has been enabled*/
    public static boolean diskCacheEnabled() {
        return persistence.diskCacheEnabled();
    }

    /**Adds a {@link android.content.BroadcastReceiver} which listens for the user changing the clock,
     * or the device rebooting. In these cases,
     * it repairs the partially-invalidated Time Data using the remaining intact information.
     * @param c Needed for accessing the Android BroadcastReceiver API,
     *          eg {@link Context#registerReceiver(BroadcastReceiver, IntentFilter)}*/
    public static void registerDataPreserver(Context c) {
        if(preserver == null) {
            preserver = new TimeDataPreserver(persistence);
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(Intent.ACTION_BOOT_COMPLETED);
            intentFilter.addAction(Intent.ACTION_TIME_CHANGED);
            c.registerReceiver(preserver, intentFilter);
        }
        else {
            Log.w(TAG, "call to registerDataPreserver(Context) was unnecessary: we are already registered");
        }
    }

    public static void unregisterDataPreserver(Context c) {
        if(preserver != null) {
            c.unregisterReceiver(preserver);
            preserver = null;
        }
        else {
            Log.w(TAG, "call to unregisterDataPreserver(Context) was unnecessary: " +
                    "there is no TimeDataPreserver currently registered");
        }
    }
}