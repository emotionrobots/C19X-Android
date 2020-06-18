package org.c19x.data.type;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;

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

    public TimeInterval timeIntervalSince(Time time) {
        return new TimeInterval((value.getTime() - time.value.getTime()) / 1000);
    }

    public Time advanced(TimeInterval by) {
        return new Time(value.getTime() + by.value * 1000);
    }

    public Time subtractingTimeInterval(TimeInterval timeInterval) {
        return new Time(value.getTime() - timeInterval.value * 1000);
    }

    public String description() {
        final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd/MM/YYYY HH:mm");
        return simpleDateFormat.format(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Time time = (Time) o;
        return Objects.equals(value.getTime(), time.value.getTime());
    }

    @Override
    public int hashCode() {
        return Objects.hash(value.getTime());
    }

    @Override
    public String toString() {
        return "Time{" +
                "value=" + value +
                '}';
    }
}
