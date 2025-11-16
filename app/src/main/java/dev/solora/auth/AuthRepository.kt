package dev.solora.auth

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import dev.solora.data.FirebaseUser as UserInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.io.IOException

private val Context.dataStore by preferencesDataStore(name = "auth")

/**
 * Repository for authentication operations
 * Handles Firebase Auth, user preferences, and biometric authentication setup
 */
class AuthRepository(private val context: Context) {
    private val firebaseAuth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val KEY_USER_ID = stringPreferencesKey("user_id")
    private val KEY_NAME = stringPreferencesKey("name")
    private val KEY_SURNAME = stringPreferencesKey("surname")
    private val KEY_EMAIL = stringPreferencesKey("email")
    private val KEY_HAS_SEEN_ONBOARDING = booleanPreferencesKey("has_seen_onboarding")
    private val KEY_BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
    private val KEY_LAST_LOGIN_TIME = longPreferencesKey("last_login_time")
    private val KEY_STAY_LOGGED_IN = booleanPreferencesKey("stay_logged_in")
    companion object {
        const val SHARED_PREFS_FILENAME = "biometric_prefs"
        const val CIPHERTEXT_WRAPPER = "ciphertext_wrapper"
        const val SECRET_KEY_NAME = "biometric_sample_encryption_key"
    }

    val currentUser: FirebaseUser? get() = firebaseAuth.currentUser
    
    val hasSeenOnboarding: Flow<Boolean> = context.dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }.map { prefs ->
        prefs[KEY_HAS_SEEN_ONBOARDING] ?: false
    }
    
    // Biometric is only enabled if both flag is set AND encrypted data exists
    val isBiometricEnabled: Flow<Boolean> = context.dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }.map { prefs ->
        val flagEnabled = prefs[KEY_BIOMETRIC_ENABLED] ?: false
        val dataExists = getCiphertextWrapperFromSharedPrefs() != null
        flagEnabled && dataExists
    }
    
    val stayLoggedIn: Flow<Boolean> = context.dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }.map { prefs ->
        prefs[KEY_STAY_LOGGED_IN] ?: true 
    }
    
    suspend fun markOnboardingComplete() {
        context.dataStore.edit { prefs ->
            prefs[KEY_HAS_SEEN_ONBOARDING] = true
        }
    }

    suspend fun login(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            val user = result.user ?: throw Exception("Login failed: No user returned")
            
            context.dataStore.edit { prefs ->
                prefs[KEY_USER_ID] = user.uid
                prefs[KEY_EMAIL] = user.email ?: email
                prefs[KEY_NAME] = user.displayName ?: email.substringBefore('@')
                prefs[KEY_HAS_SEEN_ONBOARDING] = true  // User has logged in, skip onboarding next time
                prefs[KEY_LAST_LOGIN_TIME] = System.currentTimeMillis()
            }
            
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loginWithGoogle(idToken: String): Result<FirebaseUser> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = firebaseAuth.signInWithCredential(credential).await()
            val user = authResult.user ?: throw Exception("Google login failed")

            context.dataStore.edit { prefs ->
                prefs[KEY_USER_ID] = user.uid
                prefs[KEY_NAME] = user.displayName ?: ""
                prefs[KEY_EMAIL] = user.email ?: ""
                prefs[KEY_HAS_SEEN_ONBOARDING] = true  // User has logged in, skip onboarding next time
                prefs[KEY_LAST_LOGIN_TIME] = System.currentTimeMillis()
            }

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Register new user with email/password
     * Creates Firebase Auth user and stores profile in Firestore
     */
    suspend fun register(name: String, surname: String, email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user ?: throw Exception("Registration failed: No user returned")
            
            // Update Firebase Auth profile with display name
            val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                .setDisplayName(name)
                .build()
            user.updateProfile(profileUpdates).await()
            
            // Store additional user data in Firestore
            val userDoc = hashMapOf(
                "name" to name,
                "surname" to surname,
                "email" to email,
                "createdAt" to com.google.firebase.Timestamp.now()
            )
            firestore.collection("users").document(user.uid).set(userDoc).await()
            
            context.dataStore.edit { prefs ->
                prefs[KEY_USER_ID] = user.uid
                prefs[KEY_NAME] = name
                prefs[KEY_SURNAME] = surname
                prefs[KEY_EMAIL] = email
                prefs[KEY_HAS_SEEN_ONBOARDING] = true  // User has registered, skip onboarding next time
            }
            
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun registerWithGoogle(idToken: String): Result<FirebaseUser> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = firebaseAuth.signInWithCredential(credential).await()
            val user = authResult.user ?: throw Exception("Google sign-in failed")

            val fullName = user.displayName ?: ""
            val nameParts = fullName.trim().split(" ")
            val firstName = nameParts.getOrNull(0) ?: ""
            val lastName = if (nameParts.size > 1) nameParts.subList(1, nameParts.size).joinToString(" ") else ""

            val userDoc = hashMapOf(
                "name" to firstName,
                "surname" to lastName,
                "email" to (user.email ?: "")
            )

            firestore.collection("users").document(user.uid).set(userDoc).await()

            context.dataStore.edit { prefs ->
                prefs[KEY_USER_ID] = user.uid
                prefs[KEY_NAME] = user.displayName ?: ""
                prefs[KEY_SURNAME] = "" // no surname from Google
                prefs[KEY_EMAIL] = user.email ?: ""
                prefs[KEY_HAS_SEEN_ONBOARDING] = true  // User has registered, skip onboarding next time
            }

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getCurrentUserInfo(): Flow<UserInfo?> {
        return context.dataStore.data.catch { e -> 
            if (e is IOException) emit(emptyPreferences()) else throw e 
        }.map { prefs ->
            val userId = prefs[KEY_USER_ID]
            val name = prefs[KEY_NAME]
            val surname = prefs[KEY_SURNAME]
            val email = prefs[KEY_EMAIL]
            
            if (!userId.isNullOrEmpty() && !name.isNullOrEmpty() && !surname.isNullOrEmpty() && !email.isNullOrEmpty()) {
                UserInfo(userId, name, surname, email)
            } else null
        }
    }

    suspend fun getFirebaseIdToken(): String? {
        return try {
            firebaseAuth.currentUser?.getIdToken(false)?.await()?.token
        } catch (e: Exception) {
            null
        }
    }
    
    suspend fun logout(): Result<Unit> {
        return try {
            context.dataStore.edit { prefs ->
                prefs.clear()
                prefs[KEY_HAS_SEEN_ONBOARDING] = true
            }
            
            clearBiometricData()
            firebaseAuth.signOut()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private suspend fun clearBiometricData() {
        try {
            val sharedPrefs = context.getSharedPreferences(SHARED_PREFS_FILENAME, Context.MODE_PRIVATE)
            sharedPrefs.edit().remove(CIPHERTEXT_WRAPPER).apply()
            
            context.dataStore.edit { prefs ->
                prefs[KEY_BIOMETRIC_ENABLED] = false
            }
        } catch (e: Exception) {
            // Ignore errors when clearing biometric data
        }
    }
    
    fun canAuthenticateWithBiometrics(): Int {
        val biometricManager = BiometricManager.from(context)
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
    }
    
    suspend fun enableBiometric(): Result<Unit> {
        return try {
            context.dataStore.edit { prefs ->
                prefs[KEY_BIOMETRIC_ENABLED] = true
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun disableBiometric(): Result<Unit> {
        return try {
            context.dataStore.edit { prefs ->
                prefs[KEY_BIOMETRIC_ENABLED] = false
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun getCiphertextWrapperFromSharedPrefs(): CiphertextWrapper? {
        val cryptographyManager = CryptographyManager()
        return cryptographyManager.getCiphertextWrapperFromSharedPrefs(
            context,
            SHARED_PREFS_FILENAME,
            Context.MODE_PRIVATE,
            CIPHERTEXT_WRAPPER
        )
    }
    
    fun storeCiphertextWrapperToSharedPrefs(ciphertextWrapper: CiphertextWrapper) {
        val cryptographyManager = CryptographyManager()
        cryptographyManager.persistCiphertextWrapperToSharedPrefs(
            ciphertextWrapper,
            context,
            SHARED_PREFS_FILENAME,
            Context.MODE_PRIVATE,
            CIPHERTEXT_WRAPPER
        )
    }
    
    suspend fun storeBiometricToken(): Result<String> {
        return try {
            val user = firebaseAuth.currentUser
            val email = user?.email ?: return Result.failure(Exception("No authenticated user"))
            Result.success(email)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Verify biometric authentication by comparing stored email with current user
     * Clears biometric data if mismatch detected (security measure)
     */
    suspend fun authenticateWithStoredData(storedData: String): Result<String> {
        return try {
            val currentUser = firebaseAuth.currentUser
            if (currentUser != null && currentUser.email == storedData) {
                updateLastLoginTime()
                Result.success("User authenticated via biometric")
            } else {
                // Security: clear biometric data if user doesn't match
                clearBiometricData()
                Result.failure(Exception("Biometric data mismatch - cleared for security"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun updateLastLoginTime() {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_LOGIN_TIME] = System.currentTimeMillis()
        }
    }
    
    suspend fun setStayLoggedIn(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_STAY_LOGGED_IN] = enabled
        }
    }
    
    /**
     * Check if user should be auto-logged out
     * Returns true if user hasn't logged in for 30 days and "stay logged in" is disabled
     */
    suspend fun shouldAutoLogout(): Boolean {
        val stayLoggedIn = stayLoggedIn.first()
        if (stayLoggedIn) return false
        
        val dataStorePrefs = context.dataStore.data.first()
        val lastLoginTime = dataStorePrefs[KEY_LAST_LOGIN_TIME] ?: return false
        
        // Auto-logout after 30 days of inactivity
        val thirtyDaysInMillis = 30 * 24 * 60 * 60 * 1000L
        val currentTime = System.currentTimeMillis()
        
        return (currentTime - lastLoginTime) > thirtyDaysInMillis
    }
    
    fun isUserLoggedIn(): Boolean {
        return firebaseAuth.currentUser != null
    }
}
