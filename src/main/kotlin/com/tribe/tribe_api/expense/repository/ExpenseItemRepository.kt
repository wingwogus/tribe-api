package com.tribe.tribe_api.expense.repository

import com.tribe.tribe_api.expense.entity.ExpenseItem
import org.springframework.data.jpa.repository.JpaRepository

interface ExpenseItemRepository : JpaRepository<ExpenseItem, Long> {
}