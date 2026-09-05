package com.devson.nvplayer.data.security

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

class VaultSecurityManager(private val context: Context) {

    private val masterKey: MasterKey = MasterKey.Builder(context.applicationContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val securePrefs: SharedPreferences = try {
        EncryptedSharedPreferences.create(
            context.applicationContext,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        context.applicationContext.deleteSharedPreferences(PREFS_FILE_NAME)
        EncryptedSharedPreferences.create(
            context.applicationContext,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    val persistentVaultDirectory: File by lazy {
        val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val vaultDir = File(docsDir, "NosvedPlayer/.vault_secure_media")
        if (!vaultDir.exists()) {
            vaultDir.mkdirs()
        }
        val nomedia = File(vaultDir, ".nomedia")
        if (!nomedia.exists()) {
            try { nomedia.createNewFile() } catch (_: Exception) {}
        }
        vaultDir
    }

    private val vaultConfigFile: File
        get() = File(persistentVaultDirectory, ".vault_config")

    fun isPinSet(): Boolean {
        return securePrefs.getString(KEY_VAULT_PIN_HASH, null)?.isNotEmpty() == true
    }

    fun hasExistingVaultOnDisk(): Boolean {
        val files = persistentVaultDirectory.listFiles { file -> file.extension == "vlt" }
        return files != null && files.isNotEmpty()
    }

    fun getExistingVaultFileCount(): Int {
        val files = persistentVaultDirectory.listFiles { file -> file.extension == "vlt" }
        return files?.size ?: 0
    }

    fun setPin(pin: String, question: String = "", answer: String = "") {
        val pinHash = hashPin(pin)
        val answerHash = if (answer.isNotBlank()) hashSecurityAnswer(answer) else ""
        
        securePrefs.edit()
            .putString(KEY_VAULT_PIN_HASH, pinHash)
            .apply()

        if (question.isNotBlank()) {
            securePrefs.edit()
                .putString(KEY_VAULT_SECURITY_QUESTION, question)
                .putString(KEY_VAULT_SECURITY_ANSWER_HASH, answerHash)
                .apply()
        }

        saveToDiskConfig(pinHash, question, answerHash)
    }

    fun verifyPin(pin: String): Boolean {
        val storedHash = securePrefs.getString(KEY_VAULT_PIN_HASH, null)
            ?: readDiskPinHash()
            ?: return false
        val inputHash = hashPin(pin)
        val matches = storedHash == inputHash
        if (matches && !securePrefs.contains(KEY_VAULT_PIN_HASH)) {
            securePrefs.edit().putString(KEY_VAULT_PIN_HASH, storedHash).apply()
            val diskQ = readDiskSecurityQuestion()
            val diskA = readDiskSecurityAnswerHash()
            if (!diskQ.isNullOrBlank()) {
                securePrefs.edit()
                    .putString(KEY_VAULT_SECURITY_QUESTION, diskQ)
                    .putString(KEY_VAULT_SECURITY_ANSWER_HASH, diskA)
                    .apply()
            }
        }
        return matches
    }

    fun updatePin(oldPin: String, newPin: String): Boolean {
        if (!verifyPin(oldPin)) return false
        val question = getSecurityQuestion() ?: ""
        val answerHash = securePrefs.getString(KEY_VAULT_SECURITY_ANSWER_HASH, "") ?: readDiskSecurityAnswerHash() ?: ""
        val newPinHash = hashPin(newPin)
        securePrefs.edit().putString(KEY_VAULT_PIN_HASH, newPinHash).apply()
        saveToDiskConfig(newPinHash, question, answerHash)
        return true
    }

    fun getSecurityQuestion(): String? {
        return securePrefs.getString(KEY_VAULT_SECURITY_QUESTION, null)
            ?: readDiskSecurityQuestion()
    }

    fun verifySecurityAnswer(answer: String): Boolean {
        val storedHash = securePrefs.getString(KEY_VAULT_SECURITY_ANSWER_HASH, null)
            ?: readDiskSecurityAnswerHash()
            ?: return false
        val inputHash = hashSecurityAnswer(answer)
        return storedHash == inputHash
    }

    fun resetPinWithSecurityAnswer(answer: String, newPin: String): Boolean {
        if (!verifySecurityAnswer(answer)) return false
        val question = getSecurityQuestion() ?: ""
        val answerHash = hashSecurityAnswer(answer)
        val newPinHash = hashPin(newPin)
        
        securePrefs.edit()
            .putString(KEY_VAULT_PIN_HASH, newPinHash)
            .putString(KEY_VAULT_SECURITY_QUESTION, question)
            .putString(KEY_VAULT_SECURITY_ANSWER_HASH, answerHash)
            .apply()

        saveToDiskConfig(newPinHash, question, answerHash)
        return true
    }

    fun resetVault(deleteFiles: Boolean) {
        securePrefs.edit().clear().apply()
        if (vaultConfigFile.exists()) {
            try { vaultConfigFile.delete() } catch (_: Exception) {}
        }
        if (deleteFiles && persistentVaultDirectory.exists()) {
            persistentVaultDirectory.listFiles()?.forEach { file ->
                try { file.delete() } catch (_: Exception) {}
            }
        }
    }

    fun isBiometricEnabled(): Boolean {
        return securePrefs.getBoolean(KEY_BIOMETRIC_ENABLED, true)
    }

    fun setBiometricEnabled(enabled: Boolean) {
        securePrefs.edit()
            .putBoolean(KEY_BIOMETRIC_ENABLED, enabled)
            .apply()
    }

    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(("NOSVED_PIN_SALT_2026_" + pin).toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hashSecurityAnswer(answer: String): String {
        val normalized = answer.trim().lowercase()
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(("NOSVED_SEC_ANSWER_SALT_" + normalized).toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun saveToDiskConfig(pinHash: String, question: String, answerHash: String) {
        try {
            val json = JSONObject().apply {
                put("pinHash", pinHash)
                put("question", question)
                put("answerHash", answerHash)
                put("timestamp", System.currentTimeMillis())
            }
            vaultConfigFile.writeText(json.toString(), Charsets.UTF_8)
        } catch (_: Exception) {}
    }

    private fun readDiskPinHash(): String? {
        if (!vaultConfigFile.exists()) return null
        return try {
            val json = JSONObject(vaultConfigFile.readText(Charsets.UTF_8))
            json.optString("pinHash").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun readDiskSecurityQuestion(): String? {
        if (!vaultConfigFile.exists()) return null
        return try {
            val json = JSONObject(vaultConfigFile.readText(Charsets.UTF_8))
            json.optString("question").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun readDiskSecurityAnswerHash(): String? {
        if (!vaultConfigFile.exists()) return null
        return try {
            val json = JSONObject(vaultConfigFile.readText(Charsets.UTF_8))
            json.optString("answerHash").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val PREFS_FILE_NAME = "secure_vault_prefs"
        private const val KEY_VAULT_PIN_HASH = "vault_pin_hash"
        private const val KEY_VAULT_SECURITY_QUESTION = "vault_sec_question"
        private const val KEY_VAULT_SECURITY_ANSWER_HASH = "vault_sec_answer_hash"
        private const val KEY_BIOMETRIC_ENABLED = "vault_biometric_enabled"

        val DEFAULT_SECURITY_QUESTIONS = listOf(
            "What is your birthplace?",
            "What was the name of your first pet?",
            "What is your favorite movie?",
            "What is your mother's maiden name?",
            "What was your first school name?"
        )
    }
}
