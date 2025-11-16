package com.tribe.tribe_api.trip.service

import com.tribe.tribe_api.common.exception.BusinessException
import com.tribe.tribe_api.common.exception.ErrorCode
import com.tribe.tribe_api.expense.repository.ExpenseAssignmentRepository
import com.tribe.tribe_api.expense.repository.ExpenseRepository
import com.tribe.tribe_api.trip.dto.TripMemberDto
import com.tribe.tribe_api.trip.entity.TripMember
import com.tribe.tribe_api.trip.entity.TripRole
import com.tribe.tribe_api.trip.repository.TripMemberRepository
import com.tribe.tribe_api.trip.repository.TripRepository
import org.slf4j.LoggerFactory
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TripMemberService(
    private val tripRepository: TripRepository,
    private val tripMemberRepository: TripMemberRepository,
    private val expenseRepository: ExpenseRepository,
    private val expenseAssignmentRepository: ExpenseAssignmentRepository
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun handleMemberExit(exitingMemberId: Long, tripId: Long) {

        // 배분 정보 삭제
        expenseAssignmentRepository.deleteByDebtorId(exitingMemberId)

        // 위임받을 OWNER 찾기
        val ownerMember = tripMemberRepository.findByTripIdAndRole(tripId, TripRole.OWNER)
            .firstOrNull()
            ?: throw BusinessException(ErrorCode.OWNER_NOT_FOUND)

        val newPayerId = ownerMember.id!!

        // Payer 위임
        // 나가는 멤버가 Payer인 모든 Expense를 조회
        val expensesToReassign = expenseRepository.findByPayerId(exitingMemberId)

        // 각 Expense의 Payer를 새로운 멤버에게 위임
        expensesToReassign.forEach { expense ->
            expenseRepository.updatePayerId(expense.id!!, newPayerId)
        }
    }

    //특정 여행에 임시 참여자(게스트)를 추가
    @Transactional
    @PreAuthorize("@tripSecurityService.isTripMember(#tripId)")
    fun addGuest(tripId: Long, request: TripMemberDto.AddGuestRequest): TripMemberDto.Simple {
        val trip = tripRepository.findById(tripId)
            .orElseThrow { BusinessException(ErrorCode.TRIP_NOT_FOUND) }

        val newGuest = TripMember(
            member = null,
            trip = trip,
            guestNickname = request.name,
            role = TripRole.GUEST
        )
        trip.members.add(newGuest)

        val savedGuest = tripMemberRepository.save(newGuest)
        logger.info("Guest added to trip. Trip ID: {}, Guest Nickname: {}", tripId, request.name)
        return TripMemberDto.Simple.from(savedGuest)
    }

    //임시참여자 삭제
    @Transactional
    @PreAuthorize("@tripSecurityService.isTripMember(#tripId)")
    fun deleteGuest(tripId: Long, guestTripMemberId: Long) {

        val targetTripMember = tripMemberRepository.findByIdAndTripId(guestTripMemberId, tripId)
            .orElseThrow { BusinessException(ErrorCode.MEMBER_NOT_FOUND) }

        if (targetTripMember.role != TripRole.GUEST) {
            throw BusinessException(ErrorCode.NOT_GUEST)
        }

        handleMemberExit(
            exitingMemberId = targetTripMember.id!!,
            tripId = tripId
        )

        tripMemberRepository.delete(targetTripMember)
    }

    //OWNER가 특정 MEMBER 강퇴
    @Transactional
    @PreAuthorize("@tripSecurityService.isTripOwner(#tripId)")
    fun kickMember(tripId: Long, targetMemberId: Long){

        val targetTripMember = tripMemberRepository.findByIdAndTripId(targetMemberId, tripId)
            .orElseThrow { BusinessException(ErrorCode.MEMBER_NOT_FOUND) }

        if (targetTripMember.role == TripRole.OWNER) {
            throw BusinessException(ErrorCode.CANNOT_KICK_OWNER)
        }

        handleMemberExit(
            exitingMemberId = targetTripMember.id!!,
            tripId = tripId
        )

        tripMemberRepository.delete(targetTripMember)
    }

    //여행 탈퇴
    @Transactional
    @PreAuthorize("@tripSecurityService.isTripMember(#tripId)")
    fun leaveTrip(tripId: Long, memberId: Long) {

        val tripMember = tripMemberRepository.findByIdAndTripId(memberId, tripId)
            .orElseThrow { BusinessException(ErrorCode.MEMBER_NOT_FOUND) }

        if (tripMember.role == TripRole.OWNER) {
            throw BusinessException(ErrorCode.CANNOT_LEAVE_AS_OWNER)
        }

        handleMemberExit(
            exitingMemberId = tripMember.id!!,
            tripId = tripId
        )

        tripMemberRepository.delete(tripMember)
    }
}