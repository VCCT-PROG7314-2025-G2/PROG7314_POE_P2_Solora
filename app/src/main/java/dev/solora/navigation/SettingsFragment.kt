package dev.solora.navigation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.textfield.TextInputEditText
import dev.solora.R
import dev.solora.settings.SettingsViewModel
import dev.solora.settings.CalculationSettings
import dev.solora.settings.CompanySettings
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {
    
    private val settingsViewModel: SettingsViewModel by viewModels()
    
    // Tab elements
    private lateinit var tabCalculation: TextView
    private lateinit var tabCompany: TextView
    private lateinit var contentCalculation: View
    private lateinit var contentCompany: View
    
    // Calculation form elements
    private lateinit var etDefaultTariff: TextInputEditText
    private lateinit var etDefaultPanelWatt: TextInputEditText
    private lateinit var etDefaultSunHours: TextInputEditText
    private lateinit var etPanelCostPerWatt: TextInputEditText
    private lateinit var etInverterCostPerWatt: TextInputEditText
    private lateinit var etInstallationCostPerKw: TextInputEditText
    private lateinit var etPanelEfficiency: TextInputEditText
    private lateinit var etPerformanceRatio: TextInputEditText
    private lateinit var etInverterSizingRatio: TextInputEditText
    private lateinit var etSystemLifetime: TextInputEditText
    
    // Company form elements
    private lateinit var etCompanyName: TextInputEditText
    private lateinit var etCompanyAddress: TextInputEditText
    private lateinit var etCompanyPhone: TextInputEditText
    private lateinit var etCompanyEmail: TextInputEditText
    private lateinit var etCompanyWebsite: TextInputEditText
    private lateinit var etConsultantName: TextInputEditText
    private lateinit var etConsultantPhone: TextInputEditText
    private lateinit var etConsultantEmail: TextInputEditText
    private lateinit var etConsultantLicense: TextInputEditText
    
    // Action buttons
    private lateinit var btnBack: android.widget.ImageButton
    private lateinit var btnSaveSettings: Button
    private lateinit var btnResetSettings: Button
    
    private var currentTab = 0 // 0 = Calculation, 1 = Company
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        try {
            initializeViews(view)
            setupTabs()
            setupButtons()
            observeSettings()
            
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error initializing settings: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun initializeViews(view: View) {
        // Tab elements
        tabCalculation = view.findViewById(R.id.tab_calculation)
        tabCompany = view.findViewById(R.id.tab_company)
        contentCalculation = view.findViewById(R.id.content_calculation)
        contentCompany = view.findViewById(R.id.content_company)
        
        // Calculation form elements
        etDefaultTariff = view.findViewById(R.id.et_default_tariff)
        etDefaultPanelWatt = view.findViewById(R.id.et_default_panel_watt)
        etDefaultSunHours = view.findViewById(R.id.et_default_sun_hours)
        etPanelCostPerWatt = view.findViewById(R.id.et_panel_cost_per_watt)
        etInverterCostPerWatt = view.findViewById(R.id.et_inverter_cost_per_watt)
        etInstallationCostPerKw = view.findViewById(R.id.et_installation_cost_per_kw)
        etPanelEfficiency = view.findViewById(R.id.et_panel_efficiency)
        etPerformanceRatio = view.findViewById(R.id.et_performance_ratio)
        etInverterSizingRatio = view.findViewById(R.id.et_inverter_sizing_ratio)
        etSystemLifetime = view.findViewById(R.id.et_system_lifetime)
        
        // Company form elements
        etCompanyName = view.findViewById(R.id.et_company_name)
        etCompanyAddress = view.findViewById(R.id.et_company_address)
        etCompanyPhone = view.findViewById(R.id.et_company_phone)
        etCompanyEmail = view.findViewById(R.id.et_company_email)
        etCompanyWebsite = view.findViewById(R.id.et_company_website)
        etConsultantName = view.findViewById(R.id.et_consultant_name)
        etConsultantPhone = view.findViewById(R.id.et_consultant_phone)
        etConsultantEmail = view.findViewById(R.id.et_consultant_email)
        etConsultantLicense = view.findViewById(R.id.et_consultant_license)
        
        // Action buttons
        btnBack = view.findViewById(R.id.btn_back)
        btnSaveSettings = view.findViewById(R.id.btn_save_settings)
        btnResetSettings = view.findViewById(R.id.btn_reset_settings)
    }
    
    private fun setupTabs() {
        tabCalculation.setOnClickListener {
            switchToTab(0)
        }
        
        tabCompany.setOnClickListener {
            switchToTab(1)
        }
        
        updateTabAppearance()
    }
    
    private fun switchToTab(tab: Int) {
        currentTab = tab
        
        when (tab) {
            0 -> {
                contentCalculation.visibility = View.VISIBLE
                contentCompany.visibility = View.GONE
            }
            1 -> {
                contentCalculation.visibility = View.GONE
                contentCompany.visibility = View.VISIBLE
            }
        }
        
        updateTabAppearance()
    }
    
    private fun updateTabAppearance() {
        when (currentTab) {
            0 -> {
                tabCalculation.setBackgroundResource(R.drawable.tab_selected)
                tabCalculation.alpha = 1.0f
                tabCompany.setBackgroundResource(R.drawable.tab_unselected)
                tabCompany.alpha = 0.7f
            }
            1 -> {
                tabCompany.setBackgroundResource(R.drawable.tab_selected)
                tabCompany.alpha = 1.0f
                tabCalculation.setBackgroundResource(R.drawable.tab_unselected)
                tabCalculation.alpha = 0.7f
            }
        }
    }
    
    private fun setupButtons() {
        btnBack.setOnClickListener {
            // Navigate back to home page using Navigation Component
            try {
                findNavController().navigateUp()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error navigating back", Toast.LENGTH_SHORT).show()
            }
        }
        
        btnSaveSettings.setOnClickListener {
            saveSettings()
        }
        
        btnResetSettings.setOnClickListener {
            resetToDefaults()
        }
        
        // Set initial button states
        updateSaveButtonState()
    }
    
    private fun observeSettings() {
        try {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    settingsViewModel.settings.collect { settings ->
                        populateForms(settings)
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // Expected when fragment is destroyed, don't show error
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Error loading settings: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error setting up settings: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun populateForms(settings: dev.solora.settings.AppSettings) {
        try {
            
            // Calculation settings
            etDefaultTariff.setText(settings.calculationSettings.defaultTariff.toString())
            etDefaultPanelWatt.setText(settings.calculationSettings.defaultPanelWatt.toString())
            etDefaultSunHours.setText(settings.calculationSettings.defaultSunHours.toString())
            etPanelCostPerWatt.setText(settings.calculationSettings.panelCostPerWatt.toString())
            etInverterCostPerWatt.setText(settings.calculationSettings.inverterCostPerWatt.toString())
            etInstallationCostPerKw.setText(settings.calculationSettings.installationCostPerKw.toString())
            etPanelEfficiency.setText((settings.calculationSettings.panelEfficiency * 100).toString())
            etPerformanceRatio.setText((settings.calculationSettings.performanceRatio * 100).toString())
            etInverterSizingRatio.setText((settings.calculationSettings.inverterSizingRatio * 100).toString())
            etSystemLifetime.setText(settings.calculationSettings.systemLifetime.toString())
            
            // Company settings
            etCompanyName.setText(settings.companySettings.companyName)
            etCompanyAddress.setText(settings.companySettings.companyAddress)
            etCompanyPhone.setText(settings.companySettings.companyPhone)
            etCompanyEmail.setText(settings.companySettings.companyEmail)
            etCompanyWebsite.setText(settings.companySettings.companyWebsite)
            etConsultantName.setText(settings.companySettings.consultantName)
            etConsultantPhone.setText(settings.companySettings.consultantPhone)
            etConsultantEmail.setText(settings.companySettings.consultantEmail)
            etConsultantLicense.setText(settings.companySettings.consultantLicense)
            
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error loading form data: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun saveSettings() {
        try {
            
            // Validate inputs first
            val validationResult = validateInputs()
            if (!validationResult.isValid) {
                Toast.makeText(requireContext(), validationResult.errorMessage, Toast.LENGTH_LONG).show()
                return
            }
            
            // Show loading state
            btnSaveSettings.isEnabled = false
            btnSaveSettings.text = getString(R.string.saving)
            
            // Get calculation settings
            val calculationSettings = CalculationSettings(
                defaultTariff = etDefaultTariff.text.toString().toDoubleOrNull() ?: 2.50,
                defaultPanelWatt = etDefaultPanelWatt.text.toString().toIntOrNull() ?: 420,
                defaultSunHours = etDefaultSunHours.text.toString().toDoubleOrNull() ?: 5.0,
                panelCostPerWatt = etPanelCostPerWatt.text.toString().toDoubleOrNull() ?: 15.0,
                inverterCostPerWatt = etInverterCostPerWatt.text.toString().toDoubleOrNull() ?: 12.0,
                installationCostPerKw = etInstallationCostPerKw.text.toString().toDoubleOrNull() ?: 15000.0,
                panelEfficiency = (etPanelEfficiency.text.toString().toDoubleOrNull() ?: 20.0) / 100.0,
                performanceRatio = (etPerformanceRatio.text.toString().toDoubleOrNull() ?: 80.0) / 100.0,
                inverterSizingRatio = (etInverterSizingRatio.text.toString().toDoubleOrNull() ?: 80.0) / 100.0,
                systemLifetime = etSystemLifetime.text.toString().toIntOrNull() ?: 25,
                panelDegradationRate = 0.005, // Keep default
                co2PerKwh = 0.5 // Keep default
            )
            
            // Get company settings
            val companySettings = CompanySettings(
                companyName = etCompanyName.text.toString().trim(),
                companyAddress = etCompanyAddress.text.toString().trim(),
                companyPhone = etCompanyPhone.text.toString().trim(),
                companyEmail = etCompanyEmail.text.toString().trim(),
                companyWebsite = etCompanyWebsite.text.toString().trim(),
                consultantName = etConsultantName.text.toString().trim(),
                consultantPhone = etConsultantPhone.text.toString().trim(),
                consultantEmail = etConsultantEmail.text.toString().trim(),
                consultantLicense = etConsultantLicense.text.toString().trim(),
                companyLogo = "", // Keep empty for now
                quoteFooter = "", // Keep empty for now
                termsAndConditions = "" // Keep empty for now
            )
            
            
            // Save settings to Firebase
            try {
                settingsViewModel.updateCalculationSettings(calculationSettings)
                settingsViewModel.updateCompanySettings(companySettings)
                
                // Reset button state after successful save
                btnSaveSettings.isEnabled = true
                btnSaveSettings.text = getString(R.string.save_settings)
                Toast.makeText(requireContext(), "Settings saved successfully!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                // Reset button state on error
                btnSaveSettings.isEnabled = true
                btnSaveSettings.text = getString(R.string.save_settings)
                Toast.makeText(requireContext(), "Error saving settings: ${e.message}", Toast.LENGTH_LONG).show()
            }
            
        } catch (e: Exception) {
            // Reset button state on error
            btnSaveSettings.isEnabled = true
            btnSaveSettings.text = getString(R.string.save_settings)
            Toast.makeText(requireContext(), "Error saving settings: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun resetToDefaults() {
        try {
            
            // Show loading state
            btnResetSettings.isEnabled = false
            btnResetSettings.text = getString(R.string.resetting)
            
            // Reset settings in Firebase
            try {
                settingsViewModel.resetToDefaults()
                
                // Reset button state after successful reset
                btnResetSettings.isEnabled = true
                btnResetSettings.text = getString(R.string.reset_to_defaults)
                Toast.makeText(requireContext(), "Settings reset to defaults", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                // Reset button state on error
                btnResetSettings.isEnabled = true
                btnResetSettings.text = getString(R.string.reset_to_defaults)
                Toast.makeText(requireContext(), "Error resetting settings: ${e.message}", Toast.LENGTH_LONG).show()
            }
            
        } catch (e: Exception) {
            // Reset button state on error
            btnResetSettings.isEnabled = true
            btnResetSettings.text = getString(R.string.reset_to_defaults)
            Toast.makeText(requireContext(), "Error resetting settings: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun validateInputs(): ValidationResult {
        try {
            // Validate calculation settings
            val tariff = etDefaultTariff.text.toString().toDoubleOrNull()
            if (tariff == null || tariff <= 0) {
                return ValidationResult(false, "Please enter a valid tariff (must be greater than 0)")
            }
            
            val panelWatt = etDefaultPanelWatt.text.toString().toIntOrNull()
            if (panelWatt == null || panelWatt <= 0) {
                return ValidationResult(false, "Please enter a valid panel wattage (must be greater than 0)")
            }
            
            val sunHours = etDefaultSunHours.text.toString().toDoubleOrNull()
            if (sunHours == null || sunHours <= 0 || sunHours > 24) {
                return ValidationResult(false, "Please enter valid sun hours (must be between 0 and 24)")
            }
            
            val panelCost = etPanelCostPerWatt.text.toString().toDoubleOrNull()
            if (panelCost == null || panelCost <= 0) {
                return ValidationResult(false, "Please enter a valid panel cost (must be greater than 0)")
            }
            
            val inverterCost = etInverterCostPerWatt.text.toString().toDoubleOrNull()
            if (inverterCost == null || inverterCost <= 0) {
                return ValidationResult(false, "Please enter a valid inverter cost (must be greater than 0)")
            }
            
            val installationCost = etInstallationCostPerKw.text.toString().toDoubleOrNull()
            if (installationCost == null || installationCost <= 0) {
                return ValidationResult(false, "Please enter a valid installation cost (must be greater than 0)")
            }
            
            val efficiency = etPanelEfficiency.text.toString().toDoubleOrNull()
            if (efficiency == null || efficiency <= 0 || efficiency > 100) {
                return ValidationResult(false, "Please enter a valid panel efficiency (must be between 0 and 100%)")
            }
            
            val performanceRatio = etPerformanceRatio.text.toString().toDoubleOrNull()
            if (performanceRatio == null || performanceRatio <= 0 || performanceRatio > 100) {
                return ValidationResult(false, "Please enter a valid performance ratio (must be between 0 and 100%)")
            }
            
            val inverterRatio = etInverterSizingRatio.text.toString().toDoubleOrNull()
            if (inverterRatio == null || inverterRatio <= 0 || inverterRatio > 100) {
                return ValidationResult(false, "Please enter a valid inverter sizing ratio (must be between 0 and 100%)")
            }
            
            val lifetime = etSystemLifetime.text.toString().toIntOrNull()
            if (lifetime == null || lifetime <= 0) {
                return ValidationResult(false, "Please enter a valid system lifetime (must be greater than 0)")
            }
            
            return ValidationResult(true, "")
            
        } catch (e: Exception) {
            return ValidationResult(false, "Validation error: ${e.message}")
        }
    }
    
    private fun updateSaveButtonState() {
        try {
            // Enable save button by default
            btnSaveSettings.isEnabled = true
            btnSaveSettings.text = getString(R.string.save_settings)
        } catch (e: Exception) {
        }
    }
    
    private data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String
    )
}