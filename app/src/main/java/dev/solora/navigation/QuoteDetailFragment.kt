package dev.solora.navigation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import dev.solora.R
import dev.solora.quotes.QuotesViewModel
import dev.solora.pdf.PdfGenerator
import dev.solora.pdf.FileShareUtils
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.File

class QuoteDetailFragment : Fragment() {
    private val quotesViewModel: QuotesViewModel by viewModels()
    private lateinit var pdfGenerator: PdfGenerator
    
    // UI Components
    private lateinit var btnBackDetail: ImageButton
    private lateinit var tvQuoteReference: TextView
    private lateinit var tvClientName: TextView
    private lateinit var tvClientAddress: TextView
    private lateinit var tvQuoteDate: TextView
    private lateinit var tvMonthlySavings: TextView
    private lateinit var tvRecommendedPanels: TextView
    private lateinit var tvSystemSize: TextView
    private lateinit var tvRecommendedInverter: TextView
    private lateinit var tvCoverage: TextView
    private lateinit var tvSystemCost: TextView
    private lateinit var tvTax: TextView
    private lateinit var tvTotalCost: TextView
    private lateinit var btnExportPdf: Button
    
    private var currentQuote: dev.solora.data.FirebaseQuote? = null
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_quote_detail, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        pdfGenerator = PdfGenerator(requireContext())
        initializeViews(view)
        setupClickListeners()
        
        val quoteId = requireArguments().getString("id") ?: ""
        if (quoteId.isNotEmpty()) {
            loadQuoteDetails(quoteId)
        } else {
            Toast.makeText(requireContext(), "Quote ID not provided", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
        }
    }
    
    private fun initializeViews(view: View) {
        btnBackDetail = view.findViewById(R.id.btn_back_detail)
        tvQuoteReference = view.findViewById(R.id.tv_quote_reference)
        tvClientName = view.findViewById(R.id.tv_client_name)
        tvClientAddress = view.findViewById(R.id.tv_client_address)
        tvQuoteDate = view.findViewById(R.id.tv_quote_date)
        tvMonthlySavings = view.findViewById(R.id.tv_monthly_savings)
        tvRecommendedPanels = view.findViewById(R.id.tv_recommended_panels)
        tvSystemSize = view.findViewById(R.id.tv_system_size)
        tvRecommendedInverter = view.findViewById(R.id.tv_recommended_inverter)
        tvCoverage = view.findViewById(R.id.tv_coverage)
        tvSystemCost = view.findViewById(R.id.tv_system_cost)
        tvTax = view.findViewById(R.id.tv_tax)
        tvTotalCost = view.findViewById(R.id.tv_total_cost)
        btnExportPdf = view.findViewById(R.id.btn_export_pdf)
    }
    
    private fun setupClickListeners() {
        btnBackDetail.setOnClickListener {
            // Navigate back to quotes fragment and show view tab (tab 1)
            val bundle = Bundle().apply {
                putInt("show_tab", 1) // 1 = view tab
            }
            findNavController().navigate(R.id.action_quote_detail_to_quotes, bundle)
        }
        
        btnExportPdf.setOnClickListener {
            exportToPdf()
        }
    }
    
    private fun loadQuoteDetails(quoteId: String) {
        // Hide content initially to prevent showing demo data
        hideContent()
        
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val quote = quotesViewModel.getQuoteById(quoteId)
                if (quote != null) {
                    currentQuote = quote
                    displayQuoteDetails(quote)
                    showContent() // Show content only after real data is loaded
                } else {
                    Toast.makeText(requireContext(), "Quote not found", Toast.LENGTH_SHORT).show()
                    findNavController().popBackStack()
                }
            } catch (e: Exception) {
                // ("QuoteDetailFragment", "Error loading quote: ${e.message}", e)
                Toast.makeText(requireContext(), "Error loading quote details", Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
            }
        }
    }
    
    private fun hideContent() {
        // Hide all content TextViews to prevent showing demo data
        tvQuoteReference.visibility = View.INVISIBLE
        tvClientName.visibility = View.INVISIBLE
        tvClientAddress.visibility = View.INVISIBLE
        tvQuoteDate.visibility = View.INVISIBLE
        tvMonthlySavings.visibility = View.INVISIBLE
        tvRecommendedPanels.visibility = View.INVISIBLE
        tvSystemSize.visibility = View.INVISIBLE
        tvRecommendedInverter.visibility = View.INVISIBLE
        tvCoverage.visibility = View.INVISIBLE
        tvSystemCost.visibility = View.INVISIBLE
        tvTax.visibility = View.INVISIBLE
        tvTotalCost.visibility = View.INVISIBLE
    }
    
    private fun showContent() {
        // Show all content TextViews after real data is loaded
        tvQuoteReference.visibility = View.VISIBLE
        tvClientName.visibility = View.VISIBLE
        tvClientAddress.visibility = View.VISIBLE
        tvQuoteDate.visibility = View.VISIBLE
        tvMonthlySavings.visibility = View.VISIBLE
        tvRecommendedPanels.visibility = View.VISIBLE
        tvSystemSize.visibility = View.VISIBLE
        tvRecommendedInverter.visibility = View.VISIBLE
        tvCoverage.visibility = View.VISIBLE
        tvSystemCost.visibility = View.VISIBLE
        tvTax.visibility = View.VISIBLE
        tvTotalCost.visibility = View.VISIBLE
    }
    
    private fun displayQuoteDetails(quote: dev.solora.data.FirebaseQuote) {
        // Reference number
        val reference = if (quote.reference.isNotEmpty()) {
            quote.reference
        } else {
            "REF-${quote.id?.takeLast(5) ?: "00000"}"
        }
        tvQuoteReference.text = getString(R.string.reference_number_with_value, reference)
        
        // Client details
        tvClientName.text = quote.clientName.ifEmpty { "Temporary Client" }
        tvClientAddress.text = quote.address.ifEmpty { "Address not available" }
        
        // Date
        val dateText = quote.createdAt?.toDate()?.let {
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(it)
        } ?: "Unknown"
        tvQuoteDate.text = dateText
        
        // Monthly savings
        val monthlySavings = if (quote.monthlySavings > 0) {
            "R ${String.format("%.2f", quote.monthlySavings)}"
        } else {
            "R 1,170.00" // Default value
        }
        tvMonthlySavings.text = monthlySavings
        
        // System details
        val systemSize = quote.systemKwp
        val panelCount = if (systemSize > 0) {
            (systemSize * 1000 / 550).toInt() // Assuming 550W panels
        } else {
            12 // Default
        }
        
        tvRecommendedPanels.text = "$panelCount x 550W"
        tvSystemSize.text = if (systemSize > 0) "${String.format("%.1f", systemSize)}KW" else "6.6KW"
        tvRecommendedInverter.text = if (systemSize > 0) "${String.format("%.0f", systemSize)}KW" else "6KW"
        
        // Coverage (estimated based on system size)
        val coverage = if (systemSize > 0) {
            ((systemSize * 150) / 1000 * 100).toInt() // Rough estimation
        } else {
            95 // Default
        }
        tvCoverage.text = "${coverage}%"
        
        // Cost breakdown
        val systemCost = if (systemSize > 0) {
            systemSize * 8500 // R8500 per kW
        } else {
            56700.0 // Default
        }
        
        val tax = systemCost * 0.15 // 15% VAT
        val totalCost = systemCost + tax
        
        tvSystemCost.text = "R ${String.format("%.2f", systemCost)}"
        tvTax.text = "R ${String.format("%.2f", tax)}"
        tvTotalCost.text = "R ${String.format("%.2f", totalCost)}"
        
        // Quote details displayed for: $reference
    }
    
    private fun exportToPdf() {
        currentQuote?.let { quote ->
            // Navigate to PDF preview page instead of directly exporting
            val bundle = Bundle().apply {
                putString("quoteId", quote.id ?: "")
            }
            findNavController().navigate(R.id.quotePdfPreviewFragment, bundle)
        } ?: run {
            Toast.makeText(requireContext(), "No quote data available", Toast.LENGTH_SHORT).show()
        }
    }
}