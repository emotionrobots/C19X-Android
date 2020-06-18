package org.c19x.gui;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;

import org.c19x.AppDelegate;
import org.c19x.R;
import org.c19x.beacon.Transceiver;
import org.c19x.data.Logger;
import org.c19x.data.primitive.Triple;
import org.c19x.data.primitive.Tuple;
import org.c19x.data.type.Advice;
import org.c19x.data.type.BluetoothState;
import org.c19x.data.type.Contact;
import org.c19x.data.type.ControllerState;
import org.c19x.data.type.Message;
import org.c19x.data.type.SerialNumber;
import org.c19x.data.type.Status;
import org.c19x.data.type.Time;
import org.c19x.logic.Controller;
import org.c19x.logic.ControllerDelegate;

import java.util.Deque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class MainActivity extends Activity implements ControllerDelegate {
    private final static String tag = MainActivity.class.getName();
    private final ExecutorService operationQueue = Executors.newSingleThreadExecutor();
    private PowerManager.WakeLock wakeLock;
    private Controller controller;

    // UI elements
    private Button[] segmentedControl;
    private TextView statusDescription;
    private TextView statusLastUpdate;
    private TextView contactDescription;
    private ImageView contactDescriptionStatus;
    private TextView contactValue;
    private TextView contactValueUnit;
    private TextView contactLastUpdate;
    private TextView adviceDescription;
    private ImageView adviceDescriptionStatus;
    private TextView adviceMessage;
    private TextView adviceLastUpdate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        wakeLock = ActivityUtil.getWakeLock(this);
        controller = AppDelegate.getAppDelegate().controller;
        ActivityUtil.setFullscreen(this);
        setContentView(R.layout.activity_main);

        // UI elements
        segmentedControl = new Button[]{
                findViewById(R.id.buttonNormal),
                findViewById(R.id.buttonSymptoms),
                findViewById(R.id.buttonDiagnosis)
        };
        statusDescription = findViewById(R.id.StatusDescription);
        statusLastUpdate = findViewById(R.id.StatusLastUpdate);
        contactDescription = findViewById(R.id.ContactDescription);
        contactDescriptionStatus = findViewById(R.id.ContactDescriptionStatus);
        contactValue = findViewById(R.id.ContactValue);
        contactValueUnit = findViewById(R.id.ContactUnit);
        contactLastUpdate = findViewById(R.id.ContactLastUpdate);
        adviceDescription = findViewById(R.id.AdviceDescription);
        adviceDescriptionStatus = findViewById(R.id.AdviceDescriptionStatus);
        adviceMessage = findViewById(R.id.AdviceMessage);
        adviceLastUpdate = findViewById(R.id.AdviceLastUpdate);

        enableStatusSelector();
        controller.delegates.add(this);
        updateViewData(true, true, true, true);

        // TODO : CONSIDER REMOVAL FOR PRODUCTION
        enableImmediateUpdate();
        enableExportContacts();
    }

    @Override
    protected void onDestroy() {
        if (wakeLock != null) {
            wakeLock.release();
            wakeLock = null;
        }
        controller.delegates.remove(this);
        AppDelegate.getAppDelegate().onTerminate();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkPermissions();
    }

    private void statusSelector(Status setTo) {
        final int setToIndex = Status.toRawValue(setTo);
        for (int i = segmentedControl.length; i-- > 0; ) {
            segmentedControl[i].setBackgroundResource(i == setToIndex ? R.color.white : R.color.transparent);
        }
    }

    private void enableStatusSelector(Consumer<Status> valueChanged) {
        for (int i = segmentedControl.length; i-- > 0; ) {
            final Status status = Status.forRawValue(i);
            segmentedControl[i].setOnClickListener(v -> {
                statusSelector(status);
                valueChanged.accept(status);
            });
        }
    }

    private void enableStatusSelector() {
        enableStatusSelector(status -> {
            statusDescription(status);
            Logger.debug(tag, "Status selector value changed (status={})", status);
            showSharedInfectionDataConfirmationDialog(() -> {
                // Don't allow : Revert to stored value
                updateViewData(true, false, false, false);
            }, () -> {
                // Allow : Set status locally and remotely
                controller.status(status);
                updateViewData(true, false, false, false);
            });
        });
    }

    private void checkPermissions() {
        final String locationPermission = Manifest.permission.ACCESS_FINE_LOCATION;
        if (ActivityCompat.checkSelfPermission(this, locationPermission) != PackageManager.PERMISSION_GRANTED) {
            Logger.debug(tag, "Requesting access location permission");
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, locationPermission)) {
                showRequestLocationPermissionRationaleDialog(
                        () -> finish(),
                        () -> ActivityCompat.requestPermissions(this, new String[]{locationPermission}, 0));
            } else {
                ActivityCompat.requestPermissions(this, new String[]{locationPermission}, 0);
            }
        }
    }

    private void updateViewData(boolean status, boolean contacts, boolean advice, boolean suppressNotification) {
        operationQueue.execute(() -> {
            Logger.debug(tag, "updateViewData (status={},contacts={},advice={})", status, contacts, advice);
            if (status) {
                final Triple<Status, Time, Time> settingsStatus = controller.settings().status();
                final String statusLastUpdateText = (settingsStatus.b.equals(Time.distantPast) ? "" : settingsStatus.b.description());
                final Status setTo = settingsStatus.a;
                runOnUiThread(() -> {
                    statusSelector(setTo);
                    statusDescription(setTo);
                    statusLastUpdate.setText(statusLastUpdateText);
                    statusLastUpdate.setHeight(statusLastUpdateText.isEmpty() ? 0 : statusLastUpdate.getLineHeight());
                });
            }
            if (contacts) {
                final Triple<Integer, Status, Time> settingsContacts = controller.settings().contacts();
                final String contactValueText = settingsContacts.a.toString();
                final String contactValueUnitText = (settingsContacts.a.intValue() < 2 ? "contact" : "contacts") + " tracked";
                final String contactLastUpdateText = (settingsContacts.c.equals(Time.distantPast) ? "" : settingsContacts.c.description());
                final Status setTo = settingsContacts.b;
                runOnUiThread(() -> {
                    contactValue.setText(contactValueText);
                    contactValueUnit.setText(contactValueUnitText);
                    contactLastUpdate.setText(contactLastUpdateText);
                    contactLastUpdate.setHeight(contactLastUpdateText.isEmpty() ? 0 : contactLastUpdate.getLineHeight());
                    if (contactDescription(setTo) && !suppressNotification) {
                        AppDelegate.getAppDelegate().notification("Recent Contacts Updated", "Open C19X app to review update.");
                    }
                });
            }
            if (advice) {
                final Triple<Advice, Advice, Time> settingsAdvice = controller.settings().advice();
                final Tuple<Message, Time> settingsMessage = controller.settings().message();
                final Advice setTo = settingsAdvice.b;
                final String messageText = settingsMessage.a.value;
                final Time timestamp = (settingsAdvice.c.value.getTime() > settingsMessage.b.value.getTime() ? settingsAdvice.c : settingsMessage.b);
                final String adviceLastUpdateText = timestamp.description();
                runOnUiThread(() -> {
                    if (adviceDescription(setTo) && !suppressNotification) {
                        AppDelegate.getAppDelegate().notification("Advice Updated", "Open C19X app to review update.");
                    }
                    if (adviceMessage(messageText) && !suppressNotification) {
                        AppDelegate.getAppDelegate().notification("Message Received", "Open C19X app to view message.");
                    }
                    adviceLastUpdate.setText(adviceLastUpdateText);
                });
            }
        });
    }

    private void statusDescription(Status setTo) {
        switch (setTo) {
            case healthy: {
                statusDescription.setText("I do not have a high temperature or a new continuous cough.");
                break;
            }
            case symptomatic: {
                statusDescription.setText("I have a high temperature and/or a new continuous cough.");
                break;
            }
            case confirmedDiagnosis: {
                statusDescription.setText("I have a confirmed diagnosis of Coronavirus (COVID-19).");
                break;
            }
            default:
                break;
        }
    }

    private boolean contactDescription(Status setTo) {
        final String text = text(contactDescription);
        if (setTo == Status.healthy) {
            contactDescription.setText(R.string.contactDescriptionHealthy);
            contactDescriptionStatus.setBackgroundResource(R.color.systemGreen);
        } else {
            contactDescription.setText(R.string.contactDescriptionInfectious);
            contactDescriptionStatus.setBackgroundResource(R.color.systemRed);
        }
        return !text.equals(text(contactDescription));
    }

    private boolean adviceDescription(Advice setTo) {
        final String text = text(adviceDescription);
        switch (setTo) {
            case normal: {
                adviceDescription.setText(R.string.adviceDescriptionNormal);
                adviceDescriptionStatus.setBackgroundResource(R.color.systemGreen);
                break;
            }
            case stayAtHome: {
                adviceDescription.setText(R.string.adviceDescriptionStayAtHome);
                adviceDescriptionStatus.setBackgroundResource(R.color.systemOrange);
                break;
            }
            case selfIsolation: {
                adviceDescription.setText(R.string.adviceDescriptionSelfIsolation);
                adviceDescriptionStatus.setBackgroundResource(R.color.systemRed);
                break;
            }
        }
        return !text.equals(text(adviceDescription));
    }

    private boolean adviceMessage(String message) {
        final String text = text(adviceMessage);
        adviceMessage.setText(message);
        adviceMessage.setVisibility(message.isEmpty() ? View.GONE : View.VISIBLE);
        return !text.equals(text(adviceMessage));
    }

    private String text(TextView textView) {
        final CharSequence charSequence = textView.getText();
        if (charSequence == null) {
            return "";
        }
        final String text = charSequence.toString();
        if (text == null) {
            return "";
        }
        return text;
    }

    /**
     * Show confirmation dialog for sharing infection data.
     */
    private void showSharedInfectionDataConfirmationDialog(final Runnable dontAllow, final Runnable allow) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Share Infection Data")
                .setMessage("Share your infection status and contact pattern anonymously to help stop the spread of COVID-19?");
        builder.setNegativeButton("Don't allow", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
                dontAllow.run();
            }
        });
        builder = builder.setPositiveButton("Allow", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
                allow.run();
            }
        });
        builder.show();
    }

    /**
     * Show confirmation dialog for sharing infection data.
     */
    private void showRequestLocationPermissionRationaleDialog(final Runnable dontAllow, final Runnable allow) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Location permission")
                .setMessage("This allows you to detect contacts with other people. This app does not use GPS tracking and your location is never recorded or shared. This permission is only required to enable Bluetooth.");
        builder.setNegativeButton("Don't allow", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
                dontAllow.run();
            }
        });
        builder = builder.setPositiveButton("Allow", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
                allow.run();
            }
        });
        builder.show();
    }


    // MARK:- ControllerDelegate

    @Override
    public void controller(ControllerState didUpdateState) {
        Logger.debug(tag, "controller did update state (state={})", didUpdateState);
        updateViewData(true, true, true, false);
    }

    @Override
    public void registration(SerialNumber serialNumber) {
        Logger.debug(tag, "registration (serialNumber={})", serialNumber.value);
    }

    @Override
    public void transceiver(Transceiver initialised) {
        Logger.debug(tag, "transceiver initialised");
        updateViewData(false, true, false, false);
        transceiver(initialised.state());
    }

    @Override
    public void transceiver(BluetoothState didUpdateState) {
        Logger.debug(tag, "transceiver did update state (state={})", didUpdateState);
        switch (didUpdateState) {
            case poweredOn: {
                AppDelegate.getAppDelegate().notification("Contact Tracing Enabled", "Turn OFF Bluetooth to pause.");
                break;
            }
            case poweredOff: {
                AppDelegate.getAppDelegate().notification("Contact Tracing Disabled", "Turn ON Bluetooth to resume.");
                break;
            }
            case unsupported: {
                AppDelegate.getAppDelegate().notification("Contact Tracing Disabled", "Bluetooth unavailable on device.");
                break;
            }
            default:
                break;
        }
    }

    @Override
    public void transceiver(Time didDetectContactAt) {
        Logger.debug(tag, "transceiver did detect contact (timestamp={})", didDetectContactAt.value);
        updateViewData(false, true, false, false);
    }

    @Override
    public void message(Message didUpdateTo) {
        Logger.debug(tag, "Message did update");
        updateViewData(false, false, true, false);
    }

    @Override
    public void database(Deque<Contact> didUpdateContacts) {
        Logger.debug(tag, "Database did update");
        updateViewData(false, true, false, false);
    }

    @Override
    public void advice(Advice didUpdateTo, Status contactStatus) {
        Logger.debug(tag, "Advice did update");
        updateViewData(false, true, true, false);
    }

    // MARK:- Enable immediate update by double tapping on advice view

    private void enableImmediateUpdate() {
        final GestureDetector doubleTapGestureRecognizer = new GestureDetector(getApplicationContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                Logger.debug(tag, "Immediate update requested");
                controller.synchronise(true);
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                super.onLongPress(e);
            }

            @Override
            public boolean onDoubleTapEvent(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }
        });
        final View view = findViewById(R.id.ViewBottom);
        view.setOnTouchListener((v, event) -> doubleTapGestureRecognizer.onTouchEvent(event));
    }

    // MARK:- Enable export by double tapping on contact view

    private void enableExportContacts() {
        final GestureDetector doubleTapGestureRecognizer = new GestureDetector(getApplicationContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                Logger.debug(tag, "Export requested");
                controller.export();
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                super.onLongPress(e);
            }

            @Override
            public boolean onDoubleTapEvent(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }
        });
        final View view = findViewById(R.id.ViewMiddle);
        view.setOnTouchListener((v, event) -> doubleTapGestureRecognizer.onTouchEvent(event));
    }
}