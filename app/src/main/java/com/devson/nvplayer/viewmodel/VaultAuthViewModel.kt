package com.devson.nvplayer.viewmodel

import android.app.Application
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.devson.nvplayer.data.security.VaultBiometricHelper
import com.devson.nvplayer.data.security.VaultFileManager
import com.devson.nvplayer.data.security.VaultSecurityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface VaultAuthState {
    data object SetupPin : VaultAuthState
    data object ConfirmPin : VaultAuthState
    data class SetupSecurityQuestion(val pin: String) : VaultAuthState
    data object EnterPin : VaultAuthState
    data class RestoreExistingVault(val fileCount: Int) : VaultAuthState
    data class AnswerSecurityQuestion(val isFromRestore: Boolean = false) : VaultAuthState
    data object ResetPin : VaultAuthState
    data object ConfirmResetPin : VaultAuthState
    data object Authenticated : VaultAuthState
    data class Error(val message: String) : VaultAuthState
}

class VaultAuthViewModel(
    application: Application,
    val securityManager: VaultSecurityManager,
    private val vaultFileManager: VaultFileManager? = null
) : AndroidViewModel(application) {

    private val _authState = MutableStateFlow<VaultAuthState>(VaultAuthState.EnterPin)
    val authState: StateFlow<VaultAuthState> = _authState.asStateFlow()

    private val _pinDigits = MutableStateFlow("")
    val pinDigits: StateFlow<String> = _pinDigits.asStateFlow()

    private var setupPinTemp: String = ""
    private var resetPinTemp: String = ""

    init {
        checkPinStatus()
    }

    fun checkPinStatus() {
        _pinDigits.value = ""
        setupPinTemp = ""
        resetPinTemp = ""
        if (securityManager.isPinSet()) {
            _authState.value = VaultAuthState.EnterPin
        } else if (securityManager.hasExistingVaultOnDisk()) {
            _authState.value = VaultAuthState.RestoreExistingVault(securityManager.getExistingVaultFileCount())
        } else {
            _authState.value = VaultAuthState.SetupPin
        }
    }

    fun onDigit(digit: String) {
        if (_pinDigits.value.length < 4) {
            _pinDigits.value += digit
            if (_pinDigits.value.length == 4) {
                processCompletedPin(_pinDigits.value)
            }
        }
    }

    fun onBackspace() {
        if (_pinDigits.value.isNotEmpty()) {
            _pinDigits.value = _pinDigits.value.dropLast(1)
        }
    }

    fun onClear() {
        _pinDigits.value = ""
    }

    private fun processCompletedPin(pin: String) {
        viewModelScope.launch {
            when (val state = _authState.value) {
                is VaultAuthState.SetupPin -> {
                    setupPinTemp = pin
                    _pinDigits.value = ""
                    _authState.value = VaultAuthState.ConfirmPin
                }
                is VaultAuthState.ConfirmPin -> {
                    if (pin == setupPinTemp) {
                        _pinDigits.value = ""
                        _authState.value = VaultAuthState.SetupSecurityQuestion(pin)
                    } else {
                        _pinDigits.value = ""
                        _authState.value = VaultAuthState.Error("PINs do not match. Try again.")
                    }
                }
                is VaultAuthState.EnterPin, is VaultAuthState.Error -> {
                    if (securityManager.verifyPin(pin)) {
                        _pinDigits.value = ""
                        withContext(Dispatchers.IO) {
                            vaultFileManager?.rebuildDatabaseFromStorage()
                        }
                        _authState.value = VaultAuthState.Authenticated
                    } else {
                        _pinDigits.value = ""
                        _authState.value = VaultAuthState.Error("Incorrect PIN")
                    }
                }
                is VaultAuthState.RestoreExistingVault -> {
                    if (securityManager.verifyPin(pin)) {
                        _pinDigits.value = ""
                        withContext(Dispatchers.IO) {
                            vaultFileManager?.rebuildDatabaseFromStorage()
                        }
                        _authState.value = VaultAuthState.Authenticated
                    } else {
                        _pinDigits.value = ""
                        _authState.value = VaultAuthState.Error("Incorrect PIN for existing vault.")
                    }
                }
                is VaultAuthState.ResetPin -> {
                    resetPinTemp = pin
                    _pinDigits.value = ""
                    _authState.value = VaultAuthState.ConfirmResetPin
                }
                is VaultAuthState.ConfirmResetPin -> {
                    if (pin == resetPinTemp) {
                        securityManager.setPin(pin)
                        _pinDigits.value = ""
                        withContext(Dispatchers.IO) {
                            vaultFileManager?.rebuildDatabaseFromStorage()
                        }
                        _authState.value = VaultAuthState.Authenticated
                    } else {
                        _pinDigits.value = ""
                        _authState.value = VaultAuthState.Error("New PINs do not match. Try again.")
                    }
                }
                else -> {}
            }
        }
    }

    fun completeSecurityQuestionSetup(question: String, answer: String) {
        val current = _authState.value
        if (current is VaultAuthState.SetupSecurityQuestion) {
            securityManager.setPin(current.pin, question, answer)
            viewModelScope.launch(Dispatchers.IO) {
                vaultFileManager?.rebuildDatabaseFromStorage()
            }
            _pinDigits.value = ""
            _authState.value = VaultAuthState.Authenticated
        }
    }

    fun onForgotPinClicked() {
        val isRestore = _authState.value is VaultAuthState.RestoreExistingVault
        val question = securityManager.getSecurityQuestion()
        if (!question.isNullOrBlank()) {
            _pinDigits.value = ""
            _authState.value = VaultAuthState.AnswerSecurityQuestion(isFromRestore = isRestore)
        } else {
            _authState.value = VaultAuthState.Error("No security question set. Please enter PIN or start a fresh vault.")
        }
    }

    fun verifySecurityAnswerAndProceed(answer: String): Boolean {
        return if (securityManager.verifySecurityAnswer(answer)) {
            _pinDigits.value = ""
            _authState.value = VaultAuthState.ResetPin
            true
        } else {
            false
        }
    }

    fun startFreshVault(deleteExistingFiles: Boolean) {
        securityManager.resetVault(deleteFiles = deleteExistingFiles)
        _pinDigits.value = ""
        setupPinTemp = ""
        resetPinTemp = ""
        _authState.value = VaultAuthState.SetupPin
    }

    fun authenticateWithBiometrics(activity: FragmentActivity) {
        if (!VaultBiometricHelper.canAuthenticate(activity)) {
            return
        }

        VaultBiometricHelper.promptBiometric(
            activity = activity,
            onSuccess = {
                _pinDigits.value = ""
                viewModelScope.launch(Dispatchers.IO) {
                    vaultFileManager?.rebuildDatabaseFromStorage()
                }
                _authState.value = VaultAuthState.Authenticated
            },
            onError = { _, _ -> }
        )
    }

    fun lockVault() {
        checkPinStatus()
    }

    class Factory(
        private val application: Application,
        private val securityManager: VaultSecurityManager,
        private val vaultFileManager: VaultFileManager? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(VaultAuthViewModel::class.java)) {
                return VaultAuthViewModel(application, securityManager, vaultFileManager) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
