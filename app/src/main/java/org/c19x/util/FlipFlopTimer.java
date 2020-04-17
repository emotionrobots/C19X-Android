package org.c19x.util;

import android.os.Handler;
import android.os.Looper;


public class FlipFlopTimer {
    private final static String tag = FlipFlopTimer.class.getName();
    private final Handler handler;
    private int onDuration = 1000;
    private int offDuration = 2000;
    private Runnable onToOffAction;
    private Runnable offToOnAction;
    private Runnable onToOffActionWrapper;
    private Runnable offToOnActionWrapper;
    private boolean isActive = false;

    public FlipFlopTimer(int onDuration, int offDuration, Runnable onToOffAction, Runnable offToOnAction) {
        this.handler = new Handler(Looper.getMainLooper());

        this.onDuration = onDuration;
        this.offDuration = offDuration;
        this.onToOffAction = onToOffAction;
        this.offToOnAction = offToOnAction;
        this.onToOffActionWrapper = () -> {
            Logger.debug(tag, "Switching from ON to OFF (onDuration={},offDuration={})", getOnDuration(), getOffDuration());
            onToOffAction.run();
            if (isActive) {
                handler.postDelayed(offToOnActionWrapper, getOffDuration());
            }
        };
        this.offToOnActionWrapper = () -> {
            Logger.debug(tag, "Switching from OFF to ON (onDuration={},offDuration={})", getOnDuration(), getOffDuration());
            offToOnAction.run();
            if (isActive) {
                handler.postDelayed(onToOffActionWrapper, getOnDuration());
            }
        };
    }

    public synchronized int getOnDuration() {
        return onDuration;
    }

    public synchronized void setOnDuration(int onDuration) {
        this.onDuration = onDuration;
    }

    public synchronized int getOffDuration() {
        return offDuration;
    }

    public synchronized void setOffDuration(int offDuration) {
        this.offDuration = offDuration;
    }

    public void start() {
        Logger.debug(tag, "Starting flip flop (onDuration={},offDuration={})", onDuration, offDuration);
        isActive = true;
        offToOnActionWrapper.run();
    }

    public boolean isStarted() {
        return isActive;
    }

    public void stop() {
        isActive = false;
        handler.removeCallbacks(offToOnActionWrapper);
        handler.removeCallbacks(onToOffActionWrapper);
        handler.removeCallbacksAndMessages(null);
        onToOffAction.run();
        Logger.debug(tag, "Stopped flip flop (onDuration={},offDuration={})", onDuration, offDuration);
    }

}
