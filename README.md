README.md

# ModBus Extension for MIT App Inventor
Extension untuk MIT App Inventor yang memungkinkan komunikasi dengan perangkat ModBus (TCP/RTU).

## Persyaratan (Prerequisites)
Sebelum melakukan build, pastikan Anda memiliki:
 * JDK 8 (OpenJDK Temurin 1.8 direkomendasikan).
 * Apache Ant terinstal di sistem Anda.
 * Struktur folder library yang lengkap di dalam folder lib/.

## Struktur Proyek
Pastikan folder lib/ berisi dependensi berikut:
 * lib/appinventor/ (AnnotationProcessors.jar, AndroidRuntime.jar, Component.jar, dx.jar)
 * lib/ant-contrib/ant-contrib-1.0b3.jar
 * lib/deps/ (Library tambahan jika ada)

## Cara Build
 1. Buka terminal atau command prompt di direktori root proyek ini.
 2. Jalankan perintah untuk membersihkan hasil build sebelumnya:
   ant clean
 3. Jalankan perintah untuk mengompilasi dan membuat file .aix:
   ant extensions
 4. Hasil file .aix akan muncul di dalam folder out/.

---

LANGKAH-LANGKAH CLONE DAN BUILD

A. Persiapan Lingkungan
 1. Instal Java 8: Pastikan java -version di terminal menampilkan versi 1.8.
 2. Instal Apache Ant: Pastikan perintah ant sudah terdaftar di Environment Variables (PATH) komputer Anda.

B. Mengunduh (Clone) Proyek
 git clone https://github.com/aos92/ModBus-Extension-Mit-App-Invertor.git
 cd ModBus-Extension-Mit-App-Invertor

C. Menjalankan Build Step-by-Step
 1. Konfigurasi Library: Pastikan folder lib/ berisi semua file .jar yang dibutuhkan sesuai skrip build.xml.
 2. Bersihkan Workspace:
   ant clean
 3. Eksekusi Kompilasi:
   ant extensions
   (Proses ini akan menjalankan urutan: javac, process, jar, dex, dan pack).
 4. Ambil Hasil:
   Setelah muncul tulisan "BUILD SUCCESSFUL", buka folder out/. File .aix Anda siap digunakan.

Catatan: Jika ingin hasil build yang lebih ringkas (obfuscated), gunakan perintah:
ant extensions -Dproguard=1
