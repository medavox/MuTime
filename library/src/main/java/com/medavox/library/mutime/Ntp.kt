package com.medavox.library.mutime

import android.util.Log

import java.io.IOException

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Arrays


private const val TAG:String  = "NTP"
private const val REPEAT_COUNT = 4

/**
 * Initialize MuTime
 * Use this if you want to resolve the NTP Pool address to individual IPs yourself
 * <p>
 * See https://github.com/instacart/truetime-android/issues/42
 * to understand why you may want to do something like this.
 *
 * @return Observable of detailed long[] containing most important parts of the actual NTP response
 * See RESPONSE_INDEX_ prefixes in {@link SntpClient} for details
 */
fun performNtpAlgorithm(vararg addresses:String) {
    Log.i(TAG, "Getting the time from ${addresses.size} IP addresses: ${Arrays.toString(addresses)}...")

    val para:ParallelProcess<String, Array<InetAddress>?> = ParallelProcess{
        InetAddress.getAllByName(it)?.filterNotNull()?.filter{it.isReachable()}?.toTypedArray()
    }
    para.oneWorkerPerElement(addresses)
    val results:List<Array<InetAddress>?> = para.collectOutputWhenFinished()
    val inets = mutableSetOf<InetAddress>()
    results.filterNotNull().forEach { it.forEach { inets.add(it) } }

    for (ntpHost in inets) {
        Thread(Runnable{
            val bestResponse:TimeData? = bestResponseAgainstSingleIp(REPEAT_COUNT, ntpHost.hostAddress)
            Log.v(TAG, "got time data \"$bestResponse\" from ${ntpHost.hostAddress}")
            if(bestResponse != null) {
                dynamicCollater.onSntpTimeData(bestResponse)
            }
        }).start()
    }
}

private val dynamicCollater = object : SntpClient.SntpResponseListener {
    private val timeDatas:MutableSet<TimeData> = mutableSetOf()
    private var oldMedian:TimeData? = null

    /**Each time we receive new data,
     * find the TimeData which now has the median clock offset value,
     * and, if it's changed, send it to persistence.*/
    override fun onSntpTimeData(data:TimeData) {
        timeDatas.add(data)
        val newMedian = timeDatas.sortedBy{it.systemClockOffset}[timeDatas.size/2]
        if(newMedian != oldMedian) {
            oldMedian = newMedian
            Log.d(TAG, "new median time: $newMedian")
            MuTime.persistence.onSntpTimeData(newMedian)
        }
    }
}

/**
 * Takes a single NTP host (as a String),
 * performs an SNTP request on it repeatCount number of times,
 * and returns the single result with the lowest round-trip delay.
 *
 * Returns null if none of the requests to the IP 1) return a successful response,
 * or 2) meet the minimum NTP requirements (root delay, root dispersion, round-trip delay).
 */
private fun bestResponseAgainstSingleIp(repeatCount:Int, ntpHost:String):TimeData?  {
    val para = ParallelProcess<String, TimeData>{SntpRequest(it, null).send()}
    para.repeatOnInput(repeatCount, ntpHost)
    val results:List<TimeData> = para.collectOutputWhenFinished()
    return if(results.isEmpty()) null else filterLeastRoundTripDelay(results)
}

/**
 * Takes a List of NTP responses, and returns the one with the smallest round-trip delay.
 * Returns null if all the passed TimeDatas are null.
 */
private fun filterLeastRoundTripDelay(responseTimeList:List<TimeData>):TimeData?  {
    var bestRoundTrip = Long.MAX_VALUE
    var bestTimeData:TimeData? = null
    responseTimeList.forEach {
        if(it.roundTripDelay < bestRoundTrip) {
            bestRoundTrip = it.roundTripDelay
            bestTimeData = it
        }
    }

    return bestTimeData
}

/**Custom implementation of isReachable which checks reachability over port 80*/
private fun InetAddress.isReachable():Boolean {
    return try {
        val soc = Socket()
        soc.connect(InetSocketAddress(this, 80), 5_000)
        true
    } catch (ex: IOException) {
        false
    }
}
