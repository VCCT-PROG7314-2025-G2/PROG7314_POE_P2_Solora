package dev.solora.navigation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.card.MaterialCardView
import dev.solora.R
import dev.solora.quotes.QuotesViewModel
import dev.solora.leads.LeadsViewModel
import dev.solora.settings.SettingsViewModel
import dev.solora.data.FirebaseQuote
import dev.solora.api.FirebaseFunctionsApi
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class HomeFragment : Fragment() {
    
    private val quotesViewModel: QuotesViewModel by viewModels()
    private val leadsViewModel: LeadsViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val profileViewModel: dev.solora.profile.ProfileViewModel by viewModels()
    private val apiService = FirebaseFunctionsApi()
    
    // UI Elements
    private lateinit var tvCompanyName: TextView
    private lateinit var tvConsultantName: TextView
    private lateinit var tvQuotesCount: TextView
    private lateinit var tvLeadsCount: TextView
    private lateinit var btnNotifications: ImageView
    private lateinit var btnSettings: ImageView
    private lateinit var cardCalculateQuote: MaterialCardView
    private lateinit var cardAddLeads: MaterialCardView
    private lateinit var layoutRecentQuotes: LinearLayout
    
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initializeViews(view)
        setupClickListeners()
        observeData()
        loadRecentQuotes()
        performApiRefresh()
    }
    
    override fun onResume() {
        super.onResume()
        // Reload recent quotes when fragment becomes visible
        loadRecentQuotes()
    }
    
    private fun initializeViews(view: View) {
        tvCompanyName = view.findViewById(R.id.tv_company_name)
        tvConsultantName = view.findViewById(R.id.tv_consultant_name)
        tvQuotesCount = view.findViewById(R.id.tv_quotes_count)
        tvLeadsCount = view.findViewById(R.id.tv_leads_count)
        btnNotifications = view.findViewById(R.id.btn_notifications)
        btnSettings = view.findViewById(R.id.btn_settings)
        cardCalculateQuote = view.findViewById(R.id.card_calculate_quote)
        cardAddLeads = view.findViewById(R.id.card_add_leads)
        layoutRecentQuotes = view.findViewById(R.id.layout_recent_quotes)
    }
    
    private fun setupClickListeners() {
        btnNotifications.setOnClickListener {
            findNavController().navigate(R.id.action_to_notifications)
        }
        
        btnSettings.setOnClickListener {
            findNavController().navigate(R.id.settingsFragment)
        }
        
        cardCalculateQuote.setOnClickListener {
            // Navigate to quotes tab using bottom navigation selection
            try {
                val parentFragment = parentFragment
                if (parentFragment is MainTabsFragment) {
                    val bottomNav = parentFragment.view?.findViewById<BottomNavigationView>(R.id.bottom_nav)
                    bottomNav?.selectedItemId = R.id.quotesFragment
                } else {
                    // Fallback to direct navigation if parent access fails
                    findNavController().navigate(R.id.quotesFragment)
                }
            } catch (e: Exception) {
                // Fallback to direct navigation
                findNavController().navigate(R.id.quotesFragment)
            }
        }
        
        cardAddLeads.setOnClickListener {
            // Navigate to leads tab using bottom navigation selection
            try {
                val parentFragment = parentFragment
                if (parentFragment is MainTabsFragment) {
                    val bottomNav = parentFragment.view?.findViewById<BottomNavigationView>(R.id.bottom_nav)
                    bottomNav?.selectedItemId = R.id.leadsFragment
                } else {
                    // Fallback to direct navigation if parent access fails
                    findNavController().navigate(R.id.leadsFragment)
                }
            } catch (e: Exception) {
                // Fallback to direct navigation
                findNavController().navigate(R.id.leadsFragment)
            }
        }
    }
    
    private fun observeData() {
        // Observe quotes count
        viewLifecycleOwner.lifecycleScope.launch {
            quotesViewModel.quotes.collect { quotes ->
                tvQuotesCount.text = quotes.size.toString()
            }
        }
        
        // Observe leads count
        viewLifecycleOwner.lifecycleScope.launch {
            leadsViewModel.leads.collect { leads ->
                tvLeadsCount.text = leads.size.toString()
            }
        }
        
        // Observe settings to update company name
        viewLifecycleOwner.lifecycleScope.launch {
            settingsViewModel.settings.collect { settings ->
                updateCompanyName(settings.companySettings)
            }
        }
        
        // Observe user profile to update consultant name
        viewLifecycleOwner.lifecycleScope.launch {
            profileViewModel.userProfile.collect { user ->
                user?.let { updateConsultantName(it) }
            }
        }
    }
    
    private fun updateCompanyName(companySettings: dev.solora.settings.CompanySettings) {
        // Update company name
        val companyName = if (companySettings.companyName.isNotEmpty()) {
            companySettings.companyName
        } else {
            "SOLORA"
        }
        tvCompanyName.text = companyName
    }
    
    private fun updateConsultantName(user: dev.solora.data.FirebaseUser) {
        // Update consultant name with full name and surname from user profile
        val fullName = if (user.surname.isNotEmpty() && user.surname != user.name) {
            "${user.name} ${user.surname}"
        } else if (user.name.isNotEmpty()) {
            user.name
        } else {
            "Not Set"
        }
        tvConsultantName.text = getString(R.string.welcome_back, fullName)
    }
    
    private fun loadRecentQuotes() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                
                // Get quotes from the last 7 days using REST API
                val quotesResult = apiService.getQuotes(search = null, limit = 100)
                
                if (quotesResult.isSuccess) {
                    val quotesData = quotesResult.getOrNull() ?: emptyList()
                    
                    if (quotesData.isNotEmpty()) {
                    }
                    
                    // Convert API response to FirebaseQuote objects
                    val allQuotes = quotesData.mapNotNull { data: Map<String, Any> ->
                        try {
                            val quote = FirebaseQuote(
                                id = data["id"] as? String,
                                reference = data["reference"] as? String ?: "",
                                clientName = data["clientName"] as? String ?: "",
                                address = data["address"] as? String ?: "",
                                usageKwh = (data["usageKwh"] as? Number)?.toDouble(),
                                billRands = (data["billRands"] as? Number)?.toDouble(),
                                tariff = (data["tariff"] as? Number)?.toDouble() ?: 0.0,
                                panelWatt = (data["panelWatt"] as? Number)?.toInt() ?: 0,
                                latitude = (data["latitude"] as? Number)?.toDouble(),
                                longitude = (data["longitude"] as? Number)?.toDouble(),
                                averageAnnualIrradiance = (data["averageAnnualIrradiance"] as? Number)?.toDouble(),
                                averageAnnualSunHours = (data["averageAnnualSunHours"] as? Number)?.toDouble(),
                                systemKwp = (data["systemKwp"] as? Number)?.toDouble() ?: 0.0,
                                estimatedGeneration = (data["estimatedGeneration"] as? Number)?.toDouble() ?: 0.0,
                                monthlySavings = (data["monthlySavings"] as? Number)?.toDouble() ?: 0.0,
                                paybackMonths = (data["paybackMonths"] as? Number)?.toInt() ?: 0,
                                companyName = data["companyName"] as? String ?: "",
                                companyPhone = data["companyPhone"] as? String ?: "",
                                companyEmail = data["companyEmail"] as? String ?: "",
                                consultantName = data["consultantName"] as? String ?: "",
                                consultantPhone = data["consultantPhone"] as? String ?: "",
                                consultantEmail = data["consultantEmail"] as? String ?: "",
                                userId = data["userId"] as? String ?: "",
                                createdAt = data["createdAt"] as? com.google.firebase.Timestamp,
                                updatedAt = data["updatedAt"] as? com.google.firebase.Timestamp
                            )
                            quote
                        } catch (e: Exception) {
                            null
                        }
                    }
                    
                    
                    // Filter quotes from the last 7 days
                    val sevenDaysAgo = Calendar.getInstance().apply {
                        add(Calendar.DAY_OF_YEAR, -7)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.time
                    
                    
                    val recentQuotes = allQuotes.filter { quote ->
                        val createdAt = quote.createdAt?.toDate()
                        val isRecent = createdAt?.let { it >= sevenDaysAgo } ?: false
                        isRecent
                    }.sortedByDescending { it.createdAt?.toDate() }
                    .take(5) // Get the 5 most recent quotes from last 7 days
                    
                    
                    if (recentQuotes.isEmpty() && allQuotes.isNotEmpty()) {
                        val allRecentQuotes = allQuotes.sortedByDescending { it.createdAt?.toDate() }.take(5)
                        displayRecentQuotes(allRecentQuotes)
                    } else {
                        displayRecentQuotes(recentQuotes)
                    }
                } else {
                    // Fallback to ViewModel if API fails
                    loadRecentQuotesFromViewModel()
                }
            } catch (e: Exception) {
                // Fallback to ViewModel if API fails
                loadRecentQuotesFromViewModel()
            }
        }
    }
    
    private fun loadRecentQuotesFromViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                quotesViewModel.quotes.collect { quotes ->
                    
                    if (quotes.isNotEmpty()) {
                    }
                    
                    // Filter quotes from the last 7 days
                    val sevenDaysAgo = Calendar.getInstance().apply {
                        add(Calendar.DAY_OF_YEAR, -7)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.time
                    
                    val recentQuotes = quotes.filter { quote ->
                        val createdAt = quote.createdAt?.toDate()
                        val isRecent = createdAt?.let { it >= sevenDaysAgo } ?: false
                        isRecent
                    }.sortedByDescending { it.createdAt?.toDate() }
                    .take(5)
                    
                    
                    if (recentQuotes.isEmpty() && quotes.isNotEmpty()) {
                        val allRecentQuotes = quotes.sortedByDescending { it.createdAt?.toDate() }.take(5)
                        displayRecentQuotes(allRecentQuotes)
                    } else {
                        displayRecentQuotes(recentQuotes)
                    }
                }
            } catch (e: Exception) {
                displayEmptyQuotes()
            }
        }
    }
    
    private fun displayRecentQuotes(quotes: List<FirebaseQuote>) {
        layoutRecentQuotes.removeAllViews()
        
        if (quotes.isEmpty()) {
            displayEmptyQuotes()
            return
        }
        
        quotes.forEach { quote ->
            try {
                val quoteView = createQuoteItemView(quote)
                layoutRecentQuotes.addView(quoteView)
            } catch (e: Exception) {
                // Continue with other quotes even if one fails
            }
        }
    }
    
    private fun createQuoteItemView(quote: FirebaseQuote): View {
        // Create MaterialCardView similar to view quotes page
        val cardView = com.google.android.material.card.MaterialCardView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 4, 0, 4)
            }
            radius = 6f
            elevation = 1f
            setCardBackgroundColor(android.graphics.Color.WHITE)
            isClickable = true
            isFocusable = true
            // Use a safe drawable resource instead of android.R.attr.selectableItemBackground
            try {
                foreground = context.getDrawable(android.R.drawable.list_selector_background)
            } catch (e: Exception) {
                // Set a simple ripple effect instead using ColorStateList
                val rippleColor = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#1A000000"))
                setRippleColor(rippleColor)
            }
        }
        
        // Main content layout
        val contentLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(12, 12, 12, 12)
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        
        // Remove quote icon - no longer needed
        
        // Quote details
        val detailsLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
            setPadding(12, 0, 0, 0)
        }
        
        // Reference and address
        val referenceText = TextView(requireContext()).apply {
            val reference = if (quote.reference.isNotEmpty()) quote.reference else "REF-${quote.id?.takeLast(5) ?: "00000"}"
            text = reference
            textSize = 13f
            setTextColor(android.graphics.Color.BLACK)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        
        val addressText = TextView(requireContext()).apply {
            text = quote.address.ifEmpty { "Address not available" }
            textSize = 11f
            setTextColor(android.graphics.Color.parseColor("#666666"))
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        
        // Date
        val dateText = TextView(requireContext()).apply {
            val dateString = quote.createdAt?.toDate()?.let {
                SimpleDateFormat("dd MMM", Locale.getDefault()).format(it)
            } ?: "Unknown"
            text = dateString
            textSize = 11f
            setTextColor(resources.getColor(R.color.solora_orange, null))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        
        detailsLayout.addView(referenceText)
        detailsLayout.addView(addressText)
        
        contentLayout.addView(detailsLayout)
        contentLayout.addView(dateText)
        
        cardView.addView(contentLayout)
        
        // Click listener for quote item
        cardView.setOnClickListener {
            if (!quote.id.isNullOrBlank()) {
                val bundle = Bundle().apply { putString("id", quote.id) }
                findNavController().navigate(R.id.quoteDetailFragment, bundle)
            }
        }
        
        return cardView
    }
    
    private fun displayEmptyQuotes() {
        layoutRecentQuotes.removeAllViews()
        
        val emptyView = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }

        val emptyText = TextView(requireContext()).apply {
            text = getString(R.string.no_quotes_last_7_days)
            textSize = 14f
            setTextColor(android.graphics.Color.parseColor("#666666"))
            gravity = android.view.Gravity.CENTER
        }

        val subText = TextView(requireContext()).apply {
            text = getString(R.string.create_new_quote_here)
            textSize = 12f
            setTextColor(android.graphics.Color.parseColor("#999999"))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 8, 0, 0)
        }


        emptyView.addView(emptyText)
        emptyView.addView(subText)
        layoutRecentQuotes.addView(emptyView)
    }
    
    private fun performApiRefresh() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Refresh quotes count via API
                val quotesResult = apiService.getQuotes(search = null, limit = 100) // Set a reasonable limit
                if (quotesResult.isSuccess) {
                    val quotes = quotesResult.getOrNull() ?: emptyList()
                    tvQuotesCount.text = quotes.size.toString()
                }
                
                // Refresh leads count via API
                val leadsResult = apiService.getLeads(search = null, status = null, limit = 100) // Set a reasonable limit
                if (leadsResult.isSuccess) {
                    val leads = leadsResult.getOrNull() ?: emptyList()
                    tvLeadsCount.text = leads.size.toString()
                }
                
                // Refresh settings via API to ensure company info is up to date
                val settingsResult = apiService.getSettings()
                if (settingsResult.isSuccess) {
                    val settingsData = settingsResult.getOrNull()
                    if (settingsData != null) {
                        val companyName = settingsData["companyName"] as? String ?: "SOLORA"
                        tvCompanyName.text = companyName
                    }
                }
                
                // Refresh user profile via API to ensure consultant name is up to date
                val profileResult = apiService.getUserProfile()
                if (profileResult.isSuccess) {
                    val profileData = profileResult.getOrNull()
                    if (profileData != null) {
                        val name = profileData["name"] as? String ?: ""
                        val surname = profileData["surname"] as? String ?: ""
                        val fullName = if (surname.isNotEmpty() && surname != name) {
                            "$name $surname"
                        } else if (name.isNotEmpty()) {
                            name
                        } else {
                            "Not Set"
                        }
                        tvConsultantName.text = "Welcome back, $fullName"
                    }
                }
                
                // Sync any pending data
                val syncData = mapOf<String, Any>(
                    "timestamp" to System.currentTimeMillis(),
                    "source" to "home_refresh"
                )
                val syncResult = apiService.syncData(syncData)
                if (syncResult.isSuccess) {
                }
                
            } catch (e: Exception) {
            }
        }
    }
}