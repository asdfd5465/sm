package bankwiser.bankpromotion.material.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

private const val TAG = "KeyStoreHelper"

class KeyStoreHelper {

    private val keyStore: KeyStore = KeyStore.getInstance(EncryptionConstants.ANDROID_KEYSTORE_PROVIDER).apply {
        load(null)
    }

    fun getOrCreateAESKey(): SecretKey {
        return try {
            val existingKey = keyStore.getKey(EncryptionConstants.AES_KEY_ALIAS, null) as? SecretKey
            existingKey ?: generateNewAESKey()
        } catch (e: Exception) {
            Log.e(TAG, "Error accessing or creating AES key", e)
            // Fallback or rethrow - for critical encryption, rethrow might be better
            // For this example, we'll try to generate a new one if retrieval fails badly
            generateNewAESKey()
        }
    }

    private fun generateNewAESKey(): SecretKey {
        Log.i(TAG, "Generating new AES key with alias: ${EncryptionConstants.AES_KEY_ALIAS}")
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            EncryptionConstants.ANDROID_KEYSTORE_PROVIDER
        )
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            EncryptionConstants.AES_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(EncryptionConstants.AES_KEY_SIZE_BITS)
            // .setUserAuthenticationRequired(true) // Example: require user auth for key use
            // .setUserAuthenticationValidityDurationSeconds(30) // If auth required
            .build()
        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }

    // In case you need to retrieve the key as raw bytes (use with caution)
    // For direct use with Cipher, passing the SecretKey object is preferred.
    fun getAESKeyAsBytes(): ByteArray? {
        return try {
            val secretKey = keyStore.getKey(EncryptionConstants.AES_KEY_ALIAS, null) as? SecretKey
            secretKey?.encoded // This might be null or throw exception if key is hardware-backed and not extractable
        } catch (e: Exception) {
            Log.e(TAG, "Error getting AES key bytes", e)
            null
        }
    }
}
