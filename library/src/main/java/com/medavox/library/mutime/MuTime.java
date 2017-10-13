package com.medavox.library.mutime;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import java.io.IOException;
import java.util.Date;
import java.util.Locale;

public class MuTime<InstanceType extends MuTime> {

    private static final String TAG = MuTime.class.getSimpleName();

    private static final MuTime INSTANCE = new MuTime();
    private static DiskCacheClient diskCacheClient;
    private static final SntpClient SNTP_CLIENT = new SntpClient();

    private static float _rootDelayMax = 100;
    private static float _rootDispersionMax = 100;
    private static int _serverResponseDelayMax = 200;
    private static int _udpSocketTimeoutInMillis = 30_000;

    private String _ntpHost = "1.us.pool.ntp.org";

    public static MuTime getInstance() {
        return INSTANCE;
    }

    public void initialize() throws IOException {
        initialize(_ntpHost);
        saveTrueTimeInfoToDisk();
    }

    protected void initialize(String ntpHost) throws IOException {
        if (isInitialized()) {
            Log.w(TAG, "MuTime already initialized from previous boot/init");
            return;
        }

        requestTime(ntpHost);
    }

    public static boolean isInitialized() {
        return SNTP_CLIENT.wasInitialized() || diskCacheClient.isTrueTimeCachedFromAPreviousBoot();
    }

    /**Get the True Time as a {@link Date}
     * @return Date object that returns the current time in the default Timezone
     */
    public static Date now() {
        return new Date(nowLong());
    }

    /**Get the True Time in Unix Epoch format, milliseconds since 0:00 on 1 January, 1970.*/
    public static long nowLong() {
        if (!isInitialized()) {
            throw new IllegalStateException("You need to call init() on MuTime at least once.");
        }

        long cachedSntpTime = _getCachedSntpTime();
        long cachedDeviceUptime = _getCachedDeviceUptime();
        long deviceUptime = SystemClock.elapsedRealtime();

        return cachedSntpTime + (deviceUptime - cachedDeviceUptime);
    }

    public static void clearCachedInfo() {
        diskCacheClient.clearCachedInfo();
    }

    /**
     * Cache MuTime initialization information in SharedPreferences
     * This can help avoid additional MuTime initialization on app kills
     */
    public synchronized  InstanceType usingCache(Context context) {
        diskCacheClient = new DiskCacheClient(context);
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

    /**Specify the NTP host to query.
     * @param ntpHost an ntp host address, in the format time.example.com*/
    public synchronized InstanceType withNtpHost(String ntpHost) {
        _ntpHost = ntpHost;
        return (InstanceType)this;
    }

    // -----------------------------------------------------------------------------------

    long[] requestTime(String ntpHost) throws IOException {
        return SNTP_CLIENT.requestTime(ntpHost,
            _rootDelayMax,
            _rootDispersionMax,
            _serverResponseDelayMax,
            _udpSocketTimeoutInMillis);
    }

    synchronized static void saveTrueTimeInfoToDisk() {
        if (!SNTP_CLIENT.wasInitialized()) {
            Log.w(TAG, "SNTP client not available; Not caching MuTime info in disk");
            return;
        }
        diskCacheClient.cacheTrueTimeInfo(SNTP_CLIENT);
    }

    void cacheTrueTimeInfo(long[] response) {
        SNTP_CLIENT.cacheTrueTimeInfo(response);
    }

    private static long _getCachedDeviceUptime() {
        long cachedDeviceUptime = SNTP_CLIENT.wasInitialized()
                                  ? SNTP_CLIENT.getCachedDeviceUptime()
                                  : diskCacheClient.getCachedDeviceUptime();

        if (cachedDeviceUptime == 0L) {
            throw new RuntimeException("Couldn't find cached device time from last boot");
        }

        return cachedDeviceUptime;
    }

    private static long _getCachedSntpTime() {
        long cachedSntpTime = SNTP_CLIENT.wasInitialized()
                              ? SNTP_CLIENT.getCachedSntpTime()
                              : diskCacheClient.getCachedSntpTime();

        if (cachedSntpTime == 0L) {
            throw new RuntimeException("Couldn't find cached SNTP time from last boot");
        }

        return cachedSntpTime;
    }

}