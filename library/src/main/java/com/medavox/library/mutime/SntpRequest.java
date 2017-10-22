package com.medavox.library.mutime;

import android.util.Log;

import java.io.IOException;
import java.util.Locale;

/**Describes a network request for the time.
 * Its fluent API allows you to set various SNTP parameters, if you wish.
 * If you don't, its defaults are sensible.
 *
 *<p>To actually make the network request, call {@link #send()}.</p>
 * @author Adam Howard
 * created on 18/10/17
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

    public TimeData send() throws IOException {
        TimeData td = SNTP_CLIENT.requestTime(ntpHost,
                _rootDelayMax,
                _rootDispersionMax,
                _serverResponseDelayMax,
                _udpSocketTimeoutInMillis);
        if(listener != null) {
            listener.onSntpTimeData(td);
        }
        return td;
    }
}
