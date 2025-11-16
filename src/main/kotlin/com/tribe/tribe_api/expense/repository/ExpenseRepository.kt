package com.tribe.tribe_api.expense.repository

import com.tribe.tribe_api.expense.entity.Expense
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.Modifying
import org.springframework.transaction.annotation.Transactional

interface ExpenseRepository : JpaRepository<Expense, Long> {

    // findAllByTripId를 대체, N+1 방지를 위해 ItineraryItem과 Category를 Fetch Join
    @Query("""
        SELECT DISTINCT e FROM Expense e 
        JOIN FETCH e.trip t 
        JOIN FETCH e.payer p 
        JOIN FETCH e.itineraryItem ii
        JOIN FETCH ii.category c
        LEFT JOIN FETCH e.expenseItems items 
        LEFT JOIN FETCH items.assignments a
        WHERE e.trip.id = :tripId
    """)
    fun findAllWithDetailsByTripId(tripId: Long): List<Expense>

    // 나가는 멤버가 Payer인 모든 Expense 찾기
    @Query("SELECT e FROM Expense e WHERE e.payer.id = :payerId")
    fun findByPayerId(payerId: Long): List<Expense>

    // Expense의 Payer를 새로운 멤버로 변경
    @Transactional
    @Modifying
    @Query("UPDATE Expense e SET e.payer.id = :newPayerId WHERE e.id = :expenseId")
    fun updatePayerId(expenseId: Long, newPayerId: Long)
}