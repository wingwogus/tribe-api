package com.tribe.tribe_api.common.util.service

import com.tribe.tribe_api.common.exception.BusinessException
import com.tribe.tribe_api.common.exception.ErrorCode
import com.tribe.tribe_api.common.util.security.SecurityUtil
import com.tribe.tribe_api.expense.repository.ExpenseRepository
import com.tribe.tribe_api.trip.entity.TripRole
import com.tribe.tribe_api.trip.repository.TripMemberRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service("tripSecurityService")
class TripSecurityService(
    private val tripMemberRepository: TripMemberRepository,
    private val expenseRepository: ExpenseRepository
) {
    @Transactional(readOnly = true)
    fun isTripMember(tripId: Long): Boolean {
        val memberId = SecurityUtil.getCurrentMemberId()

        if (!tripMemberRepository.existsByTripIdAndMemberId(tripId, memberId)) {
            throw BusinessException(ErrorCode.NOT_A_TRIP_MEMBER)
        }
        return true
    }

    @Transactional(readOnly = true)
    fun isTripOwner(tripId: Long): Boolean {
        val memberId = SecurityUtil.getCurrentMemberId()

        val tripMember = tripMemberRepository.findByTripIdAndMemberId(tripId, memberId)
            ?: throw BusinessException(ErrorCode.NOT_A_TRIP_MEMBER)

        if (tripMember.role != TripRole.OWNER) {
            throw BusinessException(ErrorCode.NO_AUTHORITY_TRIP)
        }
        return true
    }

    @Transactional(readOnly = true)
    fun isTripMemberByExpenseId(expenseId: Long): Boolean {
        val expense = expenseRepository.findByIdOrNull(expenseId)
            ?: throw BusinessException(ErrorCode.EXPENSE_NOT_FOUND)

        val tripId = expense.trip.id
            ?: throw BusinessException(ErrorCode.TRIP_NOT_FOUND)

        return isTripMember(tripId)
    }
}