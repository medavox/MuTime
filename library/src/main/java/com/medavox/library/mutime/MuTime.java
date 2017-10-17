package com.medavox.library.mutime;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.SystemClock;
import android.util.Log;

import java.io.IOException;
import java.util.Locale;

/**Base class for accessing the MuTime API.
 * To get the actual time:
 *<pre>
 * {@code
 * MuTime mu = MuTime.getInstance(ctx);//initialise our singleton
 *
 * boolean doWeKnowWhatTheRealTimeIsYet = MuTime.hasTheTime();//false -- no!
 * //calling mu.now() or mu.nowAsDate() at this point would cause a MissingTimeDataException
 *
 * mu.requestTimeFromServer("time.google.com");//use any ntp server address here, eg "time.apple.com"
 * boolean doWeKnowTheTimeNow = MuTime.hasTheTime();//true -- yes!
 *
 * //get the real time in unix epoch format (milliseconds since midnight on 1 january 1970)
 * try {
 *     long trueTime = mu.now();//throws MissingTimeDataException if we don't know the time
 * }
 * catch (Exception e) {
 *     Log.e("MuTime", "failed to get the actual time:+e.getMessage());
 * }
 * }</pre>*/
public class MuTime<InstanceType extends MuTime> {
    protected static Persistence persistence;
    protected static final SntpClient SNTP_CLIENT = new SntpClient();

    private static MuTime INSTANCE;
    private static float _rootDelayMax = 100;
    private static float _rootDispersionMax = 100;
    private static int _serverResponseDelayMax = 200;
    private static int _udpSocketTimeoutInMillis = 30_000;
    private static final String TAG = MuTime.class.getSimpleName();

    private TimeDataPreserver preserver = null;

    public static MuTime getInstance(Context c) {
        if(persistence == null) {
            persistence = new Persistence(c);
        }
        if (INSTANCE == null) {
            INSTANCE = new MuTime(persistence);
        }
        return INSTANCE;
    }

    protected MuTime(Persistence p) {
        this.persistence = p;
    }

    /**Call this at least once to get reliable time from an NTP server.*/
    public long[] requestTimeFromServer(String ntpHost) throws IOException {
        return SNTP_CLIENT.requestTime(ntpHost,
                _rootDelayMax,
                _rootDispersionMax,
                _serverResponseDelayMax,
                _udpSocketTimeoutInMillis,
                persistence);
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
     * so to call it from the UI (main) thread you must wrap it in its own thread, like this:
     * <pre>
     * {@code
     *  private AsyncTask<Context, Void, Void> makeRequest = new AsyncTask<Context, Void, Void>() {
            private final static String TAG = "MuTime Process";
            @Override
            protected Void doInBackground(Context... c) {
                String server = "time.google.com";
                try {
                    MuTimeRx t = MuTimeRx.getInstance(c[0]);
                    if(MuTime.hasTheTime()) {
                        Log.i(TAG, "Using stored time:"+new Date(t.now()));
                    }
                    else {
                        Log.i(TAG, "Requesting time from " + server + "...");
                        t.requestTimeFromServer(server);
                        Log.i(TAG, "time gotten:" + new Date(t.now()));
                    }
                }
                catch(Exception e) {
                    Log.e(TAG, "Failed to get time from "+server+"; exception:"+e);
                    e.printStackTrace();
                }
                return null;
            }
        };
        //...
        //from an activity:
        makeRequest.execute(this);

     * }</pre></p>
     * @return a Unix Epoch-format timestamp of the actual current time, in the default timezone.*/
    public long now() throws Exception {
        /*3 possible states:
            1. We have fresh SNTP data from a recently-made request, store (atm) in SntpClient
            2. We have cached SNTP data from SharedPreferences
            3. We have no/invalid SNTP data,
             and won't know the correct time until we make a network request
*/
        TimeData timeData = persistence.getTimeData();//throws NullPointerException
        if(timeData == null) {
            throw new MissingTimeDataException("time data is missing or invalid. " +
                    "Please make an NTP network request to refresh the true time");
        }

        long cachedSntpTime = timeData.getSntpTime();
        long cachedDeviceUptime = timeData.getUptimeAtSntpTime();

        return cachedSntpTime + (SystemClock.elapsedRealtime() - cachedDeviceUptime);
    }

    /**Adds a {@link android.content.BroadcastReceiver} which listens for the user changing the clock,
     * or the device rebooting. In these cases,
     * it repairs the partially-invalidated Time Data using the remaining intact information.*/
    public void registerDataPreserver(Context c) {
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

    public void unregisterDataPreserver(Context c) {
        if(preserver != null) {
            c.unregisterReceiver(preserver);
            preserver = null;
        }
        else {
            Log.w(TAG, "call to unregisterDataPreserver(Context) was unnecessary: " +
                    "there is no TimeDataPreserver currently registered");
        }
    }

    public synchronized InstanceType withConnectionTimeout(int timeoutInMillis) {
        _udpSocketTimeoutInMillis = timeoutInMillis;
        return (InstanceType)this;
    }

    public synchronized InstanceType withRootDelayMax(float rootDelayMax) {
        if (rootDelayMax > _rootDelayMax) {
            String log = String.format(Locale.getDefault(),
                "The recommended max rootDelay value is %f. You are setting it at %f",
                _rootDelayMax, rootDelayMax);
            Log.w(TAG, log);
        }

        _rootDelayMax = rootDelayMax;
        return (InstanceType)this;
    }

    public synchronized InstanceType withRootDispersionMax(float rootDispersionMax) {
        if (rootDispersionMax > _rootDispersionMax) {
            Log.w(TAG, String.format(Locale.getDefault(),
            "The recommended max rootDispersion value is %f. You are setting it at %f",
            _rootDispersionMax, rootDispersionMax));
        }

        _rootDispersionMax = rootDispersionMax;

        return (InstanceType)this;
    }

    public synchronized InstanceType withServerResponseDelayMax(int serverResponseDelayInMillis) {
        _serverResponseDelayMax = serverResponseDelayInMillis;
        return (InstanceType)this;
    }
}