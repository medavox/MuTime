package com.medavox.library.mutime;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

/**
 * A {@link BroadcastReceiver} which listens for device reboots and clock changes by the user.
 * Register this class as a broadcast receiver for {@link Intent#ACTION_BOOT_COMPLETED BOOT_COMPLETED}
 * and {@link Intent#ACTION_TIME_CHANGED TIME_CHANGED},
 * to allow MuTime to correct its offsets against these events.
 */
public final class TimeDataPreserver extends BroadcastReceiver {
    private Persistence persistence;
    private static final String TAG = BroadcastReceiver.class.getSimpleName();

    public TimeDataPreserver(Persistence p) {
        this.persistence = p;
    }

    /**Detects when one of the stored time stamps have been invalidated by user actions,
     * and repairs it using the intact timestamp
     *
     * <p>
     *
     * For instance, if the user changes the system clock manually,
     * then the uptime timestamp is used to calculate a new value for the system clock time stamp.
     * */
    @Override
    public void onReceive(Context context, Intent intent) {
        TimeData old = persistence.getTimeData();
        Log.i(TAG, "action \""+intent.getAction()+"\" detected. Repairing TimeData...");
        switch(intent.getAction()) {
            case Intent.ACTION_BOOT_COMPLETED:
                //uptime can no longer be trusted

                long newTrustedUptimeAtSntpTime =
                        SystemClock.elapsedRealtime() - (System.currentTimeMillis() - old.getSystemClockAtSntpTime());
                TimeData fixedUptime = new TimeData.Builder(old)
                        .systemClockAtSntpTime(newTrustedUptimeAtSntpTime)
                        .build();
                persistence.onSntpTimeData(fixedUptime);

                break;

            case Intent.ACTION_TIME_CHANGED:
                //system clock can no longer be trusted
                /*so if the uptime was x at a KNOWN true time,
                * and we know the uptime is y nowAsDate,
                * then we know it's been z since the known true time.
                *
                * get the new system clock value as of now (w),
                * then subtract z from it.
                *
                * THAT is the new systemclock time as of the sntp response
                */

                long newTrustedSystemClockAtSntpTime =
                        System.currentTimeMillis() - (SystemClock.elapsedRealtime() - old.getUptimeAtSntpTime());
                TimeData fixedSystemClockTime = new TimeData.Builder(old)
                        .systemClockAtSntpTime(newTrustedSystemClockAtSntpTime)
                        .build();
                persistence.onSntpTimeData(fixedSystemClockTime);
        }
    }
}
