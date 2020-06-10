package org.c19x.gui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.PowerManager;
import android.view.Window;
import android.view.WindowManager;

import org.c19x.R;

import static android.content.Context.POWER_SERVICE;

public class ActivityUtil {
    private final static String tag = ActivityUtil.class.getName();

    /**
     * Set app as fullscreen app.
     */
    public final static void setFullscreen(Activity activity) {
        activity.requestWindowFeature(Window.FEATURE_NO_TITLE);
        activity.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    /**
     * Show dialog with OK and CANCEL buttons.
     *
     * @param messageId Message to display.
     * @param positive  Action on OK, null to exclude OK button.
     * @param negative  Action on CANCEL, null to exclude CANCEL button.
     */
    public final static void showDialog(final Activity activity, final int messageId, final Runnable positive, final Runnable negative) {
        showDialog(activity, -1, -1, messageId, positive, negative);
    }

    /**
     * Show dialog with OK and CANCEL buttons.
     *
     * @param titleId   Title to display.
     * @param messageId Message to display.
     * @param positive  Action on OK, null to exclude OK button.
     * @param negative  Action on CANCEL, null to exclude CANCEL button.
     */
    public final static void showDialog(final Activity activity, final int titleId, final int messageId, final Runnable positive, final Runnable negative) {
        showDialog(activity, -1, titleId, messageId, positive, negative);
    }

    /**
     * Show dialog with OK and CANCEL buttons.
     *
     * @param messageId Message to display.
     * @param positive  Action on OK, null to exclude OK button.
     * @param negative  Action on CANCEL, null to exclude CANCEL button.
     */
    public final static void showDialog(final Activity activity, final int iconId, final int titleId, final int messageId, final Runnable positive, final Runnable negative) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity).setMessage(messageId);
        if (positive != null) {
            builder = builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    dialogInterface.dismiss();
                    positive.run();
                }
            });
        }
        if (negative != null) {
            builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    dialogInterface.dismiss();
                    negative.run();
                }
            });
        }
        if (titleId != -1) {
            builder.setTitle(titleId);
        }
        if (iconId != -1) {
            builder.setIcon(iconId);
        }
        builder.setCancelable(positive == null && negative == null).show();
    }

    /**
     * Get wake lock to keep the CPU awake. This will have a dramatic impact on battery life. Use
     * wakeLock.release() to release the CPU again.
     *
     * @param activity
     * @return Wake lock.
     */
    public final static PowerManager.WakeLock getWakeLock(final Activity activity) {
        final PowerManager powerManager = (PowerManager) activity.getSystemService(POWER_SERVICE);
        final PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, activity.getClass().getName());
        wakeLock.acquire();
        return wakeLock;
    }


}
