# EXAM BROWSER SD

Aplikasi Android untuk pelaksanaan ujian berbasis Google Forms melalui jaringan lokal sekolah.

## Fitur rancangan
- Mode HOST untuk membuat sesi ujian, mengatur durasi, menerima peserta, membagikan tautan Google Form, serta memulai dan menghentikan ujian.
- Mode PESERTA untuk bergabung menggunakan kode sesi dan mengerjakan formulir melalui WebView.
- Workflow GitHub Actions untuk membangun APK debug dan mengunggahnya sebagai artifact.

## Status
Proyek sedang dibangun. APK belum tersedia sampai workflow Android berhasil dijalankan.

## Catatan penting tentang penguncian Android
Aplikasi Android biasa tidak dapat menjamin peserta sama sekali tidak berpindah ke aplikasi lain. Penguncian yang kuat memerlukan perangkat yang diprovisikan sebagai Android Device Owner/dedicated device dan penggunaan Lock Task Mode. Implementasi ini harus diuji pada perangkat sekolah sebelum digunakan untuk ujian resmi.
