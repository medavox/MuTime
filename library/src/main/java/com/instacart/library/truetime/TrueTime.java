package com.instacart.library.truetime;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import java.io.IOException;
import java.util.Date;
import java.util.Locale;

public class TrueTime<InstanceType extends TrueTime> {

    private static final String TAG = TrueTime.class.getSimpleName();

    private static final TrueTime INSTANCE = new TrueTime();
    private static final DiskCacheClient DISK_CACHE_CLIENT = new DiskCacheClient();
    private static final SntpClient SNTP_CLIENT = new SntpClient();

    private static float _rootDelayMax = 100;
    private static float _rootDispersionMax = 100;
    private static int _serverResponseDelayMax = 200;
    private static int _udpSocketTimeoutInMillis = 30_000;

    private String _ntpHost = "1.us.pool.ntp.org";



    public static TrueTime getInstance() {
        return INSTANCE;
    }

    public void initialize() throws IOException {
        initialize(_ntpHost);
        saveTrueTimeInfoToDisk();
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
            throw new IllegalStateException("You need to call init() on TrueTime at least once.");
        }

        long cachedSntpTime = _getCachedSntpTime();
        long cachedDeviceUptime = _getCachedDeviceUptime();
        long deviceUptime = SystemClock.elapsedRealtime();

        return cachedSntpTime + (deviceUptime - cachedDeviceUptime);
    }

    public static boolean isInitialized() {
        return SNTP_CLIENT.wasInitialized() || DISK_CACHE_CLIENT.isTrueTimeCachedFromAPreviousBoot();
    }



    public static void clearCachedInfo(Context context) {
        DISK_CACHE_CLIENT.clearCachedInfo(context);
    }



    /**
     * Cache TrueTime initialization information in SharedPreferences
     * This can help avoid additional TrueTime initialization on app kills
     */
    public synchronized  InstanceType withSharedPreferences(Context context) {
        DISK_CACHE_CLIENT.enableDiskCaching(context);
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

    public synchronized InstanceType withNtpHost(String ntpHost) {
        _ntpHost = ntpHost;
        return (InstanceType)this;
    }
/*
    public synchronized TrueTime withLoggingEnabled(boolean isLoggingEnabled) {
        TrueLog.setLoggingEnabled(isLoggingEnabled);
        return INSTANCE;
    }
*/
    // -----------------------------------------------------------------------------------

    protected void initialize(String ntpHost) throws IOException {
        if (isInitialized()) {
            Log.w(TAG, "TrueTime already initialized from previous boot/init");
            return;
        }

        requestTime(ntpHost);
    }

    long[] requestTime(String ntpHost) throws IOException {
        return SNTP_CLIENT.requestTime(ntpHost,
            _rootDelayMax,
            _rootDispersionMax,
            _serverResponseDelayMax,
            _udpSocketTimeoutInMillis);
    }

    synchronized static void saveTrueTimeInfoToDisk() {
        if (!SNTP_CLIENT.wasInitialized()) {
            Log.w(TAG, "SNTP client not available. not caching TrueTime info in disk");
            return;
        }
        DISK_CACHE_CLIENT.cacheTrueTimeInfo(SNTP_CLIENT);
    }

    void cacheTrueTimeInfo(long[] response) {
        SNTP_CLIENT.cacheTrueTimeInfo(response);
    }

    private static long _getCachedDeviceUptime() {
        long cachedDeviceUptime = SNTP_CLIENT.wasInitialized()
                                  ? SNTP_CLIENT.getCachedDeviceUptime()
                                  : DISK_CACHE_CLIENT.getCachedDeviceUptime();

        if (cachedDeviceUptime == 0L) {
            throw new RuntimeException("expected device time from last boot to be cached. couldn't find it.");
        }

        return cachedDeviceUptime;
    }

    private static long _getCachedSntpTime() {
        long cachedSntpTime = SNTP_CLIENT.wasInitialized()
                              ? SNTP_CLIENT.getCachedSntpTime()
                              : DISK_CACHE_CLIENT.getCachedSntpTime();

        if (cachedSntpTime == 0L) {
            throw new RuntimeException("expected SNTP time from last boot to be cached. couldn't find it.");
        }

        return cachedSntpTime;
    }

}