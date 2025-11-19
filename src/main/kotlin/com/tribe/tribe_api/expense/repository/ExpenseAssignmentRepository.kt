package com.tribe.tribe_api.expense.repository

import com.tribe.tribe_api.expense.entity.ExpenseAssignment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional

interface ExpenseAssignmentRepository : JpaRepository<ExpenseAssignment, Long> {

    @Transactional
    @Modifying
    fun deleteByExpenseItemId(itemId: Long)

    @Modifying
    @Query("UPDATE ExpenseAssignment a SET a.tripMember = null WHERE a.tripMember.id = :exitingMemberId")
    fun setTripMemberToNullForExitingMember(exitingMemberId: Long)
}