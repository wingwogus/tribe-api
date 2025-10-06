package com.tribe.tribe_api.expense.service

import com.tribe.tribe_api.expense.dto.SettlementDto
import com.tribe.tribe_api.expense.repository.ExpenseRepository
import com.tribe.tribe_api.trip.repository.TripRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate

@Service
@Transactional(readOnly = true)
class SettlementService(
    private val expenseRepository: ExpenseRepository,
    private val tripRepository: TripRepository
) {

    fun getDailySettlement(tripId: Long, date: LocalDate): SettlementDto.DailyResponse {
        val trip = tripRepository.findById(tripId)
            .orElseThrow { EntityNotFoundException("Trip not found with id: $tripId") }

        val startOfDay = date.atStartOfDay()
        val endOfDay = date.atTime(23, 59, 59)

        val dailyExpenses = expenseRepository.findAllByTripIdAndPaymentDateBetween(tripId, date, date)

        val dailyTotalAmount = dailyExpenses.sumOf { it.totalAmount }

        val expenseSummaries = dailyExpenses.map { expense ->
            SettlementDto.DailyExpenseSummary(
                expenseId = expense.id!!,
                title = expense.title,
                payerName = expense.payer.name,
                totalAmount = expense.totalAmount
            )
        }

        val memberSummaries = trip.members.map { member ->
            val paidAmount = dailyExpenses
                .filter { it.payer.id == member.id }
                .sumOf { it.totalAmount }

            val assignedAmount = dailyExpenses
                .flatMap { it.expenseItems }
                .flatMap { it.assignments }
                .filter { it.tripMember.id == member.id }
                .sumOf { it.expenseItem.price.divide(BigDecimal(it.expenseItem.assignments.size)) }


            SettlementDto.MemberDailySummary(
                memberId = member.id!!,
                memberName = member.name,
                paidAmount = paidAmount,
                assignedAmount = assignedAmount.setScale(0)
            )
        }

        return SettlementDto.DailyResponse(
            date = date,
            dailyTotalAmount = dailyTotalAmount,
            expenses = expenseSummaries,
            memberSummaries = memberSummaries
        )
    }
}