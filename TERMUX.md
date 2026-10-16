# Menggunakan VYRO dari Android / Termux

## Edit project
Pasang Termux dari sumber tepercaya, lalu:
```bash
pkg update && pkg upgrade
pkg install git nano unzip
```
Ekstrak ZIP ke folder yang mudah ditemukan. Gunakan editor Android atau `nano` untuk mengubah Kotlin dan SQL.

## Build APK
Paling mudah dan paling stabil: upload folder ke GitHub private repository, buka project dengan Android Studio di komputer, lalu Build APK.
Termux bukan lingkungan Android Studio resmi dan build native membutuhkan Android SDK, platform-tools, build-tools, JDK, serta konfigurasi Gradle yang cocok. Tidak ada jaminan semua perangkat Android dapat membangun APK dengan lancar.

Jangan menjalankan `gradle` tanpa SDK lengkap dan menganggap error berarti kode aplikasi rusak; sering kali SDK/Java belum cocok.
