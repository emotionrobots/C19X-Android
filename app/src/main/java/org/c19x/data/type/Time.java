package org.c19x.data.type;

import java.util.Date;

public class Time {
    public Date value;
    public final static Time distantPast = new Time(0);

    public Time() {
        this.value = new Date();
    }

    public Time(long time) {
        this.value = new Date(time);
    }

    public Time(Date value) {
        this.value = value;
    }

    public TimeInterval timeIntervalSinceNow() {
        return new TimeInterval((value.getTime() - new Date().getTime()) / 1000);
    }

    public Time advanced(TimeInterval by) {
        return new Time(value.getTime() + by.value * 1000);
    }

    public Time subtractingTimeInterval(TimeInterval timeInterval) {
        return new Time(value.getTime() - timeInterval.value * 1000);
    }

    @Override
    public String toString() {
        return "Time{" +
                "value=" + value +
                '}';
    }
}
