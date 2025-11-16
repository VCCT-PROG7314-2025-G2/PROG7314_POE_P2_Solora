package dev.solora.pdf

import android.content.Context
import android.os.Environment
import com.itextpdf.html2pdf.HtmlConverter
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import dev.solora.data.FirebaseQuote
import dev.solora.settings.CompanySettings
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Generates professional PDF quotes from FirebaseQuote data
 * Creates HTML template and converts to PDF using iText library
 */
class PdfGenerator(private val context: Context) {
    
    /**
     * Generate PDF quote file
     * Saves to app's Documents/Quotes directory with timestamp
     */
    fun generateQuotePdf(quote: FirebaseQuote): File? {
        return generateQuotePdf(quote, CompanySettings())
    }
    
    fun generateQuotePdf(quote: FirebaseQuote, companySettings: CompanySettings): File? {
        return try {
            // Create PDF directory if it doesn't exist
            val pdfDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Quotes")
            if (!pdfDir.exists()) {
                pdfDir.mkdirs()
            }
            
            // Generate filename with timestamp
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val reference = if (quote.reference.isNotEmpty()) quote.reference else "REF-${quote.id?.takeLast(5) ?: "00000"}"
            val filename = "Solora_Quote_${reference}_$timestamp.pdf"
            val pdfFile = File(pdfDir, filename)
            
            // Generate HTML content
            val htmlContent = generateQuoteHtml(quote, companySettings)
            
            // Convert HTML to PDF
            val outputStream = FileOutputStream(pdfFile)
            HtmlConverter.convertToPdf(htmlContent, outputStream)
            outputStream.close()
            
            pdfFile
            
        } catch (e: Exception) {
            null
        }
    }
    
    private fun generateQuoteHtml(quote: FirebaseQuote, companySettings: CompanySettings = CompanySettings()): String {
        val reference = if (quote.reference.isNotEmpty()) quote.reference else "REF-${quote.id?.takeLast(5) ?: "00000"}"
        val dateText = quote.createdAt?.toDate()?.let {
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(it)
        } ?: "Unknown"
        
        val monthlySavings = if (quote.monthlySavings > 0) {
            "R ${String.format("%.2f", quote.monthlySavings)}"
        } else {
            "R 0.00"
        }
        
        val systemSize = quote.systemKwp
        val panelCount = if (systemSize > 0 && quote.panelWatt > 0) {
            (systemSize * 1000 / quote.panelWatt).toInt()
        } else if (systemSize > 0) {
            (systemSize * 1000 / 420).toInt() // Default 420W panels
        } else {
            0
        }
        
        val systemSizeText = if (systemSize > 0) "${String.format("%.1f", systemSize)}KW" else "0.0KW"
        val inverterSizeText = if (systemSize > 0) "${String.format("%.1f", systemSize * 0.8)}KW" else "0.0KW"
        
        val coverage = if (systemSize > 0 && quote.usageKwh != null && quote.usageKwh > 0) {
            val monthlyGeneration = systemSize * (quote.averageAnnualSunHours ?: 5.0) * 30
            ((monthlyGeneration / quote.usageKwh) * 100).toInt().coerceAtMost(100)
        } else {
            0
        }
        
        val systemCost = if (systemSize > 0) {
            // Industry standard: R15,000 per kW installation cost
            systemSize * 15000
        } else {
            0.0
        }
        
        val tax = systemCost * 0.15 // 15% VAT (South African standard)
        val totalCost = systemCost + tax
        
        return """
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Solora Quote - $reference</title>
    <style>
        body {
            font-family: Arial, sans-serif;
            margin: 0;
            padding: 20px;
            background-color: #f5f5f5;
        }
        .container {
            max-width: 800px;
            margin: 0 auto;
            background-color: white;
            padding: 40px;
            border-radius: 8px;
            box-shadow: 0 4px 6px rgba(0,0,0,0.1);
        }
        .header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 30px;
            border-bottom: 2px solid #FF6B35;
            padding-bottom: 20px;
        }
        .company-info h1 {
            color: #FF6B35;
            font-size: 32px;
            margin: 0;
            font-weight: bold;
        }
        .company-info p {
            color: #333;
            margin: 5px 0 0 0;
            font-size: 14px;
        }
        .quote-ref {
            color: #FF6B35;
            font-size: 16px;
            font-weight: bold;
            text-align: right;
        }
        .section {
            margin-bottom: 25px;
        }
        .section-title {
            color: #333;
            font-size: 18px;
            font-weight: bold;
            margin-bottom: 15px;
            border-bottom: 1px solid #eee;
            padding-bottom: 5px;
        }
        .detail-row {
            display: flex;
            margin-bottom: 8px;
        }
        .detail-label {
            font-weight: bold;
            min-width: 180px;
            color: #333;
        }
        .detail-value {
            color: #333;
        }
        .highlight {
            color: #FF6B35;
            font-weight: bold;
        }
        .cost-section {
            margin-top: 20px;
        }
        .cost-row {
            display: flex;
            justify-content: space-between;
            margin-bottom: 8px;
            padding: 5px 0;
        }
        .total-row {
            background-color: #f5f5f5;
            padding: 15px;
            margin-top: 10px;
            border-radius: 5px;
            font-weight: bold;
            font-size: 16px;
        }
        .total-label {
            color: #333;
        }
        .total-value {
            color: #FF6B35;
        }
        .footer {
            margin-top: 50px;
            border-top: 3px solid #FF6B35;
            padding-top: 40px;
            background-color: #fafafa;
            padding: 40px 30px;
            border-radius: 8px;
        }
        .footer-content {
            display: flex;
            justify-content: space-between;
            margin-bottom: 30px;
            gap: 40px;
        }
        .company-details {
            flex: 1;
        }
        .company-details h3 {
            color: #FF6B35;
            font-size: 18px;
            margin: 0 0 15px 0;
            font-weight: bold;
            border-bottom: 1px solid #FF6B35;
            padding-bottom: 5px;
        }
        .company-details p {
            margin: 8px 0;
            color: #333;
            font-size: 13px;
            line-height: 1.5;
        }
        .company-details strong {
            color: #555;
            font-weight: 600;
        }
        .quote-info {
            flex: 1;
            text-align: right;
        }
        .quote-info p {
            margin: 8px 0;
            color: #666;
            font-size: 13px;
            line-height: 1.5;
        }
        .quote-info strong {
            color: #555;
            font-weight: 600;
        }
        .footer-bottom {
            text-align: center;
            color: #666;
            font-size: 12px;
            border-top: 1px solid #ddd;
            padding-top: 20px;
            margin-top: 25px;
            line-height: 1.6;
        }
        .footer-bottom p {
            margin: 5px 0;
        }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <div class="company-info">
                <h1>${if (companySettings.companyName.isNotEmpty()) companySettings.companyName else "SOLORA"}</h1>
                <p>Solar Solutions</p>
            </div>
            <div class="quote-ref">
                Ref no. $reference
            </div>
        </div>
        
        <div class="section">
            <div class="section-title">Client Information</div>
            <div class="detail-row">
                <div class="detail-label">Name:</div>
                <div class="detail-value">${quote.clientName.ifEmpty { "Temporary Client" }}</div>
            </div>
            <div class="detail-row">
                <div class="detail-label">Address:</div>
                <div class="detail-value">${quote.address.ifEmpty { "Address not available" }}</div>
            </div>
            <div class="detail-row">
                <div class="detail-label">Date:</div>
                <div class="detail-value">$dateText</div>
            </div>
        </div>
        
        <div class="section">
            <div class="section-title">Customer Requirements</div>
            ${if (quote.usageKwh != null) """
            <div class="detail-row">
                <div class="detail-label">Monthly Usage:</div>
                <div class="detail-value">${String.format("%.1f", quote.usageKwh)} kWh</div>
            </div>
            """ else ""}
            ${if (quote.billRands != null) """
            <div class="detail-row">
                <div class="detail-label">Monthly Bill:</div>
                <div class="detail-value">R ${String.format("%.2f", quote.billRands)}</div>
            </div>
            """ else ""}
            <div class="detail-row">
                <div class="detail-label">Electricity Tariff:</div>
                <div class="detail-value">R ${String.format("%.2f", quote.tariff)} per kWh</div>
            </div>
        </div>
        
        <div class="section">
            <div class="section-title">System Specifications</div>
            <div class="detail-row">
                <div class="detail-label">Estimated Monthly Savings:</div>
                <div class="detail-value highlight">$monthlySavings</div>
            </div>
            <div class="detail-row">
                <div class="detail-label">Recommended Panels:</div>
                <div class="detail-value">$panelCount x ${quote.panelWatt}W</div>
            </div>
            <div class="detail-row">
                <div class="detail-label">Total System Size:</div>
                <div class="detail-value">$systemSizeText</div>
            </div>
            <div class="detail-row">
                <div class="detail-label">Recommended Inverter:</div>
                <div class="detail-value">$inverterSizeText</div>
            </div>
            <div class="detail-row">
                <div class="detail-label">Coverage of monthly usage:</div>
                <div class="detail-value">${coverage}%</div>
            </div>
            ${if (quote.paybackMonths > 0) """
            <div class="detail-row">
                <div class="detail-label">Payback Period:</div>
                <div class="detail-value">${quote.paybackMonths} months (${String.format("%.1f", quote.paybackMonths / 12.0)} years)</div>
            </div>
            """ else ""}
        </div>
        
        <div class="section cost-section">
            <div class="section-title">Cost Breakdown</div>
            <div class="cost-row">
                <div class="detail-label">System Cost:</div>
                <div class="detail-value">R ${String.format("%.2f", systemCost)}</div>
            </div>
            <div class="cost-row">
                <div class="detail-label">Tax:</div>
                <div class="detail-value">R ${String.format("%.2f", tax)}</div>
            </div>
            <div class="total-row">
                <div class="total-label">Total:</div>
                <div class="total-value">R ${String.format("%.2f", totalCost)}</div>
            </div>
        </div>
        
        <div class="footer">
            <div class="footer-content">
                <div class="company-details">
                    <h3>${if (companySettings.companyName.isNotEmpty()) companySettings.companyName else "SOLORA"}</h3>
                    ${if (companySettings.companyAddress.isNotEmpty()) "<p><strong>Address:</strong><br>${companySettings.companyAddress}</p>" else ""}
                    ${if (companySettings.companyPhone.isNotEmpty()) "<p><strong>Phone:</strong> ${companySettings.companyPhone}</p>" else ""}
                    ${if (companySettings.companyEmail.isNotEmpty()) "<p><strong>Email:</strong> ${companySettings.companyEmail}</p>" else ""}
                    ${if (companySettings.companyWebsite.isNotEmpty()) "<p><strong>Website:</strong> ${companySettings.companyWebsite}</p>" else ""}
                    ${if (companySettings.consultantName.isNotEmpty()) "<p><strong>Consultant:</strong> ${companySettings.consultantName}</p>" else ""}
                    ${if (companySettings.consultantLicense.isNotEmpty()) "<p><strong>License:</strong> ${companySettings.consultantLicense}</p>" else ""}
                </div>
                <div class="quote-info">
                    <p><strong>Quote Reference:</strong><br>$reference</p>
                    <p><strong>Date Issued:</strong><br>$dateText</p>
                    <p><strong>Valid Until:</strong><br>${SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 30) }.time)}</p>
                    <p><strong>Generated:</strong><br>${SimpleDateFormat("dd MMM yyyy 'at' HH:mm", Locale.getDefault()).format(Date())}</p>
                </div>
            </div>
            <div class="footer-bottom">
                <p><strong>Quote Validity:</strong> This quote is valid for 30 days from the date of issue.</p>
                ${if (companySettings.quoteFooter.isNotEmpty()) "<p>${companySettings.quoteFooter}</p>" else ""}
                <p><em>Thank you for choosing ${if (companySettings.companyName.isNotEmpty()) companySettings.companyName else "SOLORA"} for your solar energy needs.</em></p>
            </div>
        </div>
    </div>
</body>
</html>
        """.trimIndent()
    }
}
