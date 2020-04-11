package org.c19x.gui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.Window;
import android.view.WindowManager;

import org.c19x.R;

public class ActivityUtil {

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
        builder.setCancelable(positive == null && negative == null).show();
    }


}
