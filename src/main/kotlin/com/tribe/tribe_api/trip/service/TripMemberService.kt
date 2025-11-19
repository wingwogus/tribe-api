package com.tribe.tribe_api.trip.service

import com.tribe.tribe_api.common.exception.BusinessException
import com.tribe.tribe_api.common.exception.ErrorCode
import com.tribe.tribe_api.common.util.security.SecurityUtil
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
    private val tripMemberRepository: TripMemberRepository
) {

    private val logger = LoggerFactory.getLogger(javaClass)

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

    @Transactional
    @PreAuthorize("@tripSecurityService.isTripOwner(#tripId)")
    fun assignRole(tripId: Long, request: TripMemberDto.AssignRoleRequest): TripMemberDto.Simple {
        tripRepository.findById(tripId)
            .orElseThrow { BusinessException(ErrorCode.TRIP_NOT_FOUND) }

        val participantTripMember = tripMemberRepository.findByTripIdAndMemberId(tripId,request.tripMemberId)
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

        val updatedMember = tripMemberRepository.save(participantTripMember)
        logger.info("TripMember Role Changed.: [TripMemberID: {}, TripID: {}] -> [ Role : {} -> {}]", request.tripMemberId, tripId, oldRole, newRole)

        return TripMemberDto.Simple.from(updatedMember)
    }
}