package org.c19x.gui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.c19x.R;
import org.c19x.data.Logger;

public class TestActivity extends AppCompatActivity {
    private final static String tag = TestActivity.class.getName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test);

        final String locationPermission = Manifest.permission.ACCESS_FINE_LOCATION;
        if (ActivityCompat.checkSelfPermission(this, locationPermission) != PackageManager.PERMISSION_GRANTED) {
            Logger.debug(tag, "Requesting access location permission");
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, locationPermission)) {
                ActivityUtil.showDialog(this,
                        R.string.dialog_welcome_permission_location_title,
                        R.string.dialog_welcome_permission_location_rationale,
                        () -> ActivityCompat.requestPermissions(this, new String[]{locationPermission}, 0), () -> finish());
            } else {
                ActivityCompat.requestPermissions(this, new String[]{locationPermission}, 0);
            }
        }


    }

}
