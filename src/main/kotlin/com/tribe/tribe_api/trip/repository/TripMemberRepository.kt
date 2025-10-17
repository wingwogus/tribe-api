package com.tribe.tribe_api.trip.repository

import com.tribe.tribe_api.member.entity.Member
import com.tribe.tribe_api.trip.entity.Trip
import com.tribe.tribe_api.trip.entity.TripMember
import org.springframework.data.jpa.repository.JpaRepository

interface TripMemberRepository : JpaRepository<TripMember, Long> {
    fun findByTripAndMember(trip: Trip, member: Member): TripMember?
    fun existsByTripIdAndMemberId(tripId: Long, memberId: Long): Boolean
    fun findByTripIdAndMemberId(tripId: Long, memberId: Long): TripMember? // 역할(Role) 확인을 위해 추가
}
