package sound.recorder.widget.encrypt

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.annotation.RequiresApi
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * CryptoManager — Enkripsi & Dekripsi aman menggunakan:
 *   - AES/GCM/NoPadding (authenticated encryption)
 *   - Android Keystore System untuk API >= 23 (hardware-backed)
 *   - SharedPreferences fallback untuk API 22 (software key)
 *   - IV (Initialization Vector) 12 byte unik per enkripsi
 *   - GCM Auth Tag 128 bit
 *
 * Penggunaan:
 *   val crypto = CryptoManager(context)
 *   val cipherText = crypto.encrypt("data rahasia")
 *   val plainText  = crypto.decrypt(cipherText)
 */
class CryptoManager(private val context: Context) {

    companion object {
        private const val KEY_ALIAS         = "MyAppSecureKey"
        private const val ANDROID_KEYSTORE  = "AndroidKeyStore"
        private const val TRANSFORMATION     = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH    = 128          // bit
        private const val IV_SIZE_BYTES     = 12           // byte (96 bit — standar GCM)
    }

    // ─────────────────────────────────────────────────────────────
    //  Public API
    // ─────────────────────────────────────────────────────────────

    /**
     * Enkripsi plaintext → Base64 string (IV + ciphertext digabung).
     * @param plainText String yang ingin dienkripsi
     * @return Base64-encoded string berisi IV (12 byte) + ciphertext
     */
    fun encrypt(plainText: String): String {
        val cipher = getCipher()
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())

        val iv         = cipher.iv                                      // 12 byte
        val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        // Gabungkan IV + ciphertext agar dekripsi bisa ambil IV kembali
        val combined = ByteArray(iv.size + cipherBytes.size)
        System.arraycopy(iv,          0, combined, 0,       iv.size)
        System.arraycopy(cipherBytes, 0, combined, iv.size, cipherBytes.size)

        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * Dekripsi Base64 string hasil [encrypt] → plaintext asli.
     * @param encryptedData Base64 string yang dikembalikan oleh [encrypt]
     * @return String plaintext asli
     */
    fun decrypt(encryptedData: String): String {
        val combined    = Base64.decode(encryptedData, Base64.NO_WRAP)

        // Pisahkan IV dan ciphertext
        val iv          = combined.copyOfRange(0, IV_SIZE_BYTES)
        val cipherBytes = combined.copyOfRange(IV_SIZE_BYTES, combined.size)

        val cipher = getCipher()
        val spec   = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), spec)

        val plainBytes = cipher.doFinal(cipherBytes)
        return String(plainBytes, Charsets.UTF_8)
    }

    /**
     * Hapus kunci dari Keystore/SharedPreferences (opsional — misalnya saat logout).
     */
    fun deleteKey() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS)
            }
        } else {
            context.getSharedPreferences("_crypto_ks", Context.MODE_PRIVATE)
                .edit().remove(KEY_ALIAS).apply()
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Private Helpers
    // ─────────────────────────────────────────────────────────────

    /** Ambil kunci; delegasikan ke Keystore (API 23+) atau SharedPreferences (API 22). */
    private fun getOrCreateKey(): SecretKey {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getOrCreateKeyFromKeystore()
        } else {
            getOrCreateKeyFromPrefs()
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun getOrCreateKeyFromKeystore(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        keyStore.getKey(KEY_ALIAS, null)?.let { return it as SecretKey }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    // API 22 fallback: kunci AES-256 dibuat secara software, disimpan di SharedPreferences.
    // Tidak hardware-backed, namun fungsional untuk perangkat lama yang sangat jarang.
    @SuppressLint("ApplySharedPref")
    private fun getOrCreateKeyFromPrefs(): SecretKey {
        val prefs = context.getSharedPreferences("_crypto_ks", Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_ALIAS, null)
        if (stored != null) {
            val keyBytes = Base64.decode(stored, Base64.NO_WRAP)
            return SecretKeySpec(keyBytes, "AES")
        }

        val keyGenerator = KeyGenerator.getInstance("AES").apply { init(256) }
        val secretKey = keyGenerator.generateKey()
        prefs.edit()
            .putString(KEY_ALIAS, Base64.encodeToString(secretKey.encoded, Base64.NO_WRAP))
            .commit()
        return secretKey
    }

    /** Buat instance Cipher dengan transformasi AES/GCM/NoPadding. */
    private fun getCipher(): Cipher = Cipher.getInstance(TRANSFORMATION)
}