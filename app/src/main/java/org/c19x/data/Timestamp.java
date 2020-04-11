package org.c19x.data;

import org.c19x.util.Logger;

public class Timestamp {
    private final static String tag = Timestamp.class.getName();
    private long delta = 0;

    /**
     * Set current server time to establish delta from local time.
     *
     * @param time
     */
    public void setServerTime(final long time) {
        delta = time - System.currentTimeMillis();
        Logger.debug(tag, "Time synchronised (server={},delta={})", time, delta);
    }

    /**
     * Get synchronised time that is close to server time.
     *
     * @return
     */
    public long getTime() {
        return System.currentTimeMillis() + delta;
    }
}
