package com.pgotomatis.notificationlistener;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Build;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BankNotificationService extends NotificationListenerService {
    private static final String TAG = "BankNotificationService";
    private static final String CHANNEL_ID = "BankNotificationChannel";
    private static final String SUPABASE_URL = "https://kvvciuedziljwpldduqp.supabase.co/functions/v1/receive-notification";
    private static final String SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imt2dmNpdWVkemlsandwbGRkdXFwIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTk1NTIxNzQsImV4cCI6MjA3NTEyODE3NH0.ENuhSVCxCNaudwQ3nO20jXx-n9gCgNKtI0miPzP8eRc";

    // Package names untuk 3 bank
    private static final String BCA_PACKAGE = "com.bca";
    private static final String BRI_PACKAGE = "com.bri.brime";
    private static final String MANDIRI_PACKAGE = "com.bankmandiri";

    // Alternative package names untuk aplikasi mobile banking
    private static final String[] BCA_PACKAGES = {"com.bca", "com.bca.mobile", "id.co.bca.mybca", "com.bca.mybca"};
    private static final String[] BRI_PACKAGES = {"com.bri.brime", "brimo", "com.bri", "id.co.bri.brimo"};
    private static final String[] MANDIRI_PACKAGES = {"com.bankmandiri", "livin", "com.bankmandiri.mandiri", "id.co.bankmandiri.livin"};

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        super.onNotificationPosted(sbn);

        String packageName = sbn.getPackageName();
        String title = sbn.getNotification().extras.getString(Notification.EXTRA_TITLE);
        String text = sbn.getNotification().extras.getString(Notification.EXTRA_TEXT);

        Log.d(TAG, "Notification received from: " + packageName);
        Log.d(TAG, "Title: " + title);
        Log.d(TAG, "Text: " + text);

        // Filter hanya untuk 3 bank
        if (isBankNotification(packageName)) {
            BankNotificationData notificationData = parseBankNotification(packageName, title, text);
            if (notificationData != null) {
                sendToBackend(notificationData);
                showLocalNotification(notificationData);
            }
        }
    }

    private boolean isBankNotification(String packageName) {
        // Check BCA
        for (String pkg : BCA_PACKAGES) {
            if (packageName.toLowerCase().contains(pkg.toLowerCase())) {
                return true;
            }
        }

        // Check BRI
        for (String pkg : BRI_PACKAGES) {
            if (packageName.toLowerCase().contains(pkg.toLowerCase())) {
                return true;
            }
        }

        // Check Mandiri
        for (String pkg : MANDIRI_PACKAGES) {
            if (packageName.toLowerCase().contains(pkg.toLowerCase())) {
                return true;
            }
        }

        return false;
    }

    private BankNotificationData parseBankNotification(String packageName, String title, String text) {
        if (packageName.contains("bca") || packageName.contains("BCA")) {
            return parseBCANotification(title, text);
        } else if (packageName.contains("bri") || packageName.contains("BRIMO")) {
            return parseBRINotification(title, text);
        } else if (packageName.contains("mandiri") || packageName.contains("livin")) {
            return parseMandiriNotification(title, text);
        }
        return null;
    }

    private BankNotificationData parseBCANotification(String title, String text) {
        Log.d(TAG, "Parsing BCA/MyBCA notification");

        // Pattern untuk MyBCA app
        // Contoh: "MyBCA" dan "Transfer masuk Rp1.234.567 dari JOHN DOE"
        // atau "MyBCA" dan "Payment received Rp500.000"
        if (title != null && (title.toLowerCase().contains("mybca") || title.toLowerCase().contains("bca"))) {
            if (text != null && (text.toLowerCase().contains("transfer masuk") ||
                                 text.toLowerCase().contains("payment received") ||
                                 text.toLowerCase().contains("saldo masuk"))) {

                // Multiple patterns for different notification formats
                String amountStr = null;
                String sender = "Unknown";

                // Pattern 1: Transfer masuk Rp1.234.567 dari NAMA
                Pattern pattern1 = Pattern.compile("transfer masuk\\s+Rp([\\d.,]+)\\s+dari\\s+([^.]+)", Pattern.CASE_INSENSITIVE);
                Matcher matcher1 = pattern1.matcher(text);
                if (matcher1.find()) {
                    amountStr = matcher1.group(1);
                    sender = matcher1.group(2).trim();
                } else {
                    // Pattern 2: Payment received Rp1.234.567
                    Pattern pattern2 = Pattern.compile("payment received\\s+Rp([\\d.,]+)", Pattern.CASE_INSENSITIVE);
                    Matcher matcher2 = pattern2.matcher(text);
                    if (matcher2.find()) {
                        amountStr = matcher2.group(1);
                        // Extract sender from next line or different format
                        Pattern senderPattern = Pattern.compile("dari\\s+([^.]+)", Pattern.CASE_INSENSITIVE);
                        Matcher senderMatcher = senderPattern.matcher(text);
                        if (senderMatcher.find()) {
                            sender = senderMatcher.group(1).trim();
                        }
                    } else {
                        // Pattern 3: Just find amount and sender separately
                        Pattern amountPattern = Pattern.compile("Rp([\\d.,]+)");
                        Pattern senderPattern = Pattern.compile("dari\\s+([^.]+)");

                        Matcher amountMatcher = amountPattern.matcher(text);
                        Matcher senderMatcher = senderPattern.matcher(text);

                        if (amountMatcher.find()) {
                            amountStr = amountMatcher.group(1);
                        }
                        if (senderMatcher.find()) {
                            sender = senderMatcher.group(1).trim();
                        }
                    }
                }

                if (amountStr != null) {
                    // Clean amount from format
                    amountStr = amountStr.replace(".", "").replace(",", "");
                    double amount = Double.parseDouble(amountStr);

                    return new BankNotificationData(
                        "BCA",
                        sender,
                        amount,
                        text,
                        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date())
                    );
                }
            }
        }
        return null;
    }

    private BankNotificationData parseBRINotification(String title, String text) {
        Log.d(TAG, "Parsing BRIMO notification");

        // Pattern untuk BRIMO
        // Contoh: "BRImo" dan "Saldo masuk Rp500.000 dari ANNA"
        if (title != null && (title.toLowerCase().contains("brimo") || title.toLowerCase().contains("bri"))) {
            if (text != null && (text.toLowerCase().contains("saldo masuk") || text.toLowerCase().contains("transfer masuk"))) {
                // Extract amount
                Pattern amountPattern = Pattern.compile("Rp([\\d.,]+)");
                Matcher amountMatcher = amountPattern.matcher(text);

                // Extract sender name
                Pattern senderPattern = Pattern.compile("dari ([^.]+)");
                Matcher senderMatcher = senderPattern.matcher(text);

                if (amountMatcher.find()) {
                    String amountStr = amountMatcher.group(1).replace(".", "").replace(",", "");
                    double amount = Double.parseDouble(amountStr);

                    String sender = senderMatcher.find() ? senderMatcher.group(1).trim() : "Unknown";

                    return new BankNotificationData(
                        "BRI",
                        sender,
                        amount,
                        text,
                        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date())
                    );
                }
            }
        }
        return null;
    }

    private BankNotificationData parseMandiriNotification(String title, String text) {
        Log.d(TAG, "Parsing Mandiri notification");

        // Pattern untuk Livin' by Mandiri
        // Contoh: "Livin' by Mandiri" dan "Transfer masuk Rp250.000 dari BOB"
        if (title != null && (title.toLowerCase().contains("livin") || title.toLowerCase().contains("mandiri"))) {
            if (text != null && text.toLowerCase().contains("transfer masuk")) {
                // Extract amount
                Pattern amountPattern = Pattern.compile("Rp([\\d.,]+)");
                Matcher amountMatcher = amountPattern.matcher(text);

                // Extract sender name
                Pattern senderPattern = Pattern.compile("dari ([^.]+)");
                Matcher senderMatcher = senderPattern.matcher(text);

                if (amountMatcher.find()) {
                    String amountStr = amountMatcher.group(1).replace(".", "").replace(",", "");
                    double amount = Double.parseDouble(amountStr);

                    String sender = senderMatcher.find() ? senderMatcher.group(1).trim() : "Unknown";

                    return new BankNotificationData(
                        "Mandiri",
                        sender,
                        amount,
                        text,
                        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date())
                    );
                }
            }
        }
        return null;
    }

    private void sendToBackend(BankNotificationData data) {
        new Thread(() -> {
            try {
                URL url = new URL(SUPABASE_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + SUPABASE_ANON_KEY);
                conn.setDoOutput(true);

                // Create JSON payload
                JSONObject json = new JSONObject();
                json.put("bankName", data.getBankName());
                json.put("senderName", data.getSenderName());
                json.put("amount", data.getAmount());
                json.put("fullText", data.getFullText());
                json.put("timestamp", data.getTimestamp());

                // Send request
                OutputStream os = conn.getOutputStream();
                os.write(json.toString().getBytes("UTF-8"));
                os.close();

                // Get response
                int responseCode = conn.getResponseCode();
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
                br.close();

                Log.d(TAG, "Response code: " + responseCode);
                Log.d(TAG, "Response: " + response.toString());

            } catch (IOException | JSONException e) {
                Log.e(TAG, "Error sending to backend", e);
            }
        }).start();
    }

    private void showLocalNotification(BankNotificationData data) {
        createNotificationChannel();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Payment Detected - " + data.getBankName())
                .setContentText(String.format("Rp %,.0f from %s", data.getAmount(), data.getSenderName()))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.notify(1, builder.build());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Bank Notifications",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Bank payment notifications");

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    // Data class untuk menyimpan notifikasi bank
    private static class BankNotificationData {
        private String bankName;
        private String senderName;
        private double amount;
        private String fullText;
        private String timestamp;

        public BankNotificationData(String bankName, String senderName, double amount, String fullText, String timestamp) {
            this.bankName = bankName;
            this.senderName = senderName;
            this.amount = amount;
            this.fullText = fullText;
            this.timestamp = timestamp;
        }

        // Getters
        public String getBankName() { return bankName; }
        public String getSenderName() { return senderName; }
        public double getAmount() { return amount; }
        public String getFullText() { return fullText; }
        public String getTimestamp() { return timestamp; }
    }
}