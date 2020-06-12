package org.c19x;

import android.app.Activity;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.c19x.data.Logger;
import org.c19x.data.primitive.Tuple;
import org.c19x.logic.ConcreteController;
import org.c19x.logic.Controller;
import org.c19x.logic.ForegroundService;

/**
 * Application and singletons.
 */
public class AppDelegate extends Application implements Application.ActivityLifecycleCallbacks {
    private final static String tag = AppDelegate.class.getName();
    private static AppDelegate appDelegate;
    public Controller controller;
    // Detecting foreground/background
    private int activityReferences = 0;
    private boolean isActivityChangingConfigurations = false;
    // Notifications
    private String notificationText = null;

    @Override
    public void onCreate() {
        Logger.debug(tag, "onCreate");
        super.onCreate();
        appDelegate = this;
        controller = new ConcreteController(getApplicationContext());
        registerActivityLifecycleCallbacks(this);
        createNotificationChannel();

        final Intent intent = new Intent(this, ForegroundService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    @Override
    public void onTerminate() {
        Logger.debug(tag, "onTerminate");
        final Intent intent = new Intent(this, ForegroundService.class);
        stopService(intent);
        super.onTerminate();
    }

    public static AppDelegate getAppDelegate() {
        return appDelegate;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            final int importance = NotificationManager.IMPORTANCE_DEFAULT;
            final NotificationChannel channel = new NotificationChannel("C19XNotificationChannel", "C19X", importance);
            channel.setDescription("C19X notifications");
            final NotificationManager notificationManager = getApplicationContext().getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    public Tuple<Integer, Notification> notification(final String title, final String body) {
        Logger.debug(tag, "notification (title={},body={})", title, body);
        final int notificationChannelId = "C19XNotificationChannel".hashCode();
        if (body != null) {
            if (!body.equals(notificationText)) {
                createNotificationChannel();
                final Intent intent = new Intent(getApplicationContext(), AppDelegate.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                final PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, intent, 0);
                final NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), "C19XNotificationChannel")
                        .setSmallIcon(R.drawable.virus)
                        .setContentTitle(title)
                        .setContentText(body)
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT);
                final Notification notification = builder.build();
                final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getApplicationContext());
                notificationManager.notify(notificationChannelId, notification);
                notificationText = body;
                return new Tuple<>(notificationChannelId, notification);
            }
        } else {
            final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getApplicationContext());
            notificationManager.deleteNotificationChannel("C19XNotificationChannel");
        }
        return new Tuple<>(notificationChannelId, null);
    }

    // MARK:- Application.ActivityLifecycleCallbacks

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        if (++activityReferences == 1 && !isActivityChangingConfigurations && controller != null) {
            controller.foreground();
        }
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        isActivityChangingConfigurations = activity.isChangingConfigurations();
        if (--activityReferences == 0 && !isActivityChangingConfigurations && controller != null) {
            controller.background();
        }
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
    }
}
