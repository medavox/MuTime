package com.medavox.library.mutime;

import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;


public class Ntp {

    private static final String TAG = Ntp.class.getSimpleName();
    private static int _repeatCount = 4;

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
    public static void performNtpAlgorithm(InetAddress... addresses) {
        Log.i(TAG, "Getting the time from "+addresses.length+" IP addresses: "
                +Arrays.toString(addresses)+"...");
        for (InetAddress address : addresses) {
            String ntpHost = address.getHostAddress();
            StringToTimeDataThread doer = new StringToTimeDataThread(ntpHost, dynamicCollater);
            doer.start();
        }
    }

    private static Comparator<TimeData> clockOffsetSorter = new Comparator<TimeData>() {
        @Override
        public int compare(TimeData lhsParam, TimeData rhsParam) {
            long lhs = lhsParam.getClockOffset();
            long rhs = rhsParam.getClockOffset();
            return lhs < rhs ? -1 : (lhs == rhs ? 0 : 1);
        }
    };

    private static SntpClient.SntpResponseListener dynamicCollater = new SntpClient.SntpResponseListener() {
        private Set<TimeData> timeDatas = new ConcurrentSkipListSet<>(clockOffsetSorter);
        private TimeData oldMedian;

        /**Each time we receive new data, recalculate the median offset
         * and send the results to persistence*/
        @Override
        public void onSntpTimeData(TimeData data) {
            if (data != null) {
                timeDatas.add(data);
                TimeData[] asArray = new TimeData[timeDatas.size()];
                //Returns the NTP response with the median value for clock offset
                TimeData[] sortedResponses = timeDatas.toArray(asArray);
                Arrays.sort(sortedResponses, clockOffsetSorter);
                TimeData newMedian = sortedResponses[sortedResponses.length / 2];
                if(!newMedian.equals(oldMedian)) {
                    oldMedian = newMedian;
                    Log.d(TAG, "new median time:" + newMedian);
                    MuTime.persistence.onSntpTimeData(newMedian);
                }
            }
        }
    };

    private static class StringToTimeDataThread extends Thread {

        private String ntpHost;
        private SntpClient.SntpResponseListener listener;

        StringToTimeDataThread(String ntpHost, SntpClient.SntpResponseListener listener) {
            this.ntpHost = ntpHost;
            this.listener = listener;
        }

        @Override
        public void run() {
            TimeData bestResponse = bestResponseAgainstSingleIp(_repeatCount, ntpHost);
            Log.v(TAG, "got time data \""+bestResponse+"\" from "+ntpHost);
            if(bestResponse != null) {
                listener.onSntpTimeData(bestResponse);
            }
        }

    }

    public static InetAddress[] resolveMultipleNtpHosts(final String... ntpPoolAddresses) {
        final InetAddress[][] allResults = new InetAddress[ntpPoolAddresses.length][];
        ParallelProcess<String, InetAddress[]> wnr = new ParallelProcess<>(ntpPoolAddresses);
        wnr.doWork(new ParallelProcess.Worker<String, InetAddress[]>() {
            @Override public InetAddress[] performWork(String input) {
                return resolveNtpPoolToIpAddresses(input);
            }
        });
        wnr.collectOutputWhenFinished(allResults);
        Set<InetAddress> asSet = new HashSet<>();
        for(InetAddress[] array : allResults) {
            if(array != null) {
                for(InetAddress i : array) {
                    if(i != null) {
                        asSet.add(i);
                    }
                }
            }
        }
        InetAddress[] ret = new InetAddress[asSet.size()];
        return asSet.toArray(ret);
    }

    /**Initialize MuTime
     * A single NTP pool server is provided.
     * Using DNS we resolve that to multiple IP hosts
     *
     * @param ntpPoolAddress NTP pool server e.g. time.apple.com, 0.us.pool.ntp.org
     * @return an array of reachable IP addresses which map to the given ntp pool address
     */
    public static InetAddress[] resolveNtpPoolToIpAddresses(String ntpPoolAddress) {
        try {
            InetAddress[] addresses = InetAddress.getAllByName(ntpPoolAddress);
            if (addresses != null & addresses.length > 0) {
                //remove unreachable addresses
                Set<InetAddress> ips = new HashSet<InetAddress>(addresses.length);
                for (InetAddress a : addresses) {
                    if (isReachable(a)) {
                        ips.add(a);
                    }
                }
                InetAddress[] ret = new InetAddress[ips.size()];
                return ips.toArray(ret);
            } else {
                return addresses;
            }
        } catch (UnknownHostException uhe) {
            Log.e(TAG, "failed to resolve ntp pool \"" + ntpPoolAddress + "\":" + uhe);
        }
        return null;
    }

    /**
     * Takes a single NTP host (as a String),
     * performs an SNTP request on it repeatCount number of times,
     * and returns the single result with the lowest round-trip delay.
     *
     * Returns null if none of the requests to the IP 1) return a successful response,
     * or 2) meet the minimum NTP requirements (root delay, root dispersion, round-trip delay).
     */
    private static TimeData bestResponseAgainstSingleIp(final int repeatCount, String ntpHost) {
        TimeData[] responses = new TimeData[repeatCount];
        ParallelProcess<String, TimeData> para
                = new ParallelProcess<String, TimeData>(ntpHost, repeatCount);
        para.doWork(new ParallelProcess.Worker<String, TimeData>() {
            @Override
            public TimeData performWork(String ntpHost) {
                try {
                    return new SntpRequest(ntpHost, null).send();
                } catch (IOException ioe) {
                    //Log.w(TAG, "request to \"" + ntpHost + "\" failed: " + ioe);
                }
                return null;
            }
        });
        para.collectOutputWhenFinished(responses);

        return filterLeastRoundTripDelay(responses);
    }

    /**
     * Takes a List of NTP responses, and returns the one with the smallest round-trip delay.
     * Returns null if all the passed TimeData objects are null.
     */
    private static TimeData filterLeastRoundTripDelay(TimeData... responseTimeList) {
        long bestRoundTrip = Long.MAX_VALUE;
        int bestIndex = -1;
        for (int i = 0; i < responseTimeList.length; i++) {
            //Log.v(TAG, "response "+(i+1)+" of "+responseTimeList.length+": "+responseTimeList[i]);
            if (responseTimeList[i] != null &&
                    responseTimeList[i].getRoundTripDelay() < bestRoundTrip) {
                bestRoundTrip = responseTimeList[i].getRoundTripDelay();
                bestIndex = i;
            }
        }
        if(bestIndex == -1) {
            return null;
        }
        return responseTimeList[bestIndex];
    }

    private static boolean isReachable(InetAddress addr) {
        try {
            Socket soc = new Socket();
            soc.connect(new InetSocketAddress(addr, 80), 5_000);
            return true;
        } catch (IOException ex) {
            return false;
        }
    }
}
