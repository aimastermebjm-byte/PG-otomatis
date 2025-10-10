package com.shoppwa.payment;

import android.content.Intent;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class NotificationListenerService extends NotificationListenerService {

    private static final String TAG = "NotificationListener";
    private static final String SUPABASE_URL = "https://kvvciuedziljwpldduqp.supabase.co";
    private static final String SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imt2dmNpdWVkemlsandwbGRkdXFwIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTk1NTIxNzQsImV4cCI6MjA3NTEyODE3NH0.ENuhSVCxCNaudwQ3nO20jXx-n9gCgNKtI0miPzP8eRc";
    private static final String ENDPOINT = SUPABASE_URL + "/functions/v1/receive-notification";

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        super.onNotificationPosted(sbn);

        String packageName = sbn.getPackageName();
        Bundle extras = sbn.getNotification().extras;

        // Filter hanya notifikasi dari aplikasi SMS atau banking
        if (isBankingNotification(packageName)) {
            String title = extras.getString("android.title");
            String text = extras.getCharSequence("android.text").toString();
            String postTime = String.valueOf(sbn.getPostTime());

            Log.d(TAG, "Bank notification detected: " + packageName + " - " + text);

            // Parse notifikasi
            String bankType = detectBankType(text);

            if (isValidPaymentNotification(text)) {
                // Kirim ke Supabase
                sendNotificationToSupabase(
                    packageName,
                    text,
                    postTime,
                    bankType
                );
            }
        }
    }

    private boolean isBankingNotification(String packageName) {
        // Package name untuk aplikasi SMS dan banking di Indonesia
        String[] bankingApps = {
            "com.google.android.apps.messaging",      // Google Messages
            "com.samsung.android.messaging",          // Samsung Messages
            "com.miui.messaging",                     // MIUI Messages
            "com.whatsapp",                          // WhatsApp (untuk notifikasi)
            "com.bca",                               // BCA Mobile
            "com.bni",                               // BNI Mobile
            "com.bri",                               // BRI Mobile
            "com.bankmandiri.mobile",                // Mandiri Mobile
            "id.co.bni.mobile",                      // BNI Mobile Banking
            "com.bca.bcandroid",                     // BCA Mobile
            "com.bri.brimo",                         // BRIMO
            "id.co.bankmandiri.mandirimobile"       // Mandiri Online
        };

        for (String app : bankingApps) {
            if (packageName != null && packageName.contains(app)) {
                return true;
            }
        }

        return false;
    }

    private String detectBankType(String message) {
        if (message.toUpperCase().contains("BCA")) return "BCA";
        if (message.toUpperCase().contains("MANDIRI")) return "MANDIRI";
        if (message.toUpperCase().contains("BNI")) return "BNI";
        if (message.toUpperCase().contains("BRI")) return "BRI";
        return "AUTO";
    }

    private boolean isValidPaymentNotification(String message) {
        // Pattern untuk deteksi pembayaran masuk
        String[] keywords = {
            "telah diterima",
            "telah masuk",
            "transfer masuk",
            "credit",
            "debet",
            "menerima transfer",
            "pembayaran",
            "top up"
        };

        for (String keyword : keywords) {
            if (message.toLowerCase().contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    private void sendNotificationToSupabase(String senderNumber, String message, String timestamp, String bankType) {
        new Thread(() -> {
            try {
                URL url = new URL(ENDPOINT);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + SUPABASE_ANON_KEY);
                conn.setDoOutput(true);

                // Create JSON payload
                JSONObject payload = new JSONObject();
                payload.put("senderNumber", senderNumber);
                payload.put("message", message);
                payload.put("timestamp", timestamp);
                payload.put("deviceId", android.provider.Settings.Secure.getString(
                    getContentResolver(),
                    android.provider.Settings.Secure.ANDROID_ID
                ));
                payload.put("bankType", bankType);

                // Send request
                try (DataOutputStream wr = new DataOutputStream(conn.getOutputStream())) {
                    wr.write(payload.toString().getBytes(StandardCharsets.UTF_8));
                }

                int responseCode = conn.getResponseCode();
                Log.d(TAG, "Response Code: " + responseCode);

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                        StringBuilder response = new StringBuilder();
                        String responseLine;
                        while ((responseLine = br.readLine()) != null) {
                            response.append(responseLine.trim());
                        }
                        Log.d(TAG, "Response: " + response.toString());
                    }
                } else {
                    Log.e(TAG, "Error sending notification: " + responseCode);
                }

            } catch (IOException | JSONException e) {
                Log.e(TAG, "Error sending notification to Supabase", e);
            }
        }).start();
    }
}