package com.tribe.tribe_api.expense.repository

import com.tribe.tribe_api.expense.entity.Expense
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface ExpenseRepository : JpaRepository<Expense, Long> {
    fun findAllByTripId(tripId: Long): List<Expense>
    fun findAllByTripIdAndPaymentDateBetween(tripId: Long, startOfDay: LocalDate, endOfDay: LocalDate): List<Expense>
}