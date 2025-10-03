package com.tribe.tribe_api.trip.service

import com.tribe.tribe_api.trip.dto.TripMemberDto
import com.tribe.tribe_api.trip.entity.TripMember
import com.tribe.tribe_api.trip.entity.TripRole
import com.tribe.tribe_api.trip.repository.TripMemberRepository
import com.tribe.tribe_api.trip.repository.TripRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TripMemberService(
    private val tripRepository: TripRepository,
    private val tripMemberRepository: TripMemberRepository
) {

    //특정 여행에 임시 참여자(게스트)를 추가
    @Transactional
    fun addGuest(tripId: Long, request: TripMemberDto.AddGuestRequest): TripMemberDto.Info {
        val trip = tripRepository.findById(tripId)
            .orElseThrow { EntityNotFoundException("Trip Not Found $tripId") }

        val newGuest = TripMember(
            member = null,
            trip = trip,
            guestNickname = request.name,
            role = TripRole.GUEST
        )

        val savedGuest = tripMemberRepository.save(newGuest)
        return TripMemberDto.Info.from(savedGuest)
    }
}