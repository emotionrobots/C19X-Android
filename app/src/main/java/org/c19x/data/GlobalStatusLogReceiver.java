package org.c19x.data;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.c19x.C19XApplication;
import org.c19x.util.Logger;

/**
 * Global status log scheduled update task
 */
public class GlobalStatusLogReceiver extends BroadcastReceiver {
    private final static String tag = GlobalStatusLogReceiver.class.getName();

    @Override
    public void onReceive(Context context, Intent intent) {
        Logger.debug(tag, "Global status log update task activated");
        final long currentTimestamp = C19XApplication.getGlobalStatusLog().getTimestamp();
        C19XApplication.getNetworkClient().getUpdate(currentTimestamp, success -> {
            Logger.info(tag, "Global status log update task completed (current={},updated={},result={})", currentTimestamp, C19XApplication.getGlobalStatusLog().getTimestamp(), success);
        });
    }
}
