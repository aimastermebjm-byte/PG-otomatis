# PG Otomatis - Payment Gateway Auto-Verifier

Aplikasi Android untuk mendeteksi notifikasi pembayaran dari 3 bank utama Indonesia dan mengirimkan data ke backend secara otomatis.

## Supported Banks
- MyBCA (Bank Central Asia)
- BRImo (Bank BRI)
- Livin' by Mandiri

## Cara Build APK dari GitHub

1. Download/clone project ini
2. Push ke GitHub repository Anda
3. Kunjungi **Actions** tab di GitHub
4. Klik **Build Android APK** workflow
5. Klik **Run workflow**
6. Tunggu build selesai (2-3 menit)
7. Download APK dari **Artifacts** section

## Cara Install APK

1. Download APK dari GitHub Actions
2. Transfer ke HP Android
3. Enable **Install from unknown sources** di Settings
4. Install APK
5. Buka aplikasi
6. Enable Notification Access permission

## Requirements
- Android 5.0 (API 21) atau lebih tinggi
- Notification Listener permission
- Internet connection untuk mengirim data ke backend

## Features
- Auto-detect payment notifications
- Parse transaction details (amount, sender)
- Send data to Supabase backend
- Show local confirmation notification
- Support 3 major Indonesian banks