package bankwiser.bankpromotion.material.crypto

import android.util.Log
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val TAG = "FileEncryptionHelper"
private const val BUFFER_SIZE = 8192 // 8KB buffer

class FileEncryptionHelper(private val keyStoreHelper: KeyStoreHelper) {

    private fun getCipher(mode: Int, iv: ByteArray): Cipher {
        val secretKey: SecretKey = keyStoreHelper.getOrCreateAESKey()
        val cipher = Cipher.getInstance(EncryptionConstants.AES_TRANSFORMATION)
        val gcmParamSpec = GCMParameterSpec(EncryptionConstants.GCM_TAG_LENGTH_BITS, iv)
        cipher.init(mode, secretKey, gcmParamSpec)
        return cipher
    }

    fun encrypt(inputStream: InputStream, outputStream: OutputStream): Boolean {
        return try {
            val iv = ByteArray(EncryptionConstants.GCM_IV_LENGTH_BYTES)
            SecureRandom().nextBytes(iv) // Generate a random IV for each encryption
            outputStream.write(iv) // Prepend IV to the encrypted data

            val cipher = getCipher(Cipher.ENCRYPT_MODE, iv)
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                val encryptedBytes = cipher.update(buffer, 0, bytesRead)
                if (encryptedBytes != null) {
                    outputStream.write(encryptedBytes)
                }
            }
            val finalEncryptedBytes = cipher.doFinal()
            if (finalEncryptedBytes != null) {
                outputStream.write(finalEncryptedBytes)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Encryption failed", e)
            false
        } finally {
            try { inputStream.close() } catch (e: Exception) { /* ignore */ }
            try { outputStream.close() } catch (e: Exception) { /* ignore */ }
        }
    }

    fun decrypt(inputStream: InputStream, outputStream: OutputStream): Boolean {
        return try {
            val iv = ByteArray(EncryptionConstants.GCM_IV_LENGTH_BYTES)
            val ivRead = inputStream.read(iv) // Read the prepended IV
            if (ivRead != EncryptionConstants.GCM_IV_LENGTH_BYTES) {
                Log.e(TAG, "Decryption failed: Could not read full IV.")
                return false
            }

            val cipher = getCipher(Cipher.DECRYPT_MODE, iv)
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                val decryptedBytes = cipher.update(buffer, 0, bytesRead)
                if (decryptedBytes != null) {
                    outputStream.write(decryptedBytes)
                }
            }
            val finalDecryptedBytes = cipher.doFinal()
            if (finalDecryptedBytes != null) {
                outputStream.write(finalDecryptedBytes)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Decryption failed", e)
            // This can happen due to wrong key, corrupted data, or incorrect IV
            false
        } finally {
            try { inputStream.close() } catch (e: Exception) { /* ignore */ }
            try { outputStream.close() } catch (e: Exception) { /* ignore */ }
        }
    }
}
