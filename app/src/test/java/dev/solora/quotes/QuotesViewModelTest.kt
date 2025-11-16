package dev.solora.quotes

import dev.solora.quote.QuoteOutputs
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for QuotesViewModel
 * Tests quote calculation state management
 */
class QuotesViewModelTest {

    @Test
    fun `test CalculationState sealed class structure`() {
        // Test that CalculationState sealed class has all expected states
        val idle = CalculationState.Idle
        val loading = CalculationState.Loading
        val success = CalculationState.Success(
            QuoteOutputs(
                panels = 10,
                systemKw = 5.5,
                inverterKw = 4.4,
                estimatedMonthlySavingsR = 1000.0
            )
        )
        val error = CalculationState.Error("Test error")

        assertNotNull(idle)
        assertNotNull(loading)
        assertNotNull(success)
        assertNotNull(success.outputs)
        assertEquals(10, success.outputs.panels)
        assertNotNull(error)
        assertEquals("Test error", error.message)
    }

    @Test
    fun `test CalculationState Success contains QuoteOutputs`() {
        // Given
        val outputs = QuoteOutputs(
            panels = 12,
            systemKw = 6.6,
            inverterKw = 5.28,
            estimatedMonthlySavingsR = 1200.0,
            monthlyUsageKwh = 600.0,
            tariffRPerKwh = 2.5,
            panelWatt = 550,
            estimatedMonthlyGeneration = 1188.0,
            paybackMonths = 60
        )

        // When
        val successState = CalculationState.Success(outputs)

        // Then
        assertEquals(12, successState.outputs.panels)
        assertEquals(6.6, successState.outputs.systemKw, 0.001)
        assertEquals(1200.0, successState.outputs.estimatedMonthlySavingsR, 0.001)
    }

    @Test
    fun `test CalculationState Error message is accessible`() {
        // Given
        val errorMessage = "Calculation failed: Invalid input"
        val errorState = CalculationState.Error(errorMessage)

        // Then
        assertEquals(errorMessage, errorState.message)
    }
}


