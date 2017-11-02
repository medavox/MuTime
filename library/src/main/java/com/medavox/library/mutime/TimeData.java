package com.medavox.library.mutime;

import android.os.SystemClock;

/**
 * @author Adam Howard
 * created on 16/10/17.
 */
class TimeData {
    private long roundTripDelay;
    private long uptimeOffset;
    private long clockOffset;

    /**Optional constructor. It's strongly recommended NOT TO USE THIS constructor,
     * as the 3 identical-type arguments can easily be mis-ordered.
     * @deprecated */
    public TimeData(long roundTripDelay, long uptimeOffset, long clockOffset) {
        this.roundTripDelay = roundTripDelay;
        this.uptimeOffset = uptimeOffset;
        this.clockOffset = clockOffset;
    }

    /**
     * time value computed from NTP server response
     */
    public long getRoundTripDelay() {
        return roundTripDelay;
    }

    /**
     * Device uptime corresponding to the SNTP time.
     * In other words, this is the value of {@link SystemClock#elapsedRealtime()} ()}
     * at the time represented by {@link #getRoundTripDelay()}.
     */
    public long getUptimeOffset() {
        return uptimeOffset;
    }

    /**The System Clock value corresponding to the SNTP time.
     * In other words, this is the value of {@link System#currentTimeMillis()}
     * at the time represented by {@link #getRoundTripDelay()}.*/
    public long getClockOffset() {
        return clockOffset;
    }

    /**Optional Builder class.
     * Recommended because this prevents accidental mis-ordering of arguments.*/
    public static class Builder {

        private long roundTripDelay;
        private long uptimeOffset;
        private long systemClockOffset;

        public Builder() {

        }

        public Builder(TimeData existing) {
            roundTripDelay = existing.getRoundTripDelay();
            uptimeOffset = existing.getUptimeOffset();
            systemClockOffset = existing.getClockOffset();
        }

        public Builder roundTripDelay(long time) {
            roundTripDelay = time;
            return this;
        }

        public Builder uptimeOffset(long time) {
            uptimeOffset = time;
            return this;
        }

        public Builder systemClockOffset(long time) {
            systemClockOffset = time;
            return this;
        }

        public TimeData build() {
            if(roundTripDelay != 0 && uptimeOffset != 0 && systemClockOffset != 0) {
                return new TimeData(roundTripDelay, uptimeOffset, systemClockOffset);
            }
            else {
                throw new IllegalArgumentException("All arguments must be set. Passed Values:"+
                "roundTripDelay="+ roundTripDelay +"; uptimeOffset="+ uptimeOffset
                        +"; systemClockOffset="+ systemClockOffset);
            }
        }
    }
    @Override
    public String toString() {
        return "TimeData ["
                +"Round Trip Delay: "+ roundTripDelay
                +"; System Clock offset: "+ clockOffset
                +"; Device Uptime offset: "+ uptimeOffset
                +"]";
    }

    @Override
    public boolean equals(Object obj) {
        if(obj == null) {
            return false;
        }
        else if(obj instanceof TimeData) {
            TimeData td = (TimeData)obj;
            return td.getUptimeOffset() == uptimeOffset &&
                    td.getClockOffset() == clockOffset &&
                    td.getRoundTripDelay() == roundTripDelay;
        }
        else {
            return false;
        }
    }
}
