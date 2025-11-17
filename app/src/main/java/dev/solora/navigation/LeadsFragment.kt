package dev.solora.navigation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.datepicker.MaterialDatePicker
import dev.solora.R
import dev.solora.leads.LeadsViewModel
import dev.solora.data.FirebaseLead
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class LeadsFragment : Fragment() {
    
    private val leadsViewModel: LeadsViewModel by viewModels()
    private lateinit var leadsAdapter: LeadsAdapter
    
    // UI Elements
    private lateinit var rvLeads: RecyclerView
    private lateinit var layoutEmptyLeads: View
    private lateinit var fabAddLead: FloatingActionButton
    private lateinit var btnAddLeadFallback: Button
    private lateinit var btnAddLeadEmpty: Button
    private lateinit var overlayAddLead: View
    private lateinit var btnBackLeads: ImageButton
    
    // Form elements
    private lateinit var etFirstName: EditText
    private lateinit var etLastName: EditText
    private lateinit var etAddress: EditText
    private lateinit var etEmail: EditText
    private lateinit var etContact: EditText
    private lateinit var spinnerStatus: AutoCompleteTextView
    private lateinit var etFollowUpDate: EditText
    private lateinit var btnAdd: Button
    private lateinit var btnCancel: Button
    private var selectedFollowUpDateMillis: Long? = null
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_leads, container, false)
        return view
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        
        // Debug bottom navigation visibility
        debugBottomNavigation()
        
        initializeViews(view)
        setupRecyclerView()
        setupClickListeners()
        observeLeads()
        
    }
    
    override fun onResume() {
        super.onResume()
        
        // Ensure bottom navigation is visible and properly configured when fragment resumes
        try {
            val parentFragment = parentFragment
            if (parentFragment is MainTabsFragment) {
                val bottomNav = parentFragment.view?.findViewById<BottomNavigationView>(R.id.bottom_nav)
                ensureBottomNavigationVisible(bottomNav)
            }
        } catch (e: Exception) {
        }
    }
    
    private fun debugBottomNavigation() {
        try {
            val parentFragment = parentFragment
            
            if (parentFragment is MainTabsFragment) {
                val bottomNav = parentFragment.view?.findViewById<BottomNavigationView>(R.id.bottom_nav)
                
                // Ensure bottom navigation is visible and properly configured
                ensureBottomNavigationVisible(bottomNav)
            } else {
            }
        } catch (e: Exception) {
        }
    }
    
    private fun ensureBottomNavigationVisible(bottomNav: BottomNavigationView?) {
        bottomNav?.let { nav ->
            
            // Make sure it's visible
            nav.visibility = View.VISIBLE
            
            // Ensure it's properly configured
            nav.isEnabled = true
            nav.isClickable = true
            
            // Set the selected item to leads if not already set
            if (nav.selectedItemId != R.id.leadsFragment) {
                nav.selectedItemId = R.id.leadsFragment
            }
            
        }
    }
    
    private fun initializeViews(view: View) {
        rvLeads = view.findViewById(R.id.rv_leads)
        layoutEmptyLeads = view.findViewById(R.id.layout_empty_leads)
        fabAddLead = view.findViewById(R.id.fab_add_lead)
        btnAddLeadFallback = view.findViewById(R.id.btn_add_lead_fallback)
        btnAddLeadEmpty = view.findViewById(R.id.btn_add_lead_empty)
        overlayAddLead = view.findViewById(R.id.overlay_add_lead)
        btnBackLeads = view.findViewById(R.id.btn_back_leads)
        
        if (fabAddLead != null) {
            fabAddLead.isClickable = true
            fabAddLead.isFocusable = true
        } else {
            // Show fallback button if FAB doesn't work
            btnAddLeadFallback.visibility = View.VISIBLE
        }
        
        // Form elements
        etFirstName = view.findViewById(R.id.et_first_name)
        etLastName = view.findViewById(R.id.et_last_name)
        etAddress = view.findViewById(R.id.et_address)
        etEmail = view.findViewById(R.id.et_email)
        etContact = view.findViewById(R.id.et_contact)
        spinnerStatus = view.findViewById(R.id.spinner_status)
        etFollowUpDate = view.findViewById(R.id.et_follow_up_date)
        btnAdd = view.findViewById(R.id.btn_add)
        btnCancel = view.findViewById(R.id.btn_cancel)
        
        // Setup status dropdown
        setupStatusDropdown()
        
        // Setup follow-up date picker
        setupFollowUpDatePicker()
    }
    
    private fun setupStatusDropdown() {
        val statusOptions = arrayOf("New", "Contacted", "Qualified", "Negotiating", "Closed - Won", "Closed - Lost")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, statusOptions)
        spinnerStatus.setAdapter(adapter)
        spinnerStatus.setText("New", false) // Set default value
    }
    
    private fun setupFollowUpDatePicker() {
        etFollowUpDate.setOnClickListener {
            val datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select Follow-up Date")
                .setSelection(selectedFollowUpDateMillis ?: MaterialDatePicker.todayInUtcMilliseconds())
                .setTheme(R.style.ThemeOverlay_Solora_DatePicker)
                .build()

            datePicker.addOnPositiveButtonClickListener { selection ->
                selectedFollowUpDateMillis = selection
                val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                etFollowUpDate.setText(dateFormat.format(Date(selection)))
            }

            datePicker.show(parentFragmentManager, "DATE_PICKER")
        }
    }
    
    private fun setupRecyclerView() {
        leadsAdapter = LeadsAdapter { lead ->
            // Show lead details in professional dialog
            showLeadDetails(lead)
        }
        
        rvLeads.layoutManager = LinearLayoutManager(requireContext())
        rvLeads.adapter = leadsAdapter
    }
    
    private fun showLeadDetails(lead: FirebaseLead) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_lead_details, null)
        
        // Initialize views
        val tvLeadName = dialogView.findViewById<TextView>(R.id.tv_lead_name)
        val tvLeadEmail = dialogView.findViewById<TextView>(R.id.tv_lead_email)
        val tvLeadPhone = dialogView.findViewById<TextView>(R.id.tv_lead_phone)
        val tvLeadId = dialogView.findViewById<TextView>(R.id.tv_lead_id)
        val tvLeadDate = dialogView.findViewById<TextView>(R.id.tv_lead_date)
        val tvLeadStatus = dialogView.findViewById<TextView>(R.id.tv_lead_status)
        val tvLeadNotes = dialogView.findViewById<TextView>(R.id.tv_lead_notes)
        val tvQuoteId = dialogView.findViewById<TextView>(R.id.tv_quote_id)
        val cardLeadNotes = dialogView.findViewById<androidx.cardview.widget.CardView>(R.id.card_lead_notes)
        val cardLinkedQuote = dialogView.findViewById<androidx.cardview.widget.CardView>(R.id.card_linked_quote)
        
        // Format date
        val dateText = lead.createdAt?.toDate()?.let {
            java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(it)
        } ?: "Unknown date"
        
        // Populate data
        tvLeadName.text = lead.name
        tvLeadEmail.text = if (lead.email.isNotEmpty()) lead.email else "Not provided"
        tvLeadPhone.text = if (lead.phone.isNotEmpty()) lead.phone else "Not provided"
        tvLeadId.text = lead.id ?: "N/A"
        tvLeadDate.text = dateText
        tvLeadStatus.text = lead.status.uppercase()
        
        // Show/hide notes card
        if (!lead.notes.isNullOrEmpty()) {
            tvLeadNotes.text = lead.notes
            cardLeadNotes.visibility = View.VISIBLE
        } else {
            cardLeadNotes.visibility = View.GONE
        }
        
        // Show/hide linked quote card
        if (lead.quoteId != null) {
            tvQuoteId.text = "Quote ID: ${lead.quoteId}"
            cardLinkedQuote.visibility = View.VISIBLE
        } else {
            cardLinkedQuote.visibility = View.GONE
        }
        
        // Create dialog
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        // Set up button click listeners
        dialogView.findViewById<ImageButton>(R.id.btn_close_lead_details).setOnClickListener {
            dialog.dismiss()
        }
        
        dialogView.findViewById<Button>(R.id.btn_close_lead_details_action).setOnClickListener {
            dialog.dismiss()
        }
        
        dialogView.findViewById<Button>(R.id.btn_update_status).setOnClickListener {
            showUpdateStatusDialog(lead, dialog)
        }
        
        dialog.show()
    }
    
    private fun showUpdateStatusDialog(lead: FirebaseLead, parentDialog: androidx.appcompat.app.AlertDialog) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_status_selection, null)
        val radioGroup = dialogView.findViewById<android.widget.RadioGroup>(R.id.radio_group_status)
        val btnUpdate = dialogView.findViewById<Button>(R.id.btn_update)
        val btnCancel = dialogView.findViewById<Button>(R.id.btn_cancel)
        
        // Status options with descriptions
        val statusOptions = listOf(
            Triple("New", "new", "Lead has just been created"),
            Triple("Contacted", "contacted", "Initial contact has been made"),
            Triple("Qualified", "qualified", "Lead meets our criteria"),
            Triple("Negotiating", "negotiating", "Discussing terms and pricing"),
            Triple("Closed - Won", "closed_won", "Successfully converted to customer"),
            Triple("Closed - Lost", "closed_lost", "Lead did not convert")
        )
        
        var selectedStatus = lead.status
        
        // Add radio buttons for each status
        statusOptions.forEach { (displayName, statusValue, description) ->
            val radioButton = android.widget.RadioButton(requireContext()).apply {
                text = displayName
                id = View.generateViewId()
                setPadding(16, 24, 16, 24)
                textSize = 16f
                setTextColor(resources.getColor(android.R.color.black, null))
                buttonTintList = android.content.res.ColorStateList.valueOf(resources.getColor(dev.solora.R.color.solora_orange, null))
                
                // Add description below
                val descText = "\n$description"
                text = "$displayName$descText"
                
                // Check if this is the current status
                isChecked = (statusValue == lead.status)
                
                setOnClickListener {
                    selectedStatus = statusValue
                }
            }
            radioGroup.addView(radioButton)
        }
        
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        
        btnUpdate.setOnClickListener {
            if (selectedStatus != lead.status) {
                lead.id?.let { leadId ->
                    val displayName = statusOptions.find { it.second == selectedStatus }?.first ?: selectedStatus
                    
                    // Update status in Firebase
                    leadsViewModel.updateLeadStatus(leadId, selectedStatus)
                    
                    // Close both dialogs
                    dialog.dismiss()
                    parentDialog.dismiss()
                    
                    // Show toast after closing dialogs
                    Toast.makeText(requireContext(), "Status updated to $displayName", Toast.LENGTH_SHORT).show()
                    
                    // Real-time Firestore listener will automatically update the list
                }
            } else {
                dialog.dismiss()
            }
        }
        
        dialog.show()
    }
    
    private fun setupClickListeners() {
        
        // Back button click listener
        btnBackLeads.setOnClickListener {
            try {
                val parentFragment = parentFragment
                if (parentFragment is MainTabsFragment) {
                    val bottomNav = parentFragment.view?.findViewById<BottomNavigationView>(R.id.bottom_nav)
                    bottomNav?.selectedItemId = R.id.homeFragment
                } else {
                    // Fallback to direct navigation
                    findNavController().navigate(R.id.homeFragment)
                }
            } catch (e: Exception) {
                // Fallback to direct navigation
                findNavController().navigate(R.id.homeFragment)
            }
        }
        
        if (::fabAddLead.isInitialized) {
            fabAddLead.setOnClickListener {
                showAddLeadModal()
            }
        } else {
        }

        // Also setup fallback button click listener
        btnAddLeadFallback.setOnClickListener {
            showAddLeadModal()
        }


        // Setup empty state button click listener
        btnAddLeadEmpty.setOnClickListener {
            showAddLeadModal()
        }
        
        btnAdd.setOnClickListener {
            addFirebaseLead()
        }
        
        btnCancel.setOnClickListener {
            hideAddLeadModal()
        }
        
        // Close modal when clicking outside
        overlayAddLead.setOnClickListener {
            hideAddLeadModal()
        }
    }
    
    private fun observeLeads() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                leadsViewModel.leads.collect { leads ->
                    leadsAdapter.submitList(leads)
                    
                    // Show/hide empty state and FAB
                    if (leads.isEmpty()) {
                        rvLeads.visibility = View.GONE
                        layoutEmptyLeads.visibility = View.VISIBLE
                        if (::fabAddLead.isInitialized) {
                            fabAddLead.visibility = View.GONE
                        }
                    } else {
                        rvLeads.visibility = View.VISIBLE
                        layoutEmptyLeads.visibility = View.GONE
                        if (::fabAddLead.isInitialized) {
                            fabAddLead.visibility = View.VISIBLE
                        } else {
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error loading leads: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun showAddLeadModal() {
        overlayAddLead.visibility = View.VISIBLE
        clearForm()
    }
    
    private fun hideAddLeadModal() {
        overlayAddLead.visibility = View.GONE
    }
    
    private fun clearForm() {
        etFirstName.text.clear()
        etLastName.text.clear()
        etAddress.text.clear()
        etEmail.text.clear()
        etContact.text.clear()
        etFollowUpDate.text.clear()
        selectedFollowUpDateMillis = null
        spinnerStatus.setText("New", false)
    }
    
    private fun addFirebaseLead() {
        val firstName = etFirstName.text.toString().trim()
        val lastName = etLastName.text.toString().trim()
        val address = etAddress.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val contact = etContact.text.toString().trim()
        val selectedStatus = spinnerStatus.text.toString().trim()
        
        // Validation
        if (firstName.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter first name", Toast.LENGTH_SHORT).show()
            return
        }
        if (lastName.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter last name", Toast.LENGTH_SHORT).show()
            return
        }
        if (address.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter address", Toast.LENGTH_SHORT).show()
            return
        }
        if (contact.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter contact information", Toast.LENGTH_SHORT).show()
            return
        }
        
        val fullName = "$firstName $lastName"
        
        // Convert display status to database status
        val status = when (selectedStatus) {
            "New" -> "new"
            "Contacted" -> "contacted"
            "Qualified" -> "qualified"
            "Negotiating" -> "negotiating"
            "Closed - Won" -> "closed_won"
            "Closed - Lost" -> "closed_lost"
            else -> "new"
        }
        
        // Convert follow-up date to Timestamp if selected
        val followUpTimestamp = selectedFollowUpDateMillis?.let {
            com.google.firebase.Timestamp(Date(it))
        }
        
        // Add lead with status and follow-up date
        leadsViewModel.addLead(fullName, email, contact, "", status, followUpTimestamp)
        
        // Clear form and hide modal
        clearForm()
        hideAddLeadModal()
        Toast.makeText(requireContext(), "Lead added successfully!", Toast.LENGTH_SHORT).show()
    }
    
    private fun generateFirebaseLeadReference(): String {
        // Generate a simple reference number
        return (10000..99999).random().toString()
    }
}

// RecyclerView Adapter
class LeadsAdapter(
    private val onLeadClick: (FirebaseLead) -> Unit
) : RecyclerView.Adapter<LeadsAdapter.LeadViewHolder>() {
    
    private var leads: List<FirebaseLead> = emptyList()
    
    fun submitList(newLeads: List<FirebaseLead>) {
        leads = newLeads
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LeadViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_lead, parent, false)
        return LeadViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: LeadViewHolder, position: Int) {
        holder.bind(leads[position])
    }
    
    override fun getItemCount(): Int = leads.size
    
    inner class LeadViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvReference = itemView.findViewById<android.widget.TextView>(R.id.tv_reference)
        private val tvName = itemView.findViewById<android.widget.TextView>(R.id.tv_name)
        private val tvEmail = itemView.findViewById<android.widget.TextView>(R.id.tv_email)
        private val tvFollowUpDate = itemView.findViewById<android.widget.TextView>(R.id.tv_follow_up_date)
        private val layoutFollowUp = itemView.findViewById<LinearLayout>(R.id.layout_follow_up)
        private val clickableArea = itemView.findViewById<LinearLayout>(R.id.clickable_area) ?: itemView
        
        fun bind(lead: FirebaseLead) {
            tvReference.text = lead.id ?: "N/A"
            tvName.text = lead.name
            tvEmail.text = if (lead.email.isNotEmpty()) lead.email else "Email:"
            
            // Display follow-up date if available
            if (lead.followUpDate != null) {
                layoutFollowUp.visibility = View.VISIBLE
                val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                val followUpDateStr = dateFormat.format(lead.followUpDate.toDate())
                tvFollowUpDate.text = "Follow-up: $followUpDateStr"
            } else {
                layoutFollowUp.visibility = View.GONE
            }
            
            clickableArea.setOnClickListener { onLeadClick(lead) }
        }
    }
}