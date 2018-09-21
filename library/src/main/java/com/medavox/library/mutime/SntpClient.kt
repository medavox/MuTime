/*
 * Original work Copyright (C) 2008 The Android Open Source Project
 * Modified work Copyright (C) 2016 Instacart
 * Further Modified work Copyright (c) 2017, 2018 eLucid mHealth Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.medavox.library.mutime

import android.os.SystemClock
import android.util.Log

import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

import kotlin.experimental.and

private const val TAG = "SntpClient"

private const val INDEX_VERSION = 0
private const val INDEX_ROOT_DELAY = 4
private const val INDEX_ROOT_DISPERSION = 8
private const val INDEX_ORIGINATE_TIME = 24
private const val INDEX_RECEIVE_TIME = 32
private const val INDEX_TRANSMIT_TIME = 40


const val NTP_PORT = 123
const val NTP_MODE = 3
const val NTP_VERSION = 3
const val NTP_PACKET_SIZE = 48

// 70 years plus 17 leap days
private const val OFFSET_1900_TO_1970 = ((365L * 70L) + 17L) * 24L * 60L * 60L

/**
 * Simple Network Time Protocol client for retrieving time from the network.
 * The Simple in SNTP means that it only queries one server for the time once.
 * This server may be wrong, or there may be an anomalous delay to the response.
 * This is why it is better to use the full NTP, which queries multiple servers multiple times.
 */
internal object SntpClient {

    /**
     * Sends an NTP request to the given host and processes the response.
     *
     * @param ntpHost host name of the server.
     * @param timeout network timeout in milliseconds.
     */
    @Synchronized
    @Throws(IOException::class)
    fun requestTime(ntpHost:String,
                               rootDelayMax:Float=100.toFloat(),
                               rootDispersionMax:Float=100.toFloat(),
                               serverResponseDelayMax:Int=200,
                               timeout:Int=30_000):TimeData {
        //Log.d(TAG, "requesting the time from "+ntpHost+"...")
        var socket:DatagramSocket? = null

        try {
            val address:InetAddress = InetAddress.getByName(ntpHost)
            val buffer = ByteArray(NTP_PACKET_SIZE)
            val request = DatagramPacket(buffer, buffer.size, address, NTP_PORT)

            // set mode and version
            // mode is in low 3 bits of first byte
            // version is in bits 3-5 of first byte
            buffer[INDEX_VERSION] = (NTP_MODE or (NTP_VERSION shl 3)).toByte()

            //initialise socket
            socket = DatagramSocket()
            socket.soTimeout = timeout

            // get current time and write it to the request packet
            val clockAtRequest = System.currentTimeMillis()
            val uptimeAtRequest = SystemClock.elapsedRealtime()
            writeTimeStamp(buffer, INDEX_TRANSMIT_TIME, clockAtRequest)

            socket.send(request)

            // read the response
            val response = DatagramPacket(buffer, buffer.size)

            socket.receive(response)
            val uptimeAtResponse = SystemClock.elapsedRealtime()//=clockAtSntpTime
            val clockAtResponse = System.currentTimeMillis()//=uptimeOffset

            // extract the results
            // See here for the algorithm used:
            // https://en.wikipedia.org/wiki/Network_Time_Protocol#Clock_synchronization_algorithm

            //long originateTime = readTimeStamp(buffer, INDEX_ORIGINATE_TIME)  // T0
            val receiveTime = readTimeStamp(buffer, INDEX_RECEIVE_TIME)         // T1
            val transmitTime = readTimeStamp(buffer, INDEX_TRANSMIT_TIME)       // T2

            //long responseTime = clockAtRequest + (uptimeAtResponse - uptimeAtRequest);//T3
            //long differenceBetweenCalculatedAndClock = responseTime - clockAtResponse
            //Log.v(TAG, "difference between calculated responseTime and equivalent System Clock value: "+
            //differenceBetweenCalculatedAndClock)

            // -----------------------------------------------------------------------------------
            // check validity of response

            val rootDelay:Double = doubleMillis(read(buffer, INDEX_ROOT_DELAY))
            if (rootDelay > rootDelayMax) {
                throw InvalidNtpResponseException("Invalid response from NTP server." +
                        "root_delay violation. $rootDelay [actual] > $rootDelayMax [expected]")
            }

            val rootDispersion:Double = doubleMillis(read(buffer, INDEX_ROOT_DISPERSION))
            if (rootDispersion > rootDispersionMax) {
                throw  InvalidNtpResponseException(
                        "Invalid response from NTP server. root_dispersion violation." +
                        " $rootDispersion [actual] > $rootDispersionMax [expected]")
            }

            val mode = (buffer[0] and 0x7).toInt()
            if (mode != 4 && mode != 5) {
                throw InvalidNtpResponseException("untrusted mode value for MuTime: $mode")
            }

            val stratum = buffer[1] and 0xff.toByte()
            if (stratum < 1 || stratum > 15) {
                throw InvalidNtpResponseException("untrusted stratum value for MuTime: $stratum")
            }

            val leap = (buffer[0].toInt() shr 6) and 0x3
            if (leap == 3) {
                throw InvalidNtpResponseException("unsynchronized server responded for MuTime")
            }

            val roundTripDelay = (clockAtResponse - clockAtRequest) - (transmitTime - receiveTime)
            val delay = Math.abs(roundTripDelay)
            if (delay >= serverResponseDelayMax) {
                throw InvalidNtpResponseException("server_response_delay too large for comfort; " +
                            "$delay [actual] >= $serverResponseDelayMax [max]")
            }

            val timeElapsedSinceRequest = Math.abs(clockAtRequest - System.currentTimeMillis())
            if (timeElapsedSinceRequest >= 10_000) {
                throw InvalidNtpResponseException("Request was sent more than 10 seconds ago: " +
                        "${timeElapsedSinceRequest}ms")
            }

            // -----------------------------------------------------------------------------------

            //response data is valid, send time info from response
            //TimeData td = new TimeData.Builder().roundTripDelay()
            val clockOffset = ((receiveTime - clockAtRequest) + (transmitTime - clockAtResponse)) / 2
            val uptimeOffset = ((receiveTime - uptimeAtRequest) + (transmitTime - uptimeAtResponse)) / 2

            return TimeData(
                    systemClockOffset=clockOffset,
                    uptimeOffset=uptimeOffset,
                    roundTripDelay=roundTripDelay)
            //Log.i(TAG, "---- SNTP successful response from " + ntpHost)
        } catch (e:Exception) {
            Log.e(TAG, "SNTP request failed for $ntpHost: $e")
            //e.printStackTrace()
            throw e
        } finally {
            socket?.close()
        }
    }

    //--------------------------------------------------------------------------------------------

    /**
     * Reads an unsigned 32 bit big endian number
     * from the given offset in the buffer
     *
     * @return 4 bytes as a 32-bit long (unsigned big endian)
     */
    private fun read(buffer:ByteArray, offset:Int):Long {
        val b0 = buffer[offset]
        val b1 = buffer[offset + 1]
        val b2 = buffer[offset + 2]
        val b3 = buffer[offset + 3]

        return (ui(b0).toLong() shl 24) +
               (ui(b1).toLong() shl 16) +
               (ui(b2).toLong() shl 8) +
               ui(b3).toLong()
    }

    /**
     * Writes system time (milliseconds since January 1, 1970) as an NTP time stamp
     * as defined in RFC-1305
     * at the given offset in the buffer.
     */
    private fun writeTimeStamp(buffer:ByteArray, startingOffset:Int, time:Long) {
        // consider offset for number of seconds
        // between Jan 1, 1900 (NTP epoch) and Jan 1, 1970 (Java epoch)
        val seconds:Long = OFFSET_1900_TO_1970 + (time / 1000L)
        val milliseconds:Long = time - seconds * 1000L

        var offset = startingOffset

        // write seconds in big endian format
        buffer[offset++] = (seconds shr 24).toByte()
        buffer[offset++] = (seconds shr 16).toByte()
        buffer[offset++] = (seconds shr 8).toByte()
        buffer[offset++] = (seconds shr 0).toByte()

        val fraction = milliseconds * 0x100000000L / 1000L

        // write fraction in big endian format
        buffer[offset++] = (fraction shr 24).toByte()
        buffer[offset++] = (fraction shr 16).toByte()
        buffer[offset++] = (fraction shr 8).toByte()

        // low order bits should be random data
        buffer[offset++] = (Math.random() * 255.0).toByte()
    }

    /**
     * @param offset offset index in buffer to start reading from
     * @return NTP timestamp in Java epoch
     */
    private fun readTimeStamp(buffer:ByteArray, offset:Int):Long {
        val seconds = read(buffer, offset)
        val fraction = read(buffer, offset + 4)

        return ((seconds - OFFSET_1900_TO_1970) * 1000) + ((fraction * 1000L) / 0x100000000L)
    }

    /***
     * Convert (signed) byte to an unsigned int
     *
     * Java only has signed types so we have to do
     * more work to get unsigned ops
     *
     * @param b input byte
     * @return unsigned int value of byte
     */
    private fun ui(b:Byte):Int {
        return b.toInt() and 0xFF
    }

    /**
     * Used for root delay and dispersion
     *
     * According to the NTP spec, they are in the NTP Short format
     * viz. signed 16.16 fixed point
     *
     * @param fix signed fixed point number
     * @return as a double in milliseconds
     */
    //todo: replace floating-point arithmetic
    private fun doubleMillis(fix:Long):Double {
        return fix / 65.536
    }

    internal interface SntpResponseListener {
        fun onSntpTimeData(data: TimeData)
    }
}
