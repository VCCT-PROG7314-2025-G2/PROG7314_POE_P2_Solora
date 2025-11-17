package dev.solora.navigation

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.launch
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import dev.solora.R
import dev.solora.auth.AuthViewModel
import dev.solora.auth.BiometricState

// This is the welcome screen that shows when someone opens the app for the first time
// It explains what the app does and lets them get started
class OnboardingFragment : Fragment() {

    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_onboarding, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Find and configure the Get Started button
        val getStartedButton = view.findViewById<View>(R.id.btn_get_started)
        
        getStartedButton?.setOnClickListener {
            // Mark onboarding as complete
            authViewModel.markOnboardingComplete()
            
            // Navigate to register (first time users should register)
            findNavController().navigate(R.id.action_onboarding_to_register)
        }
    }
}

// This is the login screen where users can sign in with email/password or Google
// It checks if they have an account and lets them log in
class LoginFragment : Fragment() {

    private val authViewModel: AuthViewModel by viewModels()

    private lateinit var googleSignInClient: GoogleSignInClient
    private val RC_SIGN_IN = 1001

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_login, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val emailInput = view.findViewById<android.widget.EditText>(R.id.et_email)
        val passwordInput = view.findViewById<android.widget.EditText>(R.id.et_password)
        val submitButton = view.findViewById<LinearLayout>(R.id.btn_login)
        val biometricButton = view.findViewById<LinearLayout>(R.id.btn_biometric_login)
        setupBiometricAuth(biometricButton)
        

        submitButton.setOnClickListener {
            val email = emailInput.text?.toString()?.trim() ?: ""
            val password = passwordInput.text?.toString() ?: ""

            if (email.isEmpty() || password.isEmpty()) {
                android.widget.Toast.makeText(
                    requireContext(),
                    "Please fill in all fields",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            authViewModel.login(email, password)
        }

        // Google login setup
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso)

        view.findViewById<ImageButton>(R.id.btn_google_login).setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            startActivityForResult(signInIntent, RC_SIGN_IN)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            authViewModel.authState.collect { state ->
                when (state) {
                    is dev.solora.auth.AuthState.Success -> {
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                        authViewModel.clearAuthState()
                        
                        if (authViewModel.canUseBiometrics() && !authViewModel.isBiometricEnabled.value) {
                            offerBiometricSetup()
                        } else {
                            findNavController().navigate(R.id.action_login_to_main)
                        }
                    }
                    is dev.solora.auth.AuthState.Error -> {
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                        authViewModel.clearAuthState()
                    }
                    else -> Unit
                }
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            authViewModel.biometricState.collect { state ->
                when (state) {
                    is BiometricState.Success -> {
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                        authViewModel.clearBiometricState()
                        // Only navigate if we're actually on the login screen and user is not already logged in
                        if (isAdded && !isDetached && !authViewModel.isUserLoggedIn()) {
                            try {
                                findNavController().navigate(R.id.action_login_to_main)
                            } catch (e: IllegalArgumentException) {
                                // Navigation action doesn't exist from current destination
                            }
                        }
                    }
                    is BiometricState.Error -> {
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                        authViewModel.clearBiometricState()
                    }
                    else -> Unit
                }
            }
        }

        view.findViewById<View>(R.id.btn_to_register).setOnClickListener {
            findNavController().navigate(R.id.action_login_to_register)
        }

        view.findViewById<View>(R.id.txt_btn_sign_up).setOnClickListener {
            findNavController().navigate(R.id.action_login_to_register)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                authViewModel.loginWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                Toast.makeText(requireContext(), "Google login failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun setupBiometricAuth(biometricButton: LinearLayout) {
        viewLifecycleOwner.lifecycleScope.launch {
            authViewModel.isBiometricEnabled.collect { isEnabled ->
                if (authViewModel.canUseBiometrics() && isEnabled) {
                    biometricButton.visibility = View.VISIBLE
                    biometricButton.setOnClickListener {
                        if (isAdded && !isDetached) {
                            activity?.let { fragmentActivity ->
                                authViewModel.authenticateWithBiometrics(fragmentActivity as FragmentActivity)
                            }
                        }
                    }
                } else {
                    biometricButton.visibility = View.GONE
                }
            }
        }
    }
    
    private fun offerBiometricSetup() {
        if (!authViewModel.canUseBiometrics()) {
            findNavController().navigate(R.id.action_login_to_main)
            return
        }
        
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.enable_biometric_login))
            .setMessage(getString(R.string.enable_biometric_message))
            .setPositiveButton(getString(R.string.enable)) { _, _ ->
                activity?.let { fragmentActivity ->
                    if (isAdded && !isDetached) {
                        authViewModel.authenticateWithBiometrics(fragmentActivity as FragmentActivity)
                    }
                }
            }
            .setNegativeButton(getString(R.string.not_now)) { _, _ ->
                if (isAdded && !isDetached) {
                    findNavController().navigate(R.id.action_login_to_main)
                }
            }
            .setCancelable(false)
            .show()
    }
}

// This is the registration screen where new users can create an account
// They can sign up with email/password or Google
class RegisterFragment : Fragment() {

    private val authViewModel: AuthViewModel by viewModels()

    private lateinit var googleSignInClient: GoogleSignInClient
    private val RC_SIGN_IN = 1001

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_register, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val nameInput = view.findViewById<android.widget.EditText>(R.id.et_name)
        val surnameInput = view.findViewById<android.widget.EditText>(R.id.et_surname)
        val emailInput = view.findViewById<android.widget.EditText>(R.id.et_email)
        val passwordInput = view.findViewById<android.widget.EditText>(R.id.et_password)
        val registerButton = view.findViewById<LinearLayout>(R.id.btn_register)

        registerButton.setOnClickListener {
            val name = nameInput.text?.toString()?.trim() ?: ""
            val surname = surnameInput.text?.toString()?.trim() ?: ""
            val email = emailInput.text?.toString()?.trim() ?: ""
            val password = passwordInput.text?.toString() ?: ""

            if (name.isEmpty() || surname.isEmpty() || email.isEmpty() || password.isEmpty()) {
                android.widget.Toast.makeText(
                    requireContext(),
                    "Please fill in all fields",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            if (password.length < 6) {
                android.widget.Toast.makeText(
                    requireContext(),
                    "Password must be at least 6 characters",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            authViewModel.register(name, surname, email, password)
        }

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso)

        val googleButton = view.findViewById<ImageButton>(R.id.btn_google_register)
        googleButton.setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            startActivityForResult(signInIntent, RC_SIGN_IN)
        }

        // Observe auth state for navigation
        viewLifecycleOwner.lifecycleScope.launch {
            authViewModel.authState.collect { state ->
                when (state) {
                    is dev.solora.auth.AuthState.Success -> {
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                        authViewModel.clearAuthState()
                        findNavController().navigate(R.id.action_register_to_main)
                    }
                    is dev.solora.auth.AuthState.Error -> {
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                        authViewModel.clearAuthState()
                    }
                    else -> Unit
                }
            }
        }

        view.findViewById<View>(R.id.btn_back_login).setOnClickListener {
            Log.d("RegisterFragment", "Back to login clicked!")
            findNavController().navigate(R.id.action_register_to_login)
        }

        view.findViewById<View>(R.id.txt_btn_back_login).setOnClickListener {
            Log.d("RegisterFragment", "Back to login clicked!")
            findNavController().navigate(R.id.action_register_to_login)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                authViewModel.registerWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                Toast.makeText(requireContext(), "Google register failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}