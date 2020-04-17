package org.c19x.gui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.PowerManager;
import android.view.Window;
import android.view.WindowManager;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.c19x.C19XApplication;
import org.c19x.R;
import org.c19x.util.Logger;

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
     * Create notification
     */
    private static String notificationText = null;
    private static Notification notification = null;

    public final static synchronized Notification setNotification(final Context src, final String text) {
        Logger.info(tag, "Create notification (context={},text={})", src, text);
        final Context context = C19XApplication.getContext();
        if (text != null) {
            if (!text.equals(notificationText)) {
                C19XApplication.createNotificationChannel();

                final Intent intent = new Intent(context, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                final PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);

                final NotificationCompat.Builder builder = new NotificationCompat.Builder(context, C19XApplication.notificationChannelId)
                        .setSmallIcon(R.drawable.virus)
                        .setContentTitle(context.getString(R.string.app_fullname))
                        .setContentText(text)
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT);
                notification = builder.build();
                final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
                notificationManager.notify(Integer.parseInt(C19XApplication.notificationChannelId), notification);
                notificationText = text;
            }
            return notification;
        } else {
            final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
            notificationManager.deleteNotificationChannel(C19XApplication.notificationChannelId);
            return null;
        }
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
