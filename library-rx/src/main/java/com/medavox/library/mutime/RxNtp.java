package com.medavox.library.mutime;

import android.util.Log;

import org.reactivestreams.Publisher;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.FlowableEmitter;
import io.reactivex.FlowableOnSubscribe;
import io.reactivex.FlowableTransformer;
import io.reactivex.annotations.NonNull;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.functions.Predicate;
import io.reactivex.schedulers.Schedulers;

public class RxNtp {

    private static RxNtp RX_INSTANCE;
    private static final String TAG = RxNtp.class.getSimpleName();

    private int _retryCount = 50;

    public static RxNtp getInstance() {
        if (RX_INSTANCE == null) {
            RX_INSTANCE = new RxNtp();
        }
        return RX_INSTANCE;
    }

    public RxNtp withRetryCount(int retryCount) {
        _retryCount = retryCount;
        return this;
    }


    /**Initialize MuTime
     * A single NTP pool server is provided.
     * Using DNS we resolve that to multiple IP hosts
     * (See {@link #initializeNtp(InetAddress...)} for manually resolved IPs)
     *
     * Use this instead of {@link #initializeRx(String)} if you wish to also get additional info for
     * instrumentation/tracking actual NTP response data
     *
     * @param ntpPool NTP pool server e.g. time.apple.com, 0.us.pool.ntp.org
     * @return Observable of detailed long[] containing most important parts of the actual NTP response
     * See RESPONSE_INDEX_ prefixes in {@link SntpClient} for details
     */
    public Flowable<TimeData> initializeNtp(String ntpPool) {
        return Flowable
              .just(ntpPool)
              .compose(resolveNtpPoolToIpAddresses)
              .compose(performNtpAlgorithm);
    }

    /**Initialize MuTime
     * Use this if you want to resolve the NTP Pool address to individual IPs yourself
     *
     * See https://github.com/instacart/truetime-android/issues/42
     * to understand why you may want to do something like this.
     *
     * @param resolvedNtpAddresses list of resolved IP addresses for an NTP request
     * @return Observable of detailed long[] containing most important parts of the actual NTP response
     * See RESPONSE_INDEX_ prefixes in {@link SntpClient} for details
     */
    public Flowable<TimeData> initializeNtp(InetAddress... resolvedNtpAddresses) {
        return Flowable.fromArray(resolvedNtpAddresses)
               .compose(performNtpAlgorithm);
    }

    //----------------------------------------------------------------------------------------

    /**Takes in a pool of NTP addresses.
     * Against each IP host we issue a UDP call and retrieve the best response using the NTP algorithm
     */
    private FlowableTransformer<InetAddress, TimeData> performNtpAlgorithm
    = new FlowableTransformer<InetAddress, TimeData>() {
        @Override public Flowable<TimeData> apply(Flowable<InetAddress> inetAddressObservable) {
            return inetAddressObservable
                  .map(new Function<InetAddress, String>() {
                      @Override
                      public String apply(InetAddress inetAddress) {
                          return inetAddress.getHostAddress();
                      }
                  })
                  .flatMap(bestResponseAgainstSingleIp(5))  // get best response from querying the ip 5 times
                  .take(5)                                  // take 5 of the best results
                  .toList()
                  .toFlowable()
                  .filter(new Predicate<List<TimeData>>() {
                      @Override
                      public boolean test(List<TimeData> results) throws Exception {
                          return results.size() > 0;
                      }
                  })
                  .map(filterMedianResponse)
                  .doOnNext(new Consumer<TimeData>() {
                      @Override
                      public void accept(TimeData ntpResponse) {
                          //SNTP_CLIENT.storeTimeOffset(ntpResponse);
                          MuTime.persistence.onSntpTimeData(ntpResponse);
                      }
                  });
        }
    };

    private FlowableTransformer<String, InetAddress> resolveNtpPoolToIpAddresses
    = new FlowableTransformer<String, InetAddress>() {
        @Override
        public Publisher<InetAddress> apply(Flowable<String> ntpPoolFlowable) {
            return ntpPoolFlowable
                  .observeOn(Schedulers.io())
                  .flatMap(new Function<String, Flowable<InetAddress>>() {
                      @Override
                      public Flowable<InetAddress> apply(String ntpPoolAddress) {
                          try {
                              Log.d(TAG, "---- resolving ntpHost : " + ntpPoolAddress);
                              return Flowable.fromArray(InetAddress.getAllByName(ntpPoolAddress));
                          } catch (UnknownHostException e) {
                              return Flowable.error(e);
                          }
                      }
                  })
                .filter(new Predicate<InetAddress>() {
                        @Override
                        public boolean test(InetAddress inetAddress) throws Exception {
                            return isReachable(inetAddress);
                        }
                });
        }
    };

    /**Takes a single NTP host (as a String),
     * performs an SNTP request on it repeatCount number of times,
     * and returns the single result with the lowest round-trip delay*/
    private Function<String, Flowable<TimeData>> bestResponseAgainstSingleIp(final int repeatCount) {
        return new Function<String, Flowable<TimeData>>() {
            @Override
            public Flowable<TimeData> apply(String singleIp) {
                return Flowable
                    .just(singleIp)
                    .repeat(repeatCount)
                    .flatMap(new Function<String, Flowable<TimeData>>() {
                        @Override
                        public Flowable<TimeData> apply(final String singleIpHostAddress) {
                            return Flowable
                                    .create(new FlowableOnSubscribe<TimeData>() {
                                @Override
                                public void subscribe(@NonNull FlowableEmitter<TimeData> o)
                                    throws Exception {

                                    Log.d(TAG,
                                        "---- requestTimeFromServer from: " + singleIpHostAddress);
                                    try {
                                        o.onNext(MuTime.requestTimeFromServer(singleIpHostAddress));
                                        o.onComplete();
                                    } catch (IOException e) {
                                        if (!o.isCancelled()) {
                                            o.onError(e);
                                        }
                                    }
                                }
                              }, BackpressureStrategy.BUFFER)
                                  .subscribeOn(Schedulers.io())
                                  .doOnError(new Consumer<Throwable>() {
                                      @Override
                                      public void accept(Throwable throwable) {
                                          Log.e(TAG, "---- Error requesting time", throwable);
                                      }
                                  })
                                  .retry(_retryCount);
                        }
                    })
                    .toList()
                    .toFlowable()
                    .map(filterLeastRoundTripDelay); // pick best response for each ip
            }
        };
    }

    /**Takes a List of NTP responses, and returns the one with the smallest round-trip delay*/
    private Function<List<TimeData>, TimeData> filterLeastRoundTripDelay
    = new Function<List<TimeData>, TimeData>() {
        @Override
        public TimeData apply(List<TimeData> responseTimeList) {
            Collections.sort(responseTimeList, new Comparator<TimeData>() {
                @Override
                public int compare(TimeData lhsParam, TimeData rhsLongParam) {
                    long lhs = lhsParam.getRoundTripDelay();
                    long rhs = rhsLongParam.getRoundTripDelay();
                    return lhs < rhs ? -1 : (lhs == rhs ? 0 : 1);
                }
            });

            Log.d(TAG, "---- filterLeastRoundTrip: " + responseTimeList);

            return responseTimeList.get(0);
        }
    };

    /**Takes a list of NTP responses, and returns the one with the median value for clock offset*/
    private Function<List<TimeData>, TimeData> filterMedianResponse
    = new Function<List<TimeData>, TimeData>() {
        @Override
        public TimeData apply(List<TimeData> bestResponses) {
            Collections.sort(bestResponses, new Comparator<TimeData>() {
                @Override
                public int compare(TimeData lhsParam, TimeData rhsParam) {
                    long lhs = lhsParam.getClockOffset();
                    long rhs = rhsParam.getClockOffset();
                    return lhs < rhs ? -1 : (lhs == rhs ? 0 : 1);
                }
            });
            TimeData result = bestResponses.get(bestResponses.size() / 2);
            Log.d(TAG, "---- bestResponse: " + result);

            return result;
        }
    };

    private boolean isReachable(InetAddress addr) {
        try {
            Socket soc = new Socket();
            soc.connect(new InetSocketAddress(addr, 80), 5_000);
            return true;
        } catch (IOException ex) {
            return false;
        }
    }
}
