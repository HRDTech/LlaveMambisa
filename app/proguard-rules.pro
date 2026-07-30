# ============================================================
# ProGuard Rules - Llave Mambisa
# ============================================================

# Preservar números de línea en stack traces de release
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# -------- Tink (criptografía) --------
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# -------- Room (base de datos) --------
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-dontwarn androidx.room.**

# -------- Kotlin / Coroutines --------
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-keepclassmembernames class kotlinx.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# -------- Android Keystore / Biometría --------
-keep class android.security.keystore.** { *; }
-keep class androidx.biometric.** { *; }

# -------- ML Kit (QR scanning) --------
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# -------- Clases de la app que usan reflexión --------
-keep class com.solucioneshr.llavemambisa.domain.model.** { *; }
-keep class com.solucioneshr.llavemambisa.crypto.** { *; }
-keep class com.solucioneshr.llavemambisa.data.local.entity.** { *; }

# -------- Application class --------
-keep public class com.solucioneshr.llavemambisa.EncryptedSmsApp

# -------- BroadcastReceivers --------
-keep class com.solucioneshr.llavemambisa.sms.SmsReceiver
-keep class com.solucioneshr.llavemambisa.sms.SmsStatusReceiver

# -------- Enums --------
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# -------- Serialización JSON --------
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# -------- Suprimir advertencias innecesarias --------
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
