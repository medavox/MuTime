# MuTime for Android

![MuTime](mutime.png "MuTime for Android")

NTP client for Android. Calculate the date and time "now" impervious to manual changes to device clock time.

In certain applications it becomes important to get the real or "true" date and time.
On most devices, if the clock has been changed manually,
then a `new Date()` instance gives you a time impacted by local settings.

Users may do this for a variety of reasons, like being in different timezones, 
trying to be punctual by setting their clocks 5 – 10 minutes early, etc.
Your application or service may want a date that is unaffected by these changes and reliable as a source of truth. MuTime gives you that.

Originally a fork, now a major rewrite of [Instacart's](https://tech.instacart.com/) [TrueTime](https://github.com/instacart/truetime-android) library.

You can read more about the use case in Instacart's [blog post](https://tech.instacart.com/truetime/).

# Installation

We use [Jitpack](https://jitpack.io) to host the library.

Add this to your application's `build.gradle` file:

```groovy
repositories {
    maven {
        url "https://jitpack.io"
    }
}

dependencies {
    compile 'com.github.medavox:MuTime:<release-version>'
}
```

The current release version is `v0.5`.

# Usage

## Simple (SNTP) version

```java
//optionally enable the disk cache
MuTime.enableDiskCaching(/*Context*/ this);//this hardens MuTime against clock changes and reboots

MuTime.requestTimeFromServer("time.google.com");//use any ntp server address here, eg "time.apple.com"

//get the real time in unix epoch format (milliseconds since midnight on 1 january 1970)
try {
    long theActualTime = MuTime.now();//throws MissingTimeDataException if we don't know the time
}
catch (MissingTimeDataException e) {
    Log.e("MuTime", "failed to get the actual time:+e.getMessage());
}
```

`requestTimeFromServer(String)` must be run on a background thread. 
If you run it on the main (UI) thread, you will get a
[`NetworkOnMainThreadException`](https://developer.android.com/reference/android/os/NetworkOnMainThreadException.html)


## Complex (NTP) Version

If you use the [Ntp](https://github.com/medavox/MuTime/blob/master/library/src/main/java/com/medavox/library/mutime/Ntp.java) class then you get full NTP.

Use the `performNtpAlgorithm(InetAddress...)` method, or alternatively resolve an [NTP pool](https://en.wikipedia.org/wiki/NTP_pool) server to `Inetaddress`es automatically with `resolveNtpPoolToIpAddresses(String)`. You can even resolve multiple NTP servers with `resolveMultipleNtpHosts(String...)`.

```java

//gather NTP data from multiple hosts
Ntp.performNtpAlgorithm(Ntp.resolveMultipleNtpHosts("pool.ntp.org", "time.google.com", "time.apple.com") );

```

Now, as before:

```java
try {
    long theActualTime = mu.now();//throws MissingTimeDataException if we don't know the time
}
catch (MissingTimeDataException e) {
    Log.e("MuTime", "failed to get the actual time:+e.getMessage());
}
```

# How is the true time calculated?

It's pretty simple actually. We make a request to an NTP server that gives us the actual time.
We then establish the delta between device uptime and uptime at the time of the network response.
On each subsequent request for the true time "now", we compute the correct time from that offset.

Once we have this offset information, it's valid until the next time you boot your device.
This means if you enable the disk caching feature, after a single successful NTP request you can
use the information on-disk directly without ever making another network request.
This applies even across application kills -- which can happen frequently if a user has a memory starved device.

## Why Use The Ntp Class Instead of MuTime?

* Implements the full NTP, as opposed to the more basic SNTP (read: far more accurate time)
* The NTP pool address you provide is resolved into multiple IP addresses
* We query each IP multiple times, guarding against checks, and take the best response
* We collect all the responses and again filter for the best result as per the NTP spec
* Allows you to query multiple, independent NTP server, eg time.google.com and time.apple.com

# Reason For Fork

[I](https://github.com/medavox/) needed a way of providing reliable time for another app,
preserving the correct time across 1) android clock adjustments by the user and 2) device reboots.
Although the NTP client implementation in [TrueTime](https://github.com/instacart/truetime-android)'s
library is more sophisticated than Google's hidden Android 
[SntpClient](http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/4.1.1_r1/android/net/SntpClient.java/),
I needed even more reliable time-keeping for my use case. It was also apparent (at the time of forking)
that [instacart](https://github.com/instacart)/[Kaushik Gopal](https://github.com/kaushikgopal)'s
plans for future development did not fit with my own needs 
(judging from [development branches](https://github.com/instacart/truetime-android/tree/kg/fix/sync_to_atomic)).

## Differences From Upstream

MuTime implements a more 'stubborn' 
[`Persistence`](https://github.com/medavox/MuTime/blob/master/library/src/main/java/com/medavox/library/mutime/Persistence.java) 
solution, which preserves information about the correct time even after clock changes and device reboots.
It's a beefed-up version of TrueTime's Disk Cache functionality.

The public API has been revampe, and the underlying codebase largely rewritten,
to improve maintainability (in my humble opinion).


# Notes/tips:

* Each `requestTimeFromServer(String)` call makes an SNTP network request.
MuTime needs to do this only once -- barring any , if you use MuTime's `withSharedPreferences`
* Preferably use dependency injection (like [Dagger](http://square.github.io/dagger/)) and create a MuTime @Singleton object
* You can read up on Wikipedia the differences between [SNTP](https://en.wikipedia.org/wiki/Network_Time_Protocol#SNTP) and [NTP](https://www.meinbergglobal.com/english/faq/faq_37.htm).

## Troubleshooting/Exception handling:

When you execute the MuTime initialization, you are very likely to get an `InvalidNtpServerResponseException` because of root delay violation or root dispersion violation the first time.
This is an expected occurrence as per the [NTP Spec](https://tools.ietf.org/html/rfc5905) and needs to be handled.

### Why does this happen?

The NTP protocol works on [UDP](https://en.wikipedia.org/wiki/User_Datagram_Protocol): 

> It has no handshaking dialogues, and thus exposes the user's program to any unreliability of the underlying network and so there is no guarantee of delivery, ordering, or duplicate protection
>
> UDP is suitable for purposes where error checking and correction is either not necessary or is *performed in the application*, avoiding the overhead of such processing at the network interface level. Time-sensitive applications often use UDP because dropping packets is preferable to waiting for delayed packets, which may not be an option in a real-time system

([Wikipedia's page](https://en.wikipedia.org/wiki/User_Datagram_Protocol), emphasis our own)

This means it is highly plausible that we get faulty data packets. These are caught by the library and surfaced to the API consumer as an `InvalidNtpServerResponseException`. See this [portion of the code](https://github.com/medavox/truetime-android/blob/master/library/src/main/java/com/instacart/library/truetime/SntpClient.java#L141) for the various checks that we guard against.

These guards are *extremely* important to guarantee accurate time and cannot be avoided.

If MuTime fails to initialise (because of the above exception being thrown), then a 
[`MissingTimeDataException`](https://github.com/medavox/MuTime/blob/master/library/src/main/java/com/medavox/library/mutime/MissingTimeDataException.java)
 is thrown if you try to request an actual date via `MuTime.now()`.

### How do I handle or protect against this in my application?

It's pretty simple:

* Keep retrying the request, until you get a successful one. Yes it does happen eventually :)
* Try picking a better NTP pool server. In our experience `time.apple.com` has worked best

Or if you want the library to just handle that, use the Rx-ified version of the library (note the -rx suffix):

```
    compile 'com.github.instacart.truetime-android:library-extension-rx:<release-version>'
```

With MuTimeRx, we go the whole nine yards and implement the complete NTP Spec.

We:-

* resolve the DNS for the provided NTP host to single IP addresses,
* shoot multiple requests to that single IP, 
* guard against the above mentioned checks,
* retry every single failed request,
* filter the best response,
* and persist that to disk.

If you don't use MuTimeRx, you don't get these benefits.

# Useful Links

* [Good description of an NTP data packet](https://www.meinbergglobal.com/english/info/ntp-packet.htm)
* [Another NTP data packet  description](http://networksorcery.com/enp/protocol/ntp.htm#Originate_timestamp)

----

# License

```
Original Work (c) Instacart/Kaushik Gopal 2016-2017

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
