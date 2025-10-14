package com.tribe.tribe_api.expense.repository

import com.tribe.tribe_api.expense.dto.SettlementDto
import com.tribe.tribe_api.expense.entity.Expense
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate

interface ExpenseRepository : JpaRepository<Expense, Long> {
    fun findAllByTripId(tripId: Long): List<Expense>
    fun findAllByTripIdAndPaymentDateBetween(tripId: Long, startOfDay: LocalDate, endOfDay: LocalDate): List<Expense>

    //   쿼리에서 DTO 생성자를 제거하고, 순수 데이터 배열을 반환
    @Query("""
        SELECT
            tm.id,
            COALESCE((SELECT SUM(e.totalAmount) FROM Expense e WHERE e.payer.id = tm.id AND e.trip.id = :tripId), 0.0),
            COALESCE((SELECT SUM(ea.amount) FROM ExpenseAssignment ea WHERE ea.tripMember.id = tm.id AND ea.expenseItem.expense.trip.id = :tripId), 0.0)
        FROM TripMember tm
        WHERE tm.trip.id = :tripId
        GROUP BY tm.id
    """)
    fun getSettlementSummariesAsRaw(tripId: Long): List<Array<Any>>
    // --- 수정 끝 ---
}