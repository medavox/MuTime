package com.medavox.library.mutime

import java.io.IOException


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

/**Thrown when a call to `MuTime.now()` is made when MuTime doesn't have the actual time.
 *
 * @author Adam Howard
 * @since 16/10/17
 */
data class MissingTimeDataException(override val message: String) : Exception(message)

/**Thrown when an NTP server sends back an invalid response.
 * @param detailMessage An informative message that api users can use to know what went wrong.
 *                should contain {@link #property}, {@link #expectedValue} and
 *                {@link #actualValue} as format specifiers (in that order)
 * @param propertyName  property that caused the invalid NTP response
 *
 */
data class InvalidNtpResponseException(val detailMessage:String,
                                  val propertyName:String="n/a",
                                  val expectedValue:Float=0F,
                                  val actualValue:Float=0F) : IOException(detailMessage)