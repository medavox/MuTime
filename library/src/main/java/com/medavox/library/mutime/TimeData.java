package com.medavox.library.mutime;

import android.os.SystemClock;

/**
 * Created by Adam Howard on 16/10/17.
 */

class TimeData {
    private long sntpTime;
    private long uptimeAtSntpTime;
    private long systemClockAtSntpTime;

    /**Optional constructor. It's strongly recommended NOT TO USE THIS constructor,
     * as the 3 identical-type arguments can easily be mis-ordered.
     * @deprecated */
    public TimeData(long sntpTime, long uptimeAtSntpTime, long systemClockAtSntpTime) {
        this.sntpTime = sntpTime;
        this.uptimeAtSntpTime = uptimeAtSntpTime;
        this.systemClockAtSntpTime = systemClockAtSntpTime;
    }

    /**
     * time value computed from NTP server response
     */
    public long getSntpTime() {
        return sntpTime;
    }

    /**
     * Device uptime corresponding to the SNTP time.
     * In other words, this is the value of {@link SystemClock#elapsedRealtime()} ()}
     * at the time represented by {@link #getSntpTime()}.
     */
    public long getUptimeAtSntpTime() {
        return uptimeAtSntpTime;
    }

    /**The System Clock value corresponding to the SNTP time.
     * In other words, this is the value of {@link System#currentTimeMillis()}
     * at the time represented by {@link #getSntpTime()}.*/
    public long getSystemClockAtSntpTime() {
        return systemClockAtSntpTime;
    }

    /**Optional Builder class.
     * Recommended because this prevents accidental mis-ordering of arguments.*/
    public static class Builder {

        private long sntpTime;
        private long uptimeAtSntpTime;
        private long systemClockAtSntpTime;

        public Builder() {

        }

        public Builder(TimeData existing) {
            sntpTime = existing.getSntpTime();
            uptimeAtSntpTime = existing.getUptimeAtSntpTime();
            systemClockAtSntpTime = existing.getSystemClockAtSntpTime();
        }

        public Builder sntpTime(long time) {
            sntpTime = time;
            return this;
        }

        public Builder uptimeAtSntpTime(long time) {
            uptimeAtSntpTime = time;
            return this;
        }

        public Builder systemClockAtSntpTime(long time) {
            systemClockAtSntpTime = time;
            return this;
        }

        public TimeData build() {
            if(sntpTime != 0 && uptimeAtSntpTime != 0 && systemClockAtSntpTime != 0) {
                return new TimeData(sntpTime, uptimeAtSntpTime, systemClockAtSntpTime);
            }
            else {
                throw new IllegalArgumentException("All arguments must be set. Passed Values:"+
                "sntpTime="+sntpTime+"; uptimeAtSntpTime="+uptimeAtSntpTime
                        +"; systemClockAtSntpTime="+systemClockAtSntpTime);
            }
        }
    }
}
