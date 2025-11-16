package dev.solora.dashboard

import com.google.firebase.Timestamp
import dev.solora.data.FirebaseQuote
import org.junit.Assert.*
import org.junit.Test
import java.util.*

/**
 * Unit tests for DashboardData calculations
 * Tests dashboard statistics and analytics calculations
 */
class DashboardDataTest {

    @Test
    fun `test calculateDashboardData with empty list`() {
        // Given
        val quotes = emptyList<FirebaseQuote>()

        // When
        val result = calculateDashboardData(quotes)

        // Then
        assertEquals(0, result.totalQuotes)
        assertEquals(0.0, result.averageSystemSize, 0.001)
        assertEquals(0.0, result.totalRevenue, 0.001)
        assertEquals(0.0, result.averageMonthlySavings, 0.001)
    }

    @Test
    fun `test calculateDashboardData with single quote`() {
        // Given
        val quote = FirebaseQuote(
            id = "quote-1",
            reference = "QUOTE-001",
            clientName = "Test Client",
            address = "Test Address",
            systemKwp = 5.5,
            monthlySavings = 1000.0,
            userId = "user-1"
        )
        val quotes = listOf(quote)

        // When
        val result = calculateDashboardData(quotes)

        // Then
        assertEquals(1, result.totalQuotes)
        assertEquals(5.5, result.averageSystemSize, 0.001)
        assertEquals(82500.0, result.totalRevenue, 0.001) // 5.5 * 15000
        assertEquals(1000.0, result.averageMonthlySavings, 0.001)
    }

    @Test
    fun `test calculateDashboardData with multiple quotes`() {
        // Given
        val quotes = listOf(
            FirebaseQuote(
                id = "quote-1",
                reference = "QUOTE-001",
                clientName = "Client 1",
                address = "Address 1",
                systemKwp = 3.0,
                monthlySavings = 500.0,
                userId = "user-1"
            ),
            FirebaseQuote(
                id = "quote-2",
                reference = "QUOTE-002",
                clientName = "Client 2",
                address = "Address 2",
                systemKwp = 5.5,
                monthlySavings = 1000.0,
                userId = "user-1"
            ),
            FirebaseQuote(
                id = "quote-3",
                reference = "QUOTE-003",
                clientName = "Client 3",
                address = "Address 1",
                systemKwp = 7.5,
                monthlySavings = 1500.0,
                userId = "user-1"
            )
        )

        // When
        val result = calculateDashboardData(quotes)

        // Then
        assertEquals(3, result.totalQuotes)
        assertEquals(5.33, result.averageSystemSize, 0.01) // (3.0 + 5.5 + 7.5) / 3
        assertEquals(240000.0, result.totalRevenue, 0.001) // (3.0 + 5.5 + 7.5) * 15000
        assertEquals(1000.0, result.averageMonthlySavings, 0.001) // (500 + 1000 + 1500) / 3
    }

    @Test
    fun `test system size distribution`() {
        // Given
        val quotes = listOf(
            FirebaseQuote(systemKwp = 2.0, userId = "user-1", reference = "Q1", clientName = "C1", address = "A1"),
            FirebaseQuote(systemKwp = 4.0, userId = "user-1", reference = "Q2", clientName = "C2", address = "A2"),
            FirebaseQuote(systemKwp = 8.0, userId = "user-1", reference = "Q3", clientName = "C3", address = "A3"),
            FirebaseQuote(systemKwp = 12.0, userId = "user-1", reference = "Q4", clientName = "C4", address = "A4")
        )

        // When
        val result = calculateDashboardData(quotes)

        // Then
        assertEquals(1, result.systemSizeDistribution.size0to3kw)
        assertEquals(1, result.systemSizeDistribution.size3to6kw)
        assertEquals(1, result.systemSizeDistribution.size6to10kw)
        assertEquals(1, result.systemSizeDistribution.size10kwPlus)
    }

    @Test
    fun `test top locations calculation`() {
        // Given
        val quotes = listOf(
            FirebaseQuote(address = "Cape Town", systemKwp = 5.0, userId = "user-1", reference = "Q1", clientName = "C1"),
            FirebaseQuote(address = "Cape Town", systemKwp = 6.0, userId = "user-1", reference = "Q2", clientName = "C2"),
            FirebaseQuote(address = "Johannesburg", systemKwp = 4.0, userId = "user-1", reference = "Q3", clientName = "C3"),
            FirebaseQuote(address = "Durban", systemKwp = 3.0, userId = "user-1", reference = "Q4", clientName = "C4")
        )

        // When
        val result = calculateDashboardData(quotes)

        // Then
        assertTrue(result.topLocations.isNotEmpty())
        assertEquals("Cape Town", result.topLocations[0].location)
        assertEquals(2, result.topLocations[0].count)
        assertEquals(5.5, result.topLocations[0].averageSystemSize, 0.001) // (5.0 + 6.0) / 2
    }

    @Test
    fun `test monthly performance calculation`() {
        // Given
        val calendar = Calendar.getInstance()
        val currentMonth = calendar.get(Calendar.MONTH)
        val currentYear = calendar.get(Calendar.YEAR)
        
        val thisMonthDate = Calendar.getInstance().apply {
            set(Calendar.YEAR, currentYear)
            set(Calendar.MONTH, currentMonth)
            set(Calendar.DAY_OF_MONTH, 15)
        }.time
        
        val lastMonthDate = Calendar.getInstance().apply {
            set(Calendar.YEAR, if (currentMonth == 0) currentYear - 1 else currentYear)
            set(Calendar.MONTH, if (currentMonth == 0) 11 else currentMonth - 1)
            set(Calendar.DAY_OF_MONTH, 15)
        }.time

        val quotes = listOf(
            FirebaseQuote(
                id = "quote-1",
                reference = "QUOTE-001",
                clientName = "Client 1",
                address = "Address 1",
                systemKwp = 5.0,
                userId = "user-1",
                createdAt = Timestamp(thisMonthDate)
            ),
            FirebaseQuote(
                id = "quote-2",
                reference = "QUOTE-002",
                clientName = "Client 2",
                address = "Address 2",
                systemKwp = 5.0,
                userId = "user-1",
                createdAt = Timestamp(thisMonthDate)
            ),
            FirebaseQuote(
                id = "quote-3",
                reference = "QUOTE-003",
                clientName = "Client 3",
                address = "Address 3",
                systemKwp = 5.0,
                userId = "user-1",
                createdAt = Timestamp(lastMonthDate)
            )
        )

        // When
        val result = calculateDashboardData(quotes)

        // Then
        assertEquals(2, result.monthlyPerformance.quotesThisMonth)
        assertEquals(1, result.monthlyPerformance.quotesLastMonth)
        assertTrue(result.monthlyPerformance.growthPercentage >= 0)
    }

    @Test
    fun `test quotes with null system size are handled`() {
        // Given
        val quotes = listOf(
            FirebaseQuote(
                id = "quote-1",
                reference = "QUOTE-001",
                clientName = "Client 1",
                address = "Address 1",
                systemKwp = 0.0, // null equivalent
                userId = "user-1"
            ),
            FirebaseQuote(
                id = "quote-2",
                reference = "QUOTE-002",
                clientName = "Client 2",
                address = "Address 2",
                systemKwp = 5.0,
                userId = "user-1"
            )
        )

        // When
        val result = calculateDashboardData(quotes)

        // Then
        assertEquals(2, result.totalQuotes)
        assertEquals(2.5, result.averageSystemSize, 0.001) // (0.0 + 5.0) / 2
    }

    @Test
    fun `test quotes with null monthly savings are handled`() {
        // Given
        val quotes = listOf(
            FirebaseQuote(
                id = "quote-1",
                reference = "QUOTE-001",
                clientName = "Client 1",
                address = "Address 1",
                systemKwp = 5.0,
                monthlySavings = 0.0, // null equivalent
                userId = "user-1"
            ),
            FirebaseQuote(
                id = "quote-2",
                reference = "QUOTE-002",
                clientName = "Client 2",
                address = "Address 2",
                systemKwp = 5.0,
                monthlySavings = 1000.0,
                userId = "user-1"
            )
        )

        // When
        val result = calculateDashboardData(quotes)

        // Then
        assertEquals(2, result.totalQuotes)
        assertEquals(500.0, result.averageMonthlySavings, 0.001) // (0.0 + 1000.0) / 2
    }
}


