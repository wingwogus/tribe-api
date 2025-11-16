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

    // 나가는 멤버가 갚아야 할 사람인 모든 배분 정보를 삭제
    @Transactional
    @Modifying
    @Query("delete from ExpenseAssignment ea where ea.debtor.id = :memberId")
    fun deleteByDebtorId(memberId: Long)
}