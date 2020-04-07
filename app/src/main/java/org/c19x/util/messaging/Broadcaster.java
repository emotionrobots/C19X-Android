package org.c19x.util.messaging;

import java.util.function.Consumer;

public interface Broadcaster<T> {
    /**
     * Add listener.
     *
     * @param listener
     * @return
     */
    boolean addListener(T listener);

    /**
     * Remove listener.
     *
     * @param listener
     * @return
     */
    boolean removeListener(T listener);

    /**
     * Broadcast action to all listeners.
     *
     * @param action Action on listener.
     */
    void broadcast(Consumer<T> action);
}
