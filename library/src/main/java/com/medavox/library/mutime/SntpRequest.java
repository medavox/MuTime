package com.medavox.library.mutime;

import android.util.Log;

import java.io.IOException;
import java.util.Locale;

/**
 * @author Adam Howard
 * @date 18/10/17
 */

public final class SntpRequest {
    static final SntpClient SNTP_CLIENT = new SntpClient();

    private float _rootDelayMax = 100;
    private float _rootDispersionMax = 100;
    private int _serverResponseDelayMax = 200;
    private int _udpSocketTimeoutInMillis = 30_000;
    private String ntpHost;
    private SntpClient.SntpResponseListener listener;

    private static final String TAG = SntpRequest.class.getSimpleName();

    SntpRequest(String ntpHost, SntpClient.SntpResponseListener listener) {
        this.ntpHost = ntpHost;
        this.listener = listener;
    }

    public SntpRequest connectionTimeout(int timeoutInMillis) {
        _udpSocketTimeoutInMillis = timeoutInMillis;
        return this;
    }

    public SntpRequest rootDelayMax(float rootDelayMax) {
        if (rootDelayMax > _rootDelayMax) {
            String log = String.format(Locale.getDefault(),
                    "The recommended max rootDelay value is %f. You are setting it at %f",
                    _rootDelayMax, rootDelayMax);
            Log.w(TAG, log);
        }

        _rootDelayMax = rootDelayMax;
        return this;
    }

    public SntpRequest rootDispersionMax(float rootDispersionMax) {
        if (rootDispersionMax > _rootDispersionMax) {
            Log.w(TAG, String.format(Locale.getDefault(),
                    "The recommended max rootDispersion value is %f. You are setting it at %f",
                    _rootDispersionMax, rootDispersionMax));
        }

        _rootDispersionMax = rootDispersionMax;

        return this;
    }

    public SntpRequest serverResponseDelayMax(int serverResponseDelayInMillis) {
        _serverResponseDelayMax = serverResponseDelayInMillis;
        return this;
    }

    public long[] send() throws IOException {
        return SNTP_CLIENT.requestTime(ntpHost,
                _rootDelayMax,
                _rootDispersionMax,
                _serverResponseDelayMax,
                _udpSocketTimeoutInMillis,
                listener);
    }
}
