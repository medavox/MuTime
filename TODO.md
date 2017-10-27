TODO
====



DONE
====

* Add a timestampsDifference:long to TimeData,
which stores the difference (in ms) between the uptime and systemClock values as of the SNTP request.
    - Whenever we access the TimeData, we calculate the difference between the live versions of those clocks.
        - If these two differences are more than (say about) 5ms apart,
        - then our stored TimeData has been invalidated somehow without us detecting it, 
        - and is invalid -- we need to make a fresh SNTP request.
* Revamp the full NTP-implementing MutimeRx
    - to increase the speed of resolving the network time from multiple addresses,
    have it so that a 'rolling average' is calculated anew each time more data comes in,
    rather than waiting for all requests to complete (respond or timeout), as seems to be the case currently.
    - provide an API method for requesting the time from multiple independent hosts --
     eg time.google.com AND time.apple.com -- and combining the results with NTP
* Continue to make source code more Java-idiomatic
    - completely replace the long[] variable which is passed around with TimeData
        * this will involve adding more fields to TimeData, such as RoundTripDelay.
