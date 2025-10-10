package com.pgotomatis.notificationlistener;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;

public class MainActivity extends AppCompatActivity {
    private static final int NOTIFICATION_LISTENER_REQUEST_CODE = 1001;
    private TextView statusText;
    private Button btnEnable;
    private Button btnTest;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        btnEnable = findViewById(R.id.btnEnable);
        btnTest = findViewById(R.id.btnTest);

        prefs = getSharedPreferences("PGOtomatisPrefs", MODE_PRIVATE);

        // Check if notification listener is enabled
        checkNotificationListenerStatus();

        // Setup button listeners
        btnEnable.setOnClickListener(v -> openNotificationListenerSettings());
        btnTest.setOnClickListener(v -> testNotification());
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkNotificationListenerStatus();
    }

    private void checkNotificationListenerStatus() {
        if (isNotificationListenerEnabled()) {
            statusText.setText("✅ Notification Listener Active");
            statusText.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            btnEnable.setVisibility(View.GONE);
            btnTest.setVisibility(View.VISIBLE);

            // Save status
            prefs.edit().putBoolean("service_enabled", true).apply();
        } else {
            statusText.setText("❌ Notification Listener Disabled");
            statusText.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            btnEnable.setVisibility(View.VISIBLE);
            btnTest.setVisibility(View.GONE);

            // Save status
            prefs.edit().putBoolean("service_enabled", false).apply();
        }
    }

    private boolean isNotificationListenerEnabled() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            return notificationManager.isNotificationListenerAccessGranted();
        } else {
            // For older Android versions
            String packageName = getPackageName();
            String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
            if (!TextUtils.isEmpty(flat)) {
                String[] names = flat.split(":");
                for (String name : names) {
                    if (name.contains(packageName)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    private void openNotificationListenerSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            startActivityForResult(intent, NOTIFICATION_LISTENER_REQUEST_CODE);
            Toast.makeText(this, "Please enable PG Otomatis service", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Cannot open notification settings", Toast.LENGTH_SHORT).show();
        }
    }

    private void testNotification() {
        // Create a test notification
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.NotificationChannel channel = new android.app.NotificationChannel(
                "test_channel",
                "Test Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            );
            notificationManager.createNotificationChannel(channel);
        }

        android.app.Notification notification = new androidx.core.app.NotificationCompat.Builder(this, "test_channel")
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle("MyBCA")
                .setContentText("Transfer masuk Rp500.000 dari TEST USER")
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build();

        notificationManager.notify(999, notification);
        Toast.makeText(this, "Test notification sent!", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == NOTIFICATION_LISTENER_REQUEST_CODE) {
            checkNotificationListenerStatus();
        }
    }
}