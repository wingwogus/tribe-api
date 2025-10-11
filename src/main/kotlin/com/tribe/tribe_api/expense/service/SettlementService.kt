package com.tribe.tribe_api.expense.service

import com.tribe.tribe_api.common.exception.BusinessException
import com.tribe.tribe_api.common.exception.ErrorCode
import com.tribe.tribe_api.expense.dto.SettlementDto
import com.tribe.tribe_api.expense.repository.ExpenseRepository
import com.tribe.tribe_api.trip.entity.TripMember
import com.tribe.tribe_api.trip.repository.TripRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import kotlin.math.abs

@Service
@Transactional(readOnly = true)
class SettlementService(
    private val expenseRepository: ExpenseRepository,
    private val tripRepository: TripRepository
) {

    fun getDailySettlement(tripId: Long, date: LocalDate): SettlementDto.DailyResponse {
        // 1. 여행 정보와 참여 멤버들을 조회
        val trip = tripRepository.findById(tripId)
            .orElseThrow { BusinessException(ErrorCode.TRIP_NOT_FOUND) }

        // 2. 특정 날짜에 발생한 모든 지출 내역을 조회
        val dailyExpenses = expenseRepository.findAllByTripIdAndPaymentDateBetween(tripId, date, date)

        // 3. 일일 총 지출액을 계산
        val dailyTotalAmount = dailyExpenses.sumOf { it.totalAmount }

        // 4. 개별 지출 요약 정보 생성
        val expenseSummaries = dailyExpenses.map { expense ->
            SettlementDto.DailyExpenseSummary(
                expenseId = expense.id!!,
                title = expense.title,
                payerName = expense.payer.name,
                totalAmount = expense.totalAmount
            )
        }

        // 5. 멤버별 정산 요약 정보를 계산
        val memberSummaries = trip.members.map { member ->
            // 멤버가 해당 날짜에 결제한 총 금액
            val paidAmount = dailyExpenses
                .filter { it.payer.id == member.id }
                .sumOf { it.totalAmount }

            // 멤버에게 분담된 총 금액
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
    fun getTotalSettlement(tripId: Long): SettlementDto.TotalResponse {
        val trip = tripRepository.findById(tripId)
            .orElseThrow { BusinessException(ErrorCode.TRIP_NOT_FOUND) }

        // 1. 여행 전체 기간의 모든 지출 내역 가져옴
        val allExpenses = expenseRepository.findAllByTripId(tripId)

        // 2. 각 멤버의 최종 잔액 계산
        val memberBalances = trip.members.map { member ->
            // 멤버가 총 결제한 금액
            val totalPaid = allExpenses
                .filter { it.payer.id == member.id }
                .sumOf { it.totalAmount }

            // 멤버에게 총 분담된 금액
            val totalAssigned = allExpenses.sumOf { expense ->
                expense.expenseItems.sumOf { item ->
                    val assignments = item.assignments
                    val participantIds = assignments.map { it.tripMember.id }

                    if (member.id in participantIds) {
                        val participantCount = assignments.size.toBigDecimal()
                        if (participantCount > BigDecimal.ZERO) {
                            val baseAmount = item.price.divide(participantCount, 0, RoundingMode.DOWN)
                            val remainder = item.price.subtract(baseAmount.multiply(participantCount))
                            if (assignments.first().tripMember.id == member.id) baseAmount + remainder else baseAmount
                        } else BigDecimal.ZERO
                    } else BigDecimal.ZERO
                }
            }
            Pair(member, totalPaid.subtract(totalAssigned))
        }

        // 3. 최소 송금 관계를 계산
        val debtRelations = calculateDebtRelations(memberBalances)

        // 4. 최종 디티오로 변환하여 반환
        val memberBalanceDtos = memberBalances.map { (member, balance) ->
            SettlementDto.MemberBalance(
                tripMemberId = member.id!!,
                nickname = member.name,
                balance = balance
            )
        }

        return SettlementDto.TotalResponse(memberBalanceDtos, debtRelations)
    }

    // 최소 송금 관계를 계산
    private fun calculateDebtRelations(balances: List<Pair<TripMember, BigDecimal>>): List<SettlementDto.DebtRelation> {
        // 돈주는 사람 받는 사람 분리
        val debtors = balances.filter { it.second < BigDecimal.ZERO }.toMutableList()
        val creditors = balances.filter { it.second > BigDecimal.ZERO }.toMutableList()
        val relations = mutableListOf<SettlementDto.DebtRelation>()

        // 더 이상 거래가 필요 없을 때까지 반복
        while (debtors.isNotEmpty() && creditors.isNotEmpty()) {
            val debtorPair = debtors.first()
            val creditorPair = creditors.first()

            val debtor = debtorPair.first
            var debtorBalance = debtorPair.second
            val creditor = creditorPair.first
            var creditorBalance = creditorPair.second

            // 주고받을 금액 계산
            val transferAmount = minOf(abs(debtorBalance.toDouble()), creditorBalance.toDouble()).toBigDecimal()

            // 송금 생성
            relations.add(
                SettlementDto.DebtRelation(
                    fromNickname = debtor.name,
                    fromTripMemberId = debtor.id!!,
                    toNickname = creditor.name,
                    toTripMemberId = creditor.id!!,
                    amount = transferAmount
                )
            )

            // 각자의 잔액 업뎃
            debtorBalance += transferAmount
            creditorBalance -= transferAmount

            // 잔액이 0에 가까워지면 리스트에서 제거
            if (debtorBalance.abs() < BigDecimal("0.01")) {
                debtors.removeAt(0)
            } else {
                debtors[0] = debtor to debtorBalance
            }

            if (creditorBalance.abs() < BigDecimal("0.01")) {
                creditors.removeAt(0)
            } else {
                creditors[0] = creditor to creditorBalance
            }
        }
        return relations
    }
}