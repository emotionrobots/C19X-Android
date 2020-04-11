package org.c19x.util.messaging;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * Broadcaster for distributing action to listeners.
 *
 * @param <T>
 */
public class DefaultBroadcaster<T> implements Broadcaster<T> {
    private final Queue<T> listeners = new ConcurrentLinkedQueue<>();

    /**
     * Add listener.
     *
     * @param listener
     * @return
     */
    public boolean addListener(T listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Remove listener.
     *
     * @param listener
     * @return
     */
    public boolean removeListener(T listener) {
        if (listeners.contains(listener)) {
            listeners.remove(listener);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Broadcast action to all listeners.
     *
     * @param action Action on listener.
     */
    public void broadcast(Consumer<T> action) {
        listeners.forEach(action);
    }
}
