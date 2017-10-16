package com.medavox.library.mutime;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import java.io.IOException;
import java.util.Date;
import java.util.Locale;

public class MuTime<InstanceType extends MuTime> {
    protected static Persistence persistence;
    protected static final SntpClient SNTP_CLIENT = new SntpClient();

    private static final MuTime INSTANCE = new MuTime();
    private static float _rootDelayMax = 100;
    private static float _rootDispersionMax = 100;
    private static int _serverResponseDelayMax = 200;
    private static int _udpSocketTimeoutInMillis = 30_000;
    private static final String TAG = MuTime.class.getSimpleName();

    public static MuTime getInstance() {
        return INSTANCE;
    }

    /**Call this at least once to get reliable time from an NTP server.*/
    public long[] requestTime(String ntpHost) throws IOException {
        return SNTP_CLIENT.requestTime(ntpHost,
                _rootDelayMax,
                _rootDispersionMax,
                _serverResponseDelayMax,
                _udpSocketTimeoutInMillis,
                persistence);
    }

    /**Get the True Time as a {@link Date}
     * @return Date object that returns the current time in the default Timezone
     */
    public static Date now() throws Exception {
        return new Date(nowAsLong());
    }

    /**Get the True Time in Unix Epoch format: milliseconds since midnight on 1 January, 1970.*/
    public static long nowAsLong() throws Exception {
        /*3 possible states:
            1. We have fresh SNTP data from a recently-made request, store (atm) in SntpClient
            2. We have cached SNTP data from SharedPreferences
            3. We have no/invalid SNTP data,
             and don't know the correct time until we make a network request
*/

        TimeData timeData = Persistence.getSntpTimeData();
        if(timeData == null) {
            throw new Exception("time data is missing or invalid. " +
                    "Please make an NTP network request to refresh the true time");
        }


        long cachedSntpTime = timeData.getSntpTime();
        long cachedDeviceUptime = timeData.getUptimeAtSntpTime();

        return cachedSntpTime + (SystemClock.elapsedRealtime() - cachedDeviceUptime);
    }

    /**
     * Cache MuTime initialization information in SharedPreferences
     * This can help avoid additional MuTime initialization on app kills
     */
    public synchronized  InstanceType withDiskCache(Context context) {
        persistence = new Persistence(context);
        return (InstanceType)this;
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