package com.medavox.library.mutime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.medavox.library.mutime.DiskCache.Companion.SHARED_PREFS_KEY

/**
 * A {@link BroadcastReceiver} which listens for device reboots and clock changes by the user.
 * Register this class as a broadcast receiver for {@link Intent#ACTION_BOOT_COMPLETED BOOT_COMPLETED}
 * and {@link Intent#ACTION_TIME_CHANGED TIME_CHANGED},
 * to allow MuTime to correct its offsets against these events.
 */

class RebootWatcher : BroadcastReceiver() {
    private val TAG:String  = "TimeDataPreserver"
    /**Detects when one of the stored time stamps have been invalidated by user actions,
     * and repairs it using the intact timestamp
     *
     * <p>
     *
     * For instance, if the user changes the system clock manually,
     * then the uptime timestamp is used to calculate a new value for the system clock time stamp.
     * */
    override fun onReceive(context:Context, intent:Intent) {
        val diskCache = DiskCache(context.getSharedPreferences(SHARED_PREFS_KEY, Context.MODE_PRIVATE))
        val old = diskCache.getTimeData()
        Log.i(TAG, "action \""+intent.action+"\" detected. Repairing TimeData...")
        val clockNow = System.currentTimeMillis()
        val uptimeNow = SystemClock.elapsedRealtime()
        val trueTime:Long
        when(intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                //uptime clock can no longer be trusted

                trueTime = clockNow + old.systemClockOffset
                val newUptimeOffset = trueTime -uptimeNow

                val fixedUptime = old.copy(uptimeOffset=newUptimeOffset)
                diskCache.onSntpTimeData(fixedUptime)
            }

            Intent.ACTION_TIME_CHANGED -> {
                //system clock can no longer be trusted

                trueTime = uptimeNow + old.uptimeOffset
                val newClockOffset = trueTime -clockNow

                val fixedSystemClockTime = old.copy(systemClockOffset = newClockOffset)
                diskCache.onSntpTimeData(fixedSystemClockTime)
            }
        }
    }
}
