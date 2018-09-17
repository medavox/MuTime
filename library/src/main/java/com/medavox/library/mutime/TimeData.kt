package com.medavox.library.mutime

/**
 * A POJO data defining the difference between each of Android's system clocks,
 * and the time on the remote server.
 * Includes the round trip delay time, for gauging accuracy of data.
 *
 * @author Adam Howard
 * @since 14/09/2018
 */
internal data class TimeData(
        val roundTripDelay:Long,
        val systemClockOffset:Long,
        val uptimeOffset:Long
        )