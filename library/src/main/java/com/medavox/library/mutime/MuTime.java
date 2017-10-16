package com.medavox.library.mutime;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.IOException;
import java.util.Date;
import java.util.Locale;

public class MuTime<InstanceType extends MuTime> {
    protected static Persistence persistence;
    protected static final SntpClient SNTP_CLIENT = new SntpClient();

    private static MuTime INSTANCE;
    private static float _rootDelayMax = 100;
    private static float _rootDispersionMax = 100;
    private static int _serverResponseDelayMax = 200;
    private static int _udpSocketTimeoutInMillis = 30_000;
    private static final String TAG = MuTime.class.getSimpleName();

    @NonNull
    public static MuTime<MuTime> getInstance(Context c) {
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

    public static boolean hasTheTime() {
        return persistence.hasTimeData();
    }

    /**Get the True Time as a {@link Date}
     * @return Date object that returns the current time in the default Timezone
     */
    public Date nowAsDate() throws Exception {
        return new Date(now());
    }

    /**Get the True Time in Unix Epoch format: milliseconds since midnight on 1 January, 1970.*/
    public long now() throws Exception {
        /*3 possible states:
            1. We have fresh SNTP data from a recently-made request, store (atm) in SntpClient
            2. We have cached SNTP data from SharedPreferences
            3. We have no/invalid SNTP data,
             and don't know the correct time until we make a network request
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

    public void addDataRepairer(Context c) {
        IntentFilter phil = new IntentFilter();
        phil.addAction(Intent.ACTION_BOOT_COMPLETED);
        phil.addAction(Intent.ACTION_TIME_CHANGED);
        c.registerReceiver(persistence, phil);
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