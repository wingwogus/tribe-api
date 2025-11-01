package com.tribe.tribe_api.expense.repository

import com.tribe.tribe_api.expense.entity.Expense
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate

interface ExpenseRepository : JpaRepository<Expense, Long> {

    // 💡 수정: ExpenseItem과 Assignment 모두 LEFT JOIN FETCH (Set으로 변경했으므로 가능)
    @Query("""
        SELECT DISTINCT e FROM Expense e 
        LEFT JOIN FETCH e.expenseItems items 
        LEFT JOIN FETCH items.assignments a
        WHERE e.trip.id = :tripId 
        AND e.paymentDate BETWEEN :startDate AND :endDate
    """)
    fun findAllByTripIdAndPaymentDateBetween(tripId: Long, startDate: LocalDate, endDate: LocalDate): List<Expense>

    // 💡 수정: ExpenseItem과 Assignment 모두 LEFT JOIN FETCH (Set으로 변경했으므로 가능)
    @Query("""
        SELECT DISTINCT e FROM Expense e 
        LEFT JOIN FETCH e.expenseItems items 
        LEFT JOIN FETCH items.assignments a
        WHERE e.trip.id = :tripId
    """)
    fun findAllByTripId(tripId: Long): List<Expense>
}