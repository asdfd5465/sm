package bankwiser.bankpromotion.material.crypto

object EncryptionConstants {
    const val AES_KEY_ALIAS = "BankWiserAudioAESKey"
    const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
    const val AES_TRANSFORMATION = "AES/GCM/NoPadding" // Recommended for security
    const val AES_KEY_SIZE_BITS = 256
    const val GCM_IV_LENGTH_BYTES = 12 // GCM recommended IV size
    const val GCM_TAG_LENGTH_BITS = 128 // GCM recommended auth tag size
}
