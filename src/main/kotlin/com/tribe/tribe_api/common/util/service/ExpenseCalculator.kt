package com.tribe.tribe_api.common.util.service

import java.math.BigDecimal
import java.math.RoundingMode

object ExpenseCalculator {
    // 1/n 계산 로직
    fun calculateFairShare(totalAmount: BigDecimal, participantCount: Int): List<BigDecimal> {
        if (participantCount == 0) return emptyList()

        val count = participantCount.toBigDecimal()
        // 소수점 버림 (원 단위)
        val baseAmount = totalAmount.divide(count, 0, RoundingMode.DOWN)
        val remainder = totalAmount.subtract(baseAmount.multiply(count))

        val amounts = MutableList(participantCount) { baseAmount }

        // 나머지가 있다면 첫 번째 사람에게 더함
        if (remainder.compareTo(BigDecimal.ZERO) > 0) {
            amounts[0] = amounts[0].add(remainder)
        }
        return amounts
    }
}