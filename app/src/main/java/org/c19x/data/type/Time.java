package org.c19x.data.type;

import java.util.Date;

public class Time {
    public Date value;

    public Time(Date value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "Time{" +
                "value=" + value +
                '}';
    }
}
