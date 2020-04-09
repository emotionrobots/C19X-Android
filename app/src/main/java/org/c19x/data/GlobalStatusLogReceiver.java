package org.c19x.data;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.c19x.C19XApplication;
import org.c19x.network.response.NetworkResponse;
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
        C19XApplication.getNetworkClient().getGlobalStatusLog(currentTimestamp, r -> {
            if (r.getNetworkResponse() == NetworkResponse.OK) {
                if (r.getByteArray() != null && r.getByteArray().length > 0) {
                    Logger.info(tag, "Global status log update received");
                    if (C19XApplication.getGlobalStatusLog().setUpdate(r.getByteArray())) {
                        C19XApplication.updateAllParameters();
                        Logger.info(tag, "Global status log update applied successfully (current={},new={})", currentTimestamp, C19XApplication.getGlobalStatusLog().getTimestamp());
                    } else {
                        Logger.warn(tag, "Global status log update was not applied, verification failed");
                    }
                } else {
                    Logger.info(tag, "Global status log is already up to date (current={})", currentTimestamp);
                }
            } else {
                Logger.warn(tag, "Failed to get global status log (response={})", r.getNetworkResponse());
            }
        });
    }
}
