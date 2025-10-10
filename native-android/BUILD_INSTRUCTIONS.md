# Build Android APK - PG Otomatis

## 📋 Prerequisites:
1. **Android Studio** (Recommended) OR
2. **Java JDK 8+** + **Android SDK Command Line Tools**

## 🚀 Cara Build APK:

### Option 1: Dengan Android Studio (Rekomendasi)

1. **Install Android Studio**
   - Download: https://developer.android.com/studio
   - Install Android SDK (API level 34)

2. **Buka Project**
   ```
   File → Open → Pilih folder: D:\My Project\PG otomatis\native-android
   ```

3. **Sync Project**
   - Tunggu Gradle sync selesai
   - Install missing components jika diminta

4. **Build APK**
   ```
   Menu → Build → Build Bundle(s) / APK(s) → Build APK(s)
   ```

5. **Lokasi APK**
   ```
   app/build/outputs/apk/debug/app-debug.apk
   ```

### Option 2: Dengan Command Line (Terminal)

1. **Install Java JDK**
   - Pastikan sudah install JDK 8 atau lebih baru
   - Cek: `java -version`

2. **Download Android SDK Command Line Tools**
   - Download: https://developer.android.com/studio#command-tools
   - Extract ke folder aman

3. **Setup Environment Variables**
   ```bash
   # Di Windows (Environment Variables):
   ANDROID_HOME = C:\path\to\android-sdk
   Path tambahkan: %ANDROID_HOME%\tools
   Path tambahkan: %ANDROID_HOME%\platform-tools
   ```

4. **Install Required Packages**
   ```bash
   # Buka CMD/PowerShell di folder native-android
   cd "D:\My Project\PG otomatis\native-android"

   # Install build-tools
   sdkmanager "build-tools;34.0.0"
   sdkmanager "platforms;android-34"
   sdkmanager "platform-tools"
   ```

5. **Build APK**
   ```bash
   # Debug APK
   gradlew assembleDebug

   # Atau
   ./gradlew assembleDebug

   # Release APK (perlu signing)
   gradlew assembleRelease
   ```

## 📱 Cara Install APK:

### Install Manual:
1. Copy file `app-debug.apk` ke HP Android
2. Enable "Install from unknown sources"
3. Buka file APK dan install

### Install via ADB:
```bash
# Connect HP dengan USB Debugging enabled
adb install app/build/outputs/apk/debug/app-debug.apk
```

## ⚙️ Konfigurasi Sebelum Install:

1. **Enable Notification Listener**
   - Buka app PG Otomatis
   - Klik "Enable Notification Access"
   - Aktifkan "PG Otomatis Payment Verifier"

2. **Test Functionality**
   - Klik "Send Test Notification"
   - Cek apakah muncul notifikasi "MyBCA"

3. **Grant Permissions**
   - Notification access (wajib)
   - Overlay permission (opsional)

## 🐛 Troubleshooting:

### Gradle Sync Failed:
- Cek internet connection
- Update Gradle: File → Invalidate Caches
- Cek SDK path di File → Project Structure

### Build Failed:
- Clean project: `gradlew clean`
- Rebuild: `gradlew build`
- Cek error di console log

### APK Not Installing:
- Enable unknown sources di HP
- Pastikan API level compatible
- Sign APK untuk release version

## 📄 Build Signed APK (Production):

1. **Generate Keystore**
   ```bash
   keytool -genkey -v -keystore pg-otomatis.keystore -alias pg-key -keyalg RSA -keysize 2048 -validity 10000
   ```

2. **Configure signing di build.gradle**
   ```gradle
   signingConfigs {
       release {
           storeFile file('pg-otomatis.keystore')
           storePassword 'your-password'
           keyAlias 'pg-key'
           keyPassword 'your-password'
       }
   }
   ```

3. **Build Release APK**
   ```bash
   gradlew assembleRelease
   ```

## 📂 File Locations:
- APK Output: `app/build/outputs/apk/`
- Keystore: `pg-otomatis.keystore`
- Logs: `app/build/logs/`

---
**Setelah install APK di HP, notifikasi dari MyBCA, BRImo, dan Livin' akan otomatis terdeteksi dan dikirim ke Supabase!**