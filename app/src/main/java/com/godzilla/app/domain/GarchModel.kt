package com.godzilla.app.domain

import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

class GarchModel(
    private val omega: Double = 0.000001, // Long-run variance weight
    private val alpha: Double = 0.1,      // ARCH term (reaction to recent shocks)
    private val beta: Double = 0.85       // GARCH term (persistence of volatility)
) {
    /**
     * Calculates the next period's volatility based on the GARCH(1,1) model.
     *
     * @param currentPrice The current asset price.
     * @param previousPrice The previous asset price.
     * @param previousVolatility The previous period's volatility (sigma).
     * @return The forecasted volatility (sigma) for the next period.
     */
    fun calculateNextVolatility(
        currentPrice: Double,
        previousPrice: Double,
        previousVolatility: Double
    ): Double {
        if (previousPrice <= 0 || currentPrice <= 0) return previousVolatility

        // Calculate return: r_t = ln(P_t / P_{t-1})
        val returns = ln(currentPrice / previousPrice)

        // GARCH(1,1) formula: sigma_t^2 = omega + alpha * r_{t-1}^2 + beta * sigma_{t-1}^2
        // Note: In the formula provided, r_{t-1} is the return just observed.
        // We are forecasting sigma_t based on information up to t-1.
        // So here, 'returns' corresponds to r_{t-1} in the formula context if we are at time t.
        
        val prevVariance = previousVolatility.pow(2)
        val returnSquared = returns.pow(2)

        val nextVariance = omega + (alpha * returnSquared) + (beta * prevVariance)

        return sqrt(nextVariance)
    }

    /**
     * Fallback: Rolling Standard Deviation
     */
    fun calculateRollingStdDev(returns: List<Double>): Double {
        if (returns.isEmpty()) return 0.0
        val mean = returns.average()
        val sumSquaredDiff = returns.sumOf { (it - mean).pow(2) }
        return sqrt(sumSquaredDiff / returns.size)
    }
}
