package com.tribe.tribe_api.expense.service

import com.tribe.tribe_api.common.exception.BusinessException
import com.tribe.tribe_api.common.exception.ErrorCode
import com.tribe.tribe_api.expense.dto.SettlementDto
import com.tribe.tribe_api.expense.repository.ExpenseRepository
import com.tribe.tribe_api.trip.entity.TripMember
import com.tribe.tribe_api.trip.repository.TripRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.math.abs

@Service
@Transactional(readOnly = true)
class SettlementService(
    private val expenseRepository: ExpenseRepository,
    private val tripRepository: TripRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun getDailySettlement(tripId: Long, date: LocalDate): SettlementDto.DailyResponse {
        val trip = tripRepository.findById(tripId)
            .orElseThrow { BusinessException(ErrorCode.TRIP_NOT_FOUND) }

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

            //  1/N 계산 대신 저장된 amount를 직접 합산하도록 변경
            val assignedAmount = dailyExpenses
                .flatMap { it.expenseItems }
                .flatMap { it.assignments }
                .filter { it.tripMember.id == member.id }
                .sumOf { it.amount }

            SettlementDto.MemberDailySummary(
                memberId = member.id!!,
                memberName = member.name,
                paidAmount = paidAmount,
                assignedAmount = assignedAmount
            )
        }

        val totalAssigned = memberSummaries.sumOf { it.assignedAmount }
        if (dailyTotalAmount.compareTo(totalAssigned) != 0) {
            logger.error(
                "[정산 금액 불일치] Trip ID: {}, 날짜: {}. 총 지출액: {}, 총 분배액: {}",
                tripId, date, dailyTotalAmount, totalAssigned
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

        // 1. Repository에서 순수 데이터 배열(List<Array<Any>>)을 가져옵니다.
        val rawSummaries = expenseRepository.getSettlementSummariesAsRaw(tripId)

        // 2. 서비스 코드에서 직접 DTO 리스트로 변환합니다. (타입 문제 해결)
        val settlementSummaries = rawSummaries.map { row ->
            SettlementDto.SettlementSummary(
                tripMemberId = row[0] as Long,
                totalPaid = (row[1] as Number).let { BigDecimal(it.toString()) },
                totalAssigned = (row[2] as Number).let { BigDecimal(it.toString()) }
            )
        }
        val summaryMap = settlementSummaries.associateBy { it.tripMemberId }

        // 3. 각 멤버의 최종 잔액을 계산합니다.
        val memberBalances = trip.members.map { member ->
            val summary = summaryMap[member.id]
            val totalPaid = summary?.totalPaid ?: BigDecimal.ZERO
            val totalAssigned = summary?.totalAssigned ?: BigDecimal.ZERO
            Pair(member, totalPaid.subtract(totalAssigned))
        }

        // 4. 최소 송금 관계를 계산합니다.
        val debtRelations = calculateDebtRelations(memberBalances)

        // 5. 최종 DTO로 변환하여 반환합니다.
        val memberBalanceDtos = memberBalances.map { (member, balance) ->
            SettlementDto.MemberBalance(
                tripMemberId = member.id!!,
                nickname = member.name,
                balance = balance
            )
        }

        return SettlementDto.TotalResponse(memberBalanceDtos, debtRelations)
    }

    private fun calculateDebtRelations(balances: List<Pair<TripMember, BigDecimal>>): List<SettlementDto.DebtRelation> {
        val debtors = balances.filter { it.second < BigDecimal.ZERO }.toMutableList()
        val creditors = balances.filter { it.second > BigDecimal.ZERO }.toMutableList()
        val relations = mutableListOf<SettlementDto.DebtRelation>()

        while (debtors.isNotEmpty() && creditors.isNotEmpty()) {
            val debtorPair = debtors.first()
            val creditorPair = creditors.first()

            val debtor = debtorPair.first
            var debtorBalance = debtorPair.second
            val creditor = creditorPair.first
            var creditorBalance = creditorPair.second

            val transferAmount = minOf(abs(debtorBalance.toDouble()), creditorBalance.toDouble()).toBigDecimal()

            relations.add(
                SettlementDto.DebtRelation(
                    fromNickname = debtor.name,
                    fromTripMemberId = debtor.id!!,
                    toNickname = creditor.name,
                    toTripMemberId = creditor.id!!,
                    amount = transferAmount
                )
            )

            debtorBalance += transferAmount
            creditorBalance -= transferAmount

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