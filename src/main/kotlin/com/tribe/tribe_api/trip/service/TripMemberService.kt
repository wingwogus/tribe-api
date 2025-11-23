package com.tribe.tribe_api.trip.service

import com.tribe.tribe_api.common.exception.BusinessException
import com.tribe.tribe_api.common.exception.ErrorCode
import com.tribe.tribe_api.common.util.security.SecurityUtil
import com.tribe.tribe_api.common.util.service.ExpenseCalculator
import com.tribe.tribe_api.expense.entity.ExpenseAssignment
import com.tribe.tribe_api.expense.repository.ExpenseAssignmentRepository
import com.tribe.tribe_api.expense.repository.ExpenseRepository
import com.tribe.tribe_api.itinerary.repository.WishlistItemRepository
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
    private val wishlistItemRepository: WishlistItemRepository,
    private val expenseAssignmentRepository: ExpenseAssignmentRepository
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    private fun handleMemberExit(exitingMemberId: Long, targetRole: TripRole) {

        val targetTripMember = tripMemberRepository.findById(exitingMemberId)
            .orElseThrow { BusinessException(ErrorCode.MEMBER_NOT_FOUND) }

        // 위시리스트 삭제
        wishlistItemRepository.deleteByAdderId(exitingMemberId)

        // 연관관계는 유지하되 Role만 변경
        targetTripMember.role = targetRole
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

    //임시참여자 삭제 (지출 재분배)
    @Transactional
    @PreAuthorize("@tripSecurityService.isTripMember(#tripId)")
    fun deleteGuest(tripId: Long, guestTripMemberId: Long) {

        val targetGuest = tripMemberRepository.findByIdAndTripId(guestTripMemberId, tripId)
            ?: throw BusinessException(ErrorCode.MEMBER_NOT_FOUND)

        if (targetGuest.role != TripRole.GUEST) {
            throw BusinessException(ErrorCode.NOT_GUEST)
        }

        // Owner 찾기
        val ownerMember = tripMemberRepository.findByTripIdAndRole(tripId, TripRole.OWNER)
            .firstOrNull() ?: throw BusinessException(ErrorCode.OWNER_NOT_FOUND)

        // 게스트가 Payer인 지출 Owner에게 이관
        val expensesPaidByGuest = expenseRepository.findByPayerId(targetGuest.id!!)
        expensesPaidByGuest.forEach { expense ->
            expense.payer = ownerMember
        }

        // 게스트가 포함된 분배 재계산
        val assignments = expenseAssignmentRepository.findAllByTripMemberId(targetGuest.id!!)
        val affectedItems = assignments.map { it.expenseItem }.distinct() // 영향받는 지출 항목들

        affectedItems.forEach { item ->
            val currentAssignments = item.assignments

            // 메모리 상에서 게스트 제거 및 DB 삭제
            currentAssignments.removeIf { it.tripMember.id == targetGuest.id }
            expenseAssignmentRepository.deleteByExpenseItemIdAndTripMemberId(item.id!!, targetGuest.id!!)

            val remainingMembers = currentAssignments.map { it.tripMember }

            if (remainingMembers.isNotEmpty()) {
                // 남은 인원끼리 N빵 재계산
                val newAmounts = ExpenseCalculator.calculateFairShare(item.price, remainingMembers.size)
                currentAssignments.zip(newAmounts).forEach { (assignment, amount) ->
                    assignment.amount = amount
                }
            } else {
                // 남은 인원이 없음
                val payerAssignment = ExpenseAssignment(
                    expenseItem = item,
                    tripMember = item.expense.payer,
                    amount = item.price
                )
                item.assignments.add(payerAssignment)
                expenseAssignmentRepository.save(payerAssignment)
            }
        }
        // 위시리스트 삭제
        wishlistItemRepository.deleteByAdderId(targetGuest.id!!)

        // 게스트 엔티티 삭제
        tripMemberRepository.delete(targetGuest)
        logger.info("Guest deleted logic completed. TripId: {}, GuestId: {}", tripId, guestTripMemberId)
    }

    //OWNER가 특정 MEMBER 강퇴
    @Transactional
    @PreAuthorize("@tripSecurityService.isTripOwner(#tripId)")
    fun kickMember(tripId: Long, targetMemberId: Long) {
        val targetTripMember = tripMemberRepository.findByIdAndTripId(targetMemberId, tripId)
            ?: throw BusinessException(ErrorCode.MEMBER_NOT_FOUND)

        if (targetTripMember.role == TripRole.OWNER) {
            throw BusinessException(ErrorCode.CANNOT_KICK_OWNER)
        }

        handleMemberExit(targetTripMember.id!!, TripRole.KICKED)
    }

    //여행 탈퇴
    @Transactional
    @PreAuthorize("@tripSecurityService.isTripMember(#tripId)")
    fun leaveTrip(tripId: Long) {
        val currentMemberId = SecurityUtil.getCurrentMemberId()

        val tripMember = tripMemberRepository.findByTripIdAndMemberId(tripId, currentMemberId)
            ?: throw BusinessException(ErrorCode.MEMBER_NOT_FOUND)

        if (tripMember.role == TripRole.OWNER) {
            throw BusinessException(ErrorCode.CANNOT_LEAVE_AS_OWNER)
        }

        handleMemberExit(tripMember.id!!, TripRole.EXITED)
    }

    @Transactional
    @PreAuthorize("@tripSecurityService.isTripOwner(#tripId)")
    fun assignRole(tripId: Long, tripMemberId: Long, request: TripMemberDto.AssignRoleRequest): TripMemberDto.Simple {
        tripRepository.findById(tripId)
            .orElseThrow { BusinessException(ErrorCode.TRIP_NOT_FOUND) }

        val participantTripMember = tripMemberRepository.findByTripIdAndMemberId(tripId, tripMemberId)
            ?: throw BusinessException(ErrorCode.MEMBER_NOT_FOUND)

        if (participantTripMember.member?.id == SecurityUtil.getCurrentMemberId()) {
            throw BusinessException(ErrorCode.CANNOT_CHANGE_OWN_ROLE)
        }

        if (request.requestRole == TripRole.OWNER) {
            throw BusinessException(ErrorCode.CANNOT_CHANGE_MEMBER_TO_OWNER)
        }
        if (participantTripMember.role == request.requestRole) {
            throw BusinessException(ErrorCode.EQUAL_ROLE)
        }
        if (request.requestRole == TripRole.GUEST) {
            throw BusinessException(ErrorCode.CANNOT_CHANGE_MEMBER_TO_GUEST)
        }
        val oldRole = participantTripMember.role

        participantTripMember.role = request.requestRole

        val newRole = request.requestRole
        logger.info("TripMember Role Changed.: [TripMemberID: {}, TripID: {}] -> [ Role : {} -> {}]", tripMemberId, tripId, oldRole, newRole)

        return TripMemberDto.Simple.from(participantTripMember)
    }
}