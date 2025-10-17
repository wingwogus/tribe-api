package com.tribe.tribe_api.trip.service

import com.tribe.tribe_api.common.exception.BusinessException
import com.tribe.tribe_api.common.exception.ErrorCode
import com.tribe.tribe_api.common.util.service.TripSecurityService
import com.tribe.tribe_api.trip.dto.TripMemberDto
import com.tribe.tribe_api.trip.entity.TripMember
import com.tribe.tribe_api.trip.entity.TripRole
import com.tribe.tribe_api.trip.repository.TripMemberRepository
import com.tribe.tribe_api.trip.repository.TripRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TripMemberService(
    private val tripRepository: TripRepository,
    private val tripMemberRepository: TripMemberRepository,
    private val tripSecurityService: TripSecurityService
) {

    //특정 여행에 임시 참여자(게스트)를 추가
    @Transactional
    fun addGuest(request: TripMemberDto.AddGuestRequest): TripMemberDto.Info {
        val tripId = request.tripId

        tripSecurityService.isTripMember(tripId)

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
        return TripMemberDto.Info.from(savedGuest)
    }
}