package sound.recorder.widget.encrypt

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.annotation.RequiresApi
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * CryptoManager — Enkripsi & Dekripsi AES/GCM/NoPadding.
 *
 * Dua mode:
 *  - staticKey != null → key statis (sama di semua device, cross-device compatible)
 *  - staticKey == null → Android Keystore (hardware-backed, device-specific)
 *
 * Gunakan mode statis untuk data yang perlu di-share lintas device (misal fcmKey).
 * Gunakan mode Keystore untuk data lokal yang tidak pernah berpindah device.
 */
class CryptoManager(private val context: Context, private val staticKey: String? = null) {

    companion object {
        private const val KEY_ALIAS        = "MyAppSecureKey"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION    = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH   = 128
        private const val IV_SIZE_BYTES    = 12
    }

    fun encrypt(plainText: String): String {
        val cipher = getCipher()
        cipher.init(Cipher.ENCRYPT_MODE, getKey())

        val iv          = cipher.iv
        val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        val combined = ByteArray(iv.size + cipherBytes.size)
        System.arraycopy(iv,          0, combined, 0,       iv.size)
        System.arraycopy(cipherBytes, 0, combined, iv.size, cipherBytes.size)

        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decrypt(encryptedData: String): String {
        val combined    = Base64.decode(encryptedData, Base64.NO_WRAP)
        val iv          = combined.copyOfRange(0, IV_SIZE_BYTES)
        val cipherBytes = combined.copyOfRange(IV_SIZE_BYTES, combined.size)

        val cipher = getCipher()
        cipher.init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(GCM_TAG_LENGTH, iv))

        return String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
    }

    fun deleteKey() {
        if (staticKey != null) return // static key tidak disimpan di Keystore
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
        } else {
            context.getSharedPreferences("_crypto_ks", Context.MODE_PRIVATE)
                .edit().remove(KEY_ALIAS).apply()
        }
    }

    private fun getKey(): SecretKey {
        if (staticKey != null) return deriveStaticKey(staticKey)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getOrCreateKeyFromKeystore()
        } else {
            getOrCreateKeyFromPrefs()
        }
    }

    /** Derive AES-256 key dari passphrase — deterministik, sama di semua device. */
    private fun deriveStaticKey(passphrase: String): SecretKey {
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(passphrase.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun getOrCreateKeyFromKeystore(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        keyStore.getKey(KEY_ALIAS, null)?.let { return it as SecretKey }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return keyGenerator.generateKey()
    }

    @SuppressLint("ApplySharedPref")
    private fun getOrCreateKeyFromPrefs(): SecretKey {
        val prefs  = context.getSharedPreferences("_crypto_ks", Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_ALIAS, null)
        if (stored != null) {
            return SecretKeySpec(Base64.decode(stored, Base64.NO_WRAP), "AES")
        }
        val secretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        prefs.edit()
            .putString(KEY_ALIAS, Base64.encodeToString(secretKey.encoded, Base64.NO_WRAP))
            .commit()
        return secretKey
    }

    private fun getCipher(): Cipher = Cipher.getInstance(TRANSFORMATION)
}
