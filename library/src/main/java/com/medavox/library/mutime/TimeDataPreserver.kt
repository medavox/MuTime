package com.medavox.library.mutime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log

/**
 * A {@link BroadcastReceiver} which listens for device reboots and clock changes by the user.
 * Register this class as a broadcast receiver for {@link Intent#ACTION_BOOT_COMPLETED BOOT_COMPLETED}
 * and {@link Intent#ACTION_TIME_CHANGED TIME_CHANGED},
 * to allow MuTime to correct its offsets against these events.
 */
private const val TAG:String  = "TimeDataPreserver"
internal class TimeDataPreserver(private val persistence:Persistence) : BroadcastReceiver() {

    /**Detects when one of the stored time stamps have been invalidated by user actions,
     * and repairs it using the intact timestamp
     *
     * <p>
     *
     * For instance, if the user changes the system clock manually,
     * then the uptime timestamp is used to calculate a new value for the system clock time stamp.
     * */
    override fun onReceive(context:Context, intent:Intent) {
        val old = persistence.getTimeData()
        Log.i(TAG, "action \""+intent.action+"\" detected. Repairing TimeData...")
        val clockNow = System.currentTimeMillis()
        val uptimeNow = SystemClock.elapsedRealtime()
        val trueTime:Long
        when(intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                //uptime can no longer be trusted

                trueTime = clockNow + old.systemClockOffset
                val newUptimeOffset = trueTime -uptimeNow

                val fixedUptime = TimeData(old, uptimeOffset=newUptimeOffset)
                persistence.onSntpTimeData(fixedUptime)
            }

            Intent.ACTION_TIME_CHANGED -> {
                //offset from system clock can no longer be trusted

                trueTime = uptimeNow + old.uptimeOffset
                val newClockOffset = trueTime -clockNow;

                val fixedSystemClockTime = TimeData(old, clockOffset = newClockOffset)
                persistence.onSntpTimeData(fixedSystemClockTime)
            }
        }
    }
}
