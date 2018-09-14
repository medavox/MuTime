package com.medavox.library.mutime

/**
 * @author Adam Howard
 * @since 14/09/2018
 */
internal class TimeData(
        val roundTripDelay:Long,
        val systemClockOffset:Long,
        val uptimeOffset:Long
        )
{
    constructor(existing:TimeData,
                roundTripDelay:Long=existing.roundTripDelay,
                clockOffset:Long=existing.systemClockOffset,
                uptimeOffset:Long=existing.uptimeOffset)
            : this(roundTripDelay, clockOffset, uptimeOffset)
    override fun toString(): String
    {
        return ("TimeData ["
                + "Round Trip Delay: " + roundTripDelay
                + "; System Clock offset: " + systemClockOffset
                + "; Device Uptime offset: " + uptimeOffset
                + "]")
    }

    override fun equals(other: Any?): Boolean
    {
        if (other == null || other !is TimeData) {
            return false
        } else {
            val td = other as TimeData?
            return td!!.uptimeOffset == uptimeOffset &&
                    td.systemClockOffset == systemClockOffset &&
                    td.roundTripDelay == roundTripDelay
        }
    }
}