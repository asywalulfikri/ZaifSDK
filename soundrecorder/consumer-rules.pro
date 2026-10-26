# jump3r: LameEncoder (tidak dipakai SDK) mereferensikan javax.sound yang tidak ada di Android
-dontwarn javax.sound.sampled.**

# okhttp3 (dibawa Picasso) mereferensikan Conscrypt secara opsional; tidak dipakai jika tidak ada di classpath
-dontwarn org.conscrypt.**
