package dev.solora.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import dev.solora.R
import dev.solora.notifications.MotivationalNotificationManager
import kotlinx.coroutines.launch

class NotificationsFragment : Fragment() {

    private lateinit var btnBackNotifications: ImageButton
    private lateinit var btnBackToHome: Button
    private var switchNotifications: Switch? = null
    private var tvNotificationStatus: TextView? = null
    
    private var isInitializingToggle = false
    private lateinit var notificationManager: MotivationalNotificationManager
    
    // Permission launcher for notifications (Android 13+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            enableNotificationsAfterPermission()
        } else {
            switchNotifications?.isChecked = false
            updateNotificationStatus(false)
            Toast.makeText(requireContext(), "Permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_notifications, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        notificationManager = MotivationalNotificationManager(requireContext())
        
        initializeViews(view)
        setupClickListeners()
        loadNotificationSettings()
    }

    private fun initializeViews(view: View) {
        btnBackNotifications = view.findViewById(R.id.btn_back_notifications)
        btnBackToHome = view.findViewById(R.id.btn_back_to_home)
        switchNotifications = view.findViewById(R.id.switch_notifications)
        tvNotificationStatus = view.findViewById(R.id.tv_notification_status)
    }

    private fun setupClickListeners() {
        btnBackNotifications.setOnClickListener {
            findNavController().popBackStack()
        }

        btnBackToHome.setOnClickListener {
            findNavController().popBackStack()
        }
        
        switchNotifications?.setOnCheckedChangeListener { _, isChecked ->
            handleNotificationToggle(isChecked)
        }
    }
    
    private fun loadNotificationSettings() {
        viewLifecycleOwner.lifecycleScope.launch {
            isInitializingToggle = true
            notificationManager.syncNotificationPreference()
            val isEnabled = notificationManager.isNotificationsEnabled()
            switchNotifications?.isChecked = isEnabled
            updateNotificationStatus(isEnabled)
            isInitializingToggle = false
        }
    }
    
    private fun handleNotificationToggle(isEnabled: Boolean) {
        if (isInitializingToggle) return
        
        if (isEnabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED) {
                    enableNotificationsAfterPermission()
                } else {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            } else {
                enableNotificationsAfterPermission()
            }
        } else {
            viewLifecycleOwner.lifecycleScope.launch {
                notificationManager.enableMotivationalNotifications(false)
                updateNotificationStatus(false)
                Toast.makeText(requireContext(), "Push notifications disabled", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun enableNotificationsAfterPermission() {
        viewLifecycleOwner.lifecycleScope.launch {
            notificationManager.enableMotivationalNotifications(true)
            updateNotificationStatus(true)
            Toast.makeText(requireContext(), "Push notifications enabled", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun updateNotificationStatus(isEnabled: Boolean) {
        tvNotificationStatus?.text = if (isEnabled) {
            "Notifications enabled"
        } else {
            "Notifications disabled"
        }
    }
    
}