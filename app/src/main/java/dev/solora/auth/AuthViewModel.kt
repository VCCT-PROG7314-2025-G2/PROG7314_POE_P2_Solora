package dev.solora.auth

import android.app.Application
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import dev.solora.data.FirebaseUser as UserInfo

/**
 * ViewModel for authentication operations
 * Manages user login, registration, biometric authentication, and onboarding state
 */
class AuthViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AuthRepository(app.applicationContext)
    
    // Observable state flows for UI
    val hasSeenOnboarding = repo.hasSeenOnboarding.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val userInfo = repo.getCurrentUserInfo().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null as UserInfo?)
    val isBiometricEnabled = repo.isBiometricEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    
    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()
    
    private val _biometricState = MutableStateFlow<BiometricState>(BiometricState.Idle)
    val biometricState: StateFlow<BiometricState> = _biometricState.asStateFlow()
    
    fun markOnboardingComplete() {
        viewModelScope.launch {
            repo.markOnboardingComplete()
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = repo.login(email, password)
            _authState.value = if (result.isSuccess) {
                AuthState.Success("Login successful")
            } else {
                AuthState.Error(result.exceptionOrNull()?.message ?: "Login failed")
            }
        }
    }

    fun loginWithGoogle(idToken: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = repo.loginWithGoogle(idToken)
            _authState.value = if (result.isSuccess) {
                AuthState.Success("Google login successful")
            } else {
                AuthState.Error(result.exceptionOrNull()?.message ?: "Google login failed")
            }
        }
    }

    fun register(name: String, surname: String, email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = repo.register(name, surname, email, password)
            _authState.value = if (result.isSuccess) {
                AuthState.Success("Registration successful")
            } else {
                AuthState.Error(result.exceptionOrNull()?.message ?: "Registration failed")
            }
        }
    }

    fun registerWithGoogle(idToken: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = repo.registerWithGoogle(idToken)
            _authState.value = if (result.isSuccess) {
                AuthState.Success("Google sign-in successful")
            } else {
                AuthState.Error(result.exceptionOrNull()?.message ?: "Google sign-in failed")
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = repo.logout()
            _authState.value = if (result.isSuccess) {
                AuthState.Success("Logged out successfully")
            } else {
                AuthState.Error(result.exceptionOrNull()?.message ?: "Logout failed")
            }
        }
    }

    fun clearAuthState() {
        _authState.value = AuthState.Idle
    }
    // Check if biometric authentication is available
    fun canUseBiometrics(): Boolean {
        val result = repo.canAuthenticateWithBiometrics()
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }
    
    // Get biometric availability status message
    fun getBiometricAvailabilityMessage(): String {
        return when (repo.canAuthenticateWithBiometrics()) {
            BiometricManager.BIOMETRIC_SUCCESS -> "Fingerprint authentication available"
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "No fingerprint hardware available"
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "Fingerprint hardware unavailable"
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "No fingerprint enrolled"
            else -> "Fingerprint authentication not available"
        }
    }
    
    // Enable biometric authentication
    fun enableBiometric() {
        viewModelScope.launch {
            _biometricState.value = BiometricState.Loading
            val result = repo.enableBiometric()
            _biometricState.value = if (result.isSuccess) {
                BiometricState.Success("Biometric authentication enabled")
            } else {
                BiometricState.Error(result.exceptionOrNull()?.message ?: "Failed to enable biometric")
            }
        }
    }
    
    // Disable biometric authentication
    fun disableBiometric() {
        viewModelScope.launch {
            _biometricState.value = BiometricState.Loading
            val result = repo.disableBiometric()
            _biometricState.value = if (result.isSuccess) {
                BiometricState.Success("Biometric authentication disabled")
            } else {
                BiometricState.Error(result.exceptionOrNull()?.message ?: "Failed to disable biometric")
            }
        }
    }
    
    /**
     * Authenticate user using biometric (fingerprint/face)
     * If biometric data exists, decrypts and authenticates
     * If not, sets up new biometric authentication
     */
    fun authenticateWithBiometrics(activity: FragmentActivity) {
        if (!canUseBiometrics()) {
            _biometricState.value = BiometricState.Error("Fingerprint not available")
            return
        }
        
        _biometricState.value = BiometricState.PromptShowing
        val cryptographyManager = CryptographyManager()
        val ciphertextWrapper = repo.getCiphertextWrapperFromSharedPrefs()
        
        // If encrypted token exists, decrypt and authenticate
        if (ciphertextWrapper != null) {
            val cipher = cryptographyManager.getInitializedCipherForDecryption(
                AuthRepository.SECRET_KEY_NAME,
                ciphertextWrapper.initializationVector
            )
            val biometricPrompt = BiometricPromptUtils.createBiometricPrompt(activity) { authResult ->
                authResult.cryptoObject?.cipher?.let { 
                    try {
                        val decryptedData = cryptographyManager.decryptData(ciphertextWrapper.ciphertext, it)
                        viewModelScope.launch {
                            val result = repo.authenticateWithStoredData(decryptedData)
                            if (result.isSuccess) {
                                _biometricState.value = BiometricState.Success("Login successful")
                                _authState.value = AuthState.Success("Fingerprint login successful")
                            } else {
                                _biometricState.value = BiometricState.Error("Authentication failed")
                            }
                        }
                    } catch (e: Exception) {
                        _biometricState.value = BiometricState.Error("Authentication failed")
                    }
                } ?: run {
                    _biometricState.value = BiometricState.Error("Authentication failed")
                }
            }
            val promptInfo = BiometricPromptUtils.createPromptInfo(activity)
            biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
        } else {
            // First-time setup: encrypt and store user token
            val cipher = cryptographyManager.getInitializedCipherForEncryption(AuthRepository.SECRET_KEY_NAME)
            val biometricPrompt = BiometricPromptUtils.createBiometricPrompt(activity) { authResult ->
                authResult.cryptoObject?.cipher?.let { 
                    viewModelScope.launch {
                        try {
                            val tokenResult = repo.storeBiometricToken()
                            val token = tokenResult.getOrNull() ?: "solora_user_token"
                            val encryptedToken = cryptographyManager.encryptData(token, it)
                            repo.storeCiphertextWrapperToSharedPrefs(encryptedToken)
                            repo.enableBiometric()
                            _biometricState.value = BiometricState.Success("Fingerprint setup complete")
                        } catch (e: Exception) {
                            _biometricState.value = BiometricState.Error("Setup failed")
                        }
                    }
                } ?: run {
                    _biometricState.value = BiometricState.Error("Setup failed")
                }
            }
            val promptInfo = BiometricPromptUtils.createPromptInfo(activity)
            biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
        }
    }
    
    fun clearBiometricState() {
        _biometricState.value = BiometricState.Idle
    }
    
    fun setStayLoggedIn(enabled: Boolean) {
        viewModelScope.launch {
            repo.setStayLoggedIn(enabled)
        }
    }
    
    fun getStayLoggedInPreference(callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            val stayLoggedIn = repo.stayLoggedIn.first()
            callback(stayLoggedIn)
        }
    }
    
    fun isUserLoggedIn(): Boolean {
        return repo.isUserLoggedIn()
    }
}

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val message: String) : AuthState()
    data class Error(val message: String) : AuthState()
}

sealed class BiometricState {
    object Idle : BiometricState()
    object Loading : BiometricState()
    data class Success(val message: String) : BiometricState()
    data class Error(val message: String) : BiometricState()
    object PromptShowing : BiometricState()
}
