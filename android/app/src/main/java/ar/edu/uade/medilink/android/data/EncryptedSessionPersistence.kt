package ar.edu.uade.medilink.android.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.google.gson.Gson
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES-GCM con una clave no exportable de Android Keystore, en almacenamiento privado. */
class EncryptedSessionPersistence(context: Context, storageName: String = "session") : SessionPersistence {
    private val preferences = context.getSharedPreferences(storageName, Context.MODE_PRIVATE)
    private val gson = Gson()
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("medifind.session", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("medifind.session",
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized override fun read(): SavedSession? = try {
        preferences.getString("encrypted", null)?.let { encoded ->
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            require(bytes.size > 12)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            gson.fromJson(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8), SavedSession::class.java)
                ?.takeIf { it.login.accessToken.isNotBlank() && it.login.perfil.rol == "USUARIO" }
        }
    } catch (_: Exception) {
        preferences.edit().remove("encrypted").commit()
        null
    }
    @Synchronized override fun write(value: SavedSession?) {
        if (value == null) {
            check(preferences.edit().remove("encrypted").commit())
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val bytes = cipher.iv + cipher.doFinal(gson.toJson(value).toByteArray(Charsets.UTF_8))
        check(preferences.edit().putString("encrypted", Base64.encodeToString(bytes, Base64.NO_WRAP)).commit())
    }
}
