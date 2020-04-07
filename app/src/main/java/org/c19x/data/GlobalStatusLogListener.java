package org.c19x.data;

/**
 * Listener for log update events.
 */
public interface GlobalStatusLogListener {

    /**
     * Global status log has been updated.
     *
     * @param fromVersion From version
     * @param toVersion   To version
     */
    void updated(final long fromVersion, final long toVersion);

}
