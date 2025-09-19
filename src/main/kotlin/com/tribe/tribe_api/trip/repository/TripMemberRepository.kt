package com.tribe.tribe_api.trip.repository

import com.tribe.tribe_api.member.entity.Member
import com.tribe.tribe_api.trip.entity.Trip
import com.tribe.tribe_api.trip.entity.TripMember
import org.springframework.data.jpa.repository.JpaRepository

interface TripMemberRepository : JpaRepository<TripMember, Long> {
    fun existsByTripAndMember(trip: Trip, member: Member): Boolean
}
