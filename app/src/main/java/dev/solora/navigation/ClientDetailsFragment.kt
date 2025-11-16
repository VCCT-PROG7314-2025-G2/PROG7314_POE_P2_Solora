package dev.solora.navigation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import dev.solora.R
import dev.solora.SoloraApp
import dev.solora.data.FirebaseLead
import dev.solora.data.FirebaseQuote
import dev.solora.data.OfflineRepository
import dev.solora.data.toLocalLead
import dev.solora.leads.LeadsViewModel
import dev.solora.quotes.QuotesViewModel
import dev.solora.quote.QuoteOutputs
import kotlinx.coroutines.launch
import java.util.UUID

class ClientDetailsFragment : Fragment() {
    
    private val leadsViewModel: LeadsViewModel by viewModels()
    private val quotesViewModel: QuotesViewModel by viewModels()
    private lateinit var offlineRepo: OfflineRepository


    // UI Components
    private lateinit var btnBack: ImageButton
    private lateinit var etSelectClient: AutoCompleteTextView
    private lateinit var etReferenceNumber: TextInputEditText
    private lateinit var etFirstName: TextInputEditText
    private lateinit var etLastName: TextInputEditText
    private lateinit var etAddress: TextInputEditText
    private lateinit var etEmail: TextInputEditText
    private lateinit var etContactNumber: TextInputEditText
    private lateinit var btnSaveQuote: Button
    
    private var calculationOutputs: QuoteOutputs? = null
    private var calculatedAddress: String = ""
    private var selectedLead: dev.solora.data.FirebaseLead? = null
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_client_details, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        offlineRepo = (requireActivity().application as SoloraApp).offlineRepo
        
        initializeViews(view)
        setupClickListeners()
        setupClientDropdown()
        
        // Get data from arguments
        arguments?.let { args ->
            calculationOutputs = args.getSerializable("calculation_outputs") as? QuoteOutputs
            calculatedAddress = args.getString("calculated_address") ?: ""
        }
        
        // Pre-populate address from calculation
        if (calculatedAddress.isNotEmpty()) {
            etAddress.setText(calculatedAddress)
        }
        
        // Generate reference number
        etReferenceNumber.setText("QUOTE-${System.currentTimeMillis().toString().takeLast(5)}")
        
        // Update button text initially
        updateButtonText()
        
        observeLeads()
    }
    
    private fun initializeViews(view: View) {
        btnBack = view.findViewById(R.id.btn_back_client_details)
        etSelectClient = view.findViewById(R.id.et_select_client)
        etReferenceNumber = view.findViewById(R.id.et_reference_number)
        etFirstName = view.findViewById(R.id.et_first_name)
        etLastName = view.findViewById(R.id.et_last_name)
        etAddress = view.findViewById(R.id.et_address)
        etEmail = view.findViewById(R.id.et_email)
        etContactNumber = view.findViewById(R.id.et_contact_number)
        btnSaveQuote = view.findViewById(R.id.btn_save_quote_final)
    }
    
    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
        
        btnSaveQuote.setOnClickListener {
            saveQuoteWithClientDetails()
        }
        
        // Handle client selection
        etSelectClient.setOnItemClickListener { _, _, position, _ ->
            val leads = leadsViewModel.getLeadsForSelection()
            if (position < leads.size) {
                selectedLead = leads[position]
                // Selected lead: ID=${selectedLead?.id}, Name=${selectedLead?.name}
                selectedLead?.let { lead ->
                    populateClientDetails(lead)
                    updateButtonText()
                }
            }
        }
        
        // Clear selected lead when user types in fields
        etFirstName.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && selectedLead != null) {
                selectedLead = null
                updateButtonText()
            }
        }
        
        etLastName.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && selectedLead != null) {
                selectedLead = null
                updateButtonText()
            }
        }
    }
    
    private fun setupClientDropdown() {
        // This will be populated when leads are observed
    }
    
    private fun observeLeads() {
        viewLifecycleOwner.lifecycleScope.launch {
            leadsViewModel.leads.collect { leads ->
                // Show only lead names in the dropdown
                val leadNames = leads.map { it.name }
                val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, leadNames)
                etSelectClient.setAdapter(adapter)
                
                // Store leads for selection
                leadsViewModel.setLeadsForSelection(leads)
            }
        }
    }
    
    private fun populateClientDetails(lead: dev.solora.data.FirebaseLead) {
        etFirstName.setText(lead.name.split(" ").firstOrNull() ?: "")
        etLastName.setText(lead.name.split(" ").drop(1).joinToString(" "))
        etEmail.setText(lead.email)
        etContactNumber.setText(lead.phone)
        // Note: FirebaseLead doesn't have address field, so we keep the pre-populated address from calculation
    }
    
    private fun updateButtonText() {
        if (selectedLead != null) {
            btnSaveQuote.text = "Save Quote"
        } else {
            btnSaveQuote.text = "Save Quote & Lead"
        }
    }

    private fun saveQuoteWithClientDetails() {
        val reference = etReferenceNumber.text.toString().trim()
        val firstName = etFirstName.text.toString().trim()
        val lastName = etLastName.text.toString().trim()
        val address = etAddress.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val contact = etContactNumber.text.toString().trim()

        // Validation
        if (firstName.isEmpty() || lastName.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter client name", Toast.LENGTH_SHORT).show()
            return
        }
        if (address.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter address", Toast.LENGTH_SHORT).show()
            return
        }
        if (contact.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter contact number", Toast.LENGTH_SHORT).show()
            return
        }

        val clientName = "$firstName $lastName"

        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUserId == null) {
            Toast.makeText(requireContext(), "You must be logged in to save a quote", Toast.LENGTH_SHORT).show()
            return
        }

        calculationOutputs?.let { outputs ->
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    // Save offline first
                    val firebaseQuote = FirebaseQuote(
                        reference = reference,
                        clientName = clientName,
                        address = address,
                        usageKwh = outputs.monthlyUsageKwh,
                        billRands = outputs.monthlyBillRands,
                        tariff = outputs.tariffRPerKwh,
                        panelWatt = outputs.panelWatt,
                        latitude = outputs.detailedAnalysis?.locationData?.latitude,
                        longitude = outputs.detailedAnalysis?.locationData?.longitude,
                        averageAnnualIrradiance = outputs.detailedAnalysis?.locationData?.averageAnnualIrradiance,
                        averageAnnualSunHours = outputs.detailedAnalysis?.locationData?.averageAnnualSunHours,
                        systemKwp = outputs.systemKw,
                        estimatedGeneration = outputs.estimatedMonthlyGeneration,
                        monthlySavings = outputs.monthlySavingsRands,
                        paybackMonths = outputs.paybackMonths,
                        userId = currentUserId
                    )
                    offlineRepo.saveQuoteOffline(firebaseQuote)

                    // Save online using QuotesViewModel for consistent behavior & notifications
                    val saveResult = quotesViewModel.saveQuoteFromCalculationSync(
                        reference,
                        clientName,
                        address,
                        outputs
                    )

                    val savedQuote = saveResult.getOrNull() ?: firebaseQuote // fallback

                    // Handle lead linking or creation
                    val lead = selectedLead
                    if (lead != null && !lead.id.isNullOrBlank()) {
                        // Link quote to existing lead
                        try {
                            val linkResult = leadsViewModel.linkQuoteToLeadSync(lead.id!!, savedQuote.id!!)
                            Toast.makeText(
                                requireContext(),
                                if (linkResult) "Quote saved and linked to lead!" else "Quote saved successfully!",
                                Toast.LENGTH_LONG
                            ).show()
                        } catch (e: Exception) {
                            Toast.makeText(
                                requireContext(),
                                "Quote saved but lead linking failed: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } else {
                        // No selected lead: create new lead
                        val newLead = FirebaseLead(
                            id = UUID.randomUUID().toString(),
                            name = clientName,
                            email = email,
                            phone = contact,
                            notes = "Lead created from quote: $reference",
                            quoteId = savedQuote.id,
                            userId = currentUserId
                        )

                        offlineRepo.saveLeadOffline(newLead.toLocalLead())

                        try {
                            val createResult = leadsViewModel.createLeadFromQuoteSync(
                                quoteId = savedQuote.id!!,
                                clientName = clientName,
                                address = address,
                                email = email,
                                phone = contact,
                                notes = "Lead created from quote: $reference"
                            )
                            Toast.makeText(
                                requireContext(),
                                if (createResult) "Quote saved and new lead created!" else "Quote saved but lead creation failed.",
                                Toast.LENGTH_LONG
                            ).show()
                        } catch (e: Exception) {
                            Toast.makeText(
                                requireContext(),
                                "Quote saved but lead creation failed: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }

                    // Navigate to View Quotes tab
                    findNavController().navigate(
                        R.id.action_client_details_to_quotes,
                        Bundle().apply { putInt("show_tab", 1) }
                    )

                } catch (e: Exception) {
                    // Fallback: offline saved quote, notify user
                    Toast.makeText(requireContext(), "Quote saved successfully!", Toast.LENGTH_LONG).show()
                    findNavController().navigate(
                        R.id.action_client_details_to_quotes,
                        Bundle().apply { putInt("show_tab", 1) }
                    )
                }
            }
        } ?: run {
            Toast.makeText(requireContext(), "No calculation data available", Toast.LENGTH_SHORT).show()
        }
    }
}
