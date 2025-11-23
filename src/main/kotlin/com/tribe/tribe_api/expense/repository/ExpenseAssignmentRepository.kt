package com.tribe.tribe_api.expense.repository

import com.tribe.tribe_api.expense.entity.ExpenseAssignment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.transaction.annotation.Transactional

interface ExpenseAssignmentRepository : JpaRepository<ExpenseAssignment, Long> {

    @Transactional
    @Modifying
    fun deleteByExpenseItemId(itemId: Long)

    // 특정 멤버가 포함된 모든 분배 내역 조회
    fun findAllByTripMemberId(tripMemberId: Long): List<ExpenseAssignment>

    // 특정 지출 항목에서 특정 멤버의 분배 내역만 삭제
    fun deleteByExpenseItemIdAndTripMemberId(expenseItemId: Long, tripMemberId: Long)
}