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
        // 1. 여행 정보와 참여 멤버들을 조회합니다.
        val trip = tripRepository.findById(tripId)
            .orElseThrow { EntityNotFoundException("Trip not found with id: $tripId") }

        // 2. 특정 날짜에 발생한 모든 지출 내역을 조회합니다.
        val dailyExpenses = expenseRepository.findAllByTripIdAndPaymentDateBetween(tripId, date, date)

        // 3. 일일 총 지출액을 계산합니다.
        val dailyTotalAmount = dailyExpenses.sumOf { it.totalAmount }

        // 4. 개별 지출 요약 정보를 만듭니다.
        val expenseSummaries = dailyExpenses.map { expense ->
            SettlementDto.DailyExpenseSummary(
                expenseId = expense.id!!,
                title = expense.title,
                payerName = expense.payer.name,
                totalAmount = expense.totalAmount
            )
        }

        // 5. 멤버별 정산 요약 정보를 계산합니다.
        val memberSummaries = trip.members.map { member ->
            // 멤버가 해당 날짜에 '결제한' 총 금액
            val paidAmount = dailyExpenses
                .filter { it.payer.id == member.id }
                .sumOf { it.totalAmount }

            // 멤버에게 '분담된' 총 금액
            val assignedAmount = dailyExpenses
                .flatMap { it.expenseItems }
                .flatMap { it.assignments }
                .filter { it.tripMember.id == member.id }
                .sumOf {
                    // 각 항목의 가격을 분담한 사람 수로 나눔
                    val participantCount = it.expenseItem.assignments.size
                    if (participantCount > 0) {
                        it.expenseItem.price.divide(BigDecimal(participantCount), 0, BigDecimal.ROUND_HALF_UP)
                    } else {
                        BigDecimal.ZERO
                    }
                }

            SettlementDto.MemberDailySummary(
                memberId = member.id!!,
                memberName = member.name,
                paidAmount = paidAmount,
                assignedAmount = assignedAmount
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