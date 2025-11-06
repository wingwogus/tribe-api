package com.tribe.tribe_api.expense.repository

import com.tribe.tribe_api.expense.entity.Expense
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate

interface ExpenseRepository : JpaRepository<Expense, Long> {

    // Paid, Assigned 계산에 필요한 Trip, Payer 정보를 확실히 가져오도록 Fetch Join 추가 (일별 정산용)
    @Query("""
        SELECT DISTINCT e FROM Expense e 
        JOIN FETCH e.trip t 
        JOIN FETCH e.payer p 
        LEFT JOIN FETCH e.expenseItems items 
        LEFT JOIN FETCH items.assignments a
        WHERE e.trip.id = :tripId 
        AND e.paymentDate BETWEEN :startDate AND :endDate
    """)
    fun findAllByTripIdAndPaymentDateBetween(tripId: Long, startDate: LocalDate, endDate: LocalDate): List<Expense>

    // Paid, Assigned 계산에 필요한 Trip, Payer 정보를 확실히 가져오도록 Fetch Join 추가 (전체 정산용)
    @Query("""
        SELECT DISTINCT e FROM Expense e 
        JOIN FETCH e.trip t 
        JOIN FETCH e.payer p 
        LEFT JOIN FETCH e.expenseItems items 
        LEFT JOIN FETCH items.assignments a
        WHERE e.trip.id = :tripId
    """)
    fun findAllByTripId(tripId: Long): List<Expense>
}