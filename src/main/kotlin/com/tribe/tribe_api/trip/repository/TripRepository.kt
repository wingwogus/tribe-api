package com.tribe.tribe_api.trip.repository

import com.tribe.tribe_api.trip.entity.Trip
import io.lettuce.core.dynamic.annotation.Param
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface TripRepository : JpaRepository<Trip, Long> {

    @Query("""
            SELECT t
            FROM Trip t
            JOIN t.members tm
            WHERE tm.member.id = :memberId
            AND tm.role not in ('KICKED', 'EXITED')
            """)
    fun findTripsByMemberId(@Param("memberId") memberId: Long, pageable: Pageable): Page<Trip>

    @Query("""
            SELECT DISTINCT t FROM Trip t 
            JOIN FETCH t.members m 
            JOIN FETCH m.member 
            WHERE t.id = :tripId
            """)
    fun findTripWithMembersById(@Param("tripId") tripId: Long): Trip?

    @Query("""
            SELECT t FROM Trip t 
            LEFT JOIN FETCH t.categories c
            WHERE t.id = :tripId
            """)
    fun findTripWithFullItineraryById(tripId: Long): Trip?

}