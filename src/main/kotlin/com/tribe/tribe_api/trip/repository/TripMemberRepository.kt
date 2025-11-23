package com.tribe.tribe_api.trip.repository

import com.tribe.tribe_api.member.entity.Member
import com.tribe.tribe_api.trip.entity.Trip
import com.tribe.tribe_api.trip.entity.TripMember
import com.tribe.tribe_api.trip.entity.TripRole
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface TripMemberRepository : JpaRepository<TripMember, Long> {
    fun findByTripAndMember(trip: Trip, member: Member): TripMember?
    fun existsByTripIdAndMemberId(tripId: Long, memberId: Long): Boolean
    fun findByTripIdAndMemberId(tripId: Long, memberId: Long): TripMember? // 역할(Role) 확인을 위해 추가
    fun findByIdAndTripId(id: Long, tripId: Long): Optional<TripMember>
    fun findByTripIdAndRole(tripId: Long, role: TripRole): List<TripMember>
}
