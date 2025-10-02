package com.tribe.tribe_api.itinerary.repository

import com.tribe.tribe_api.itinerary.entity.ItineraryItem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ItineraryItemRepository : JpaRepository<ItineraryItem, Long> {
    fun findByCategoryIdOrderByOrderAsc(categoryId: Long): List<ItineraryItem>

    // 순서 변경 시 업데이트할 item들을 효율적으로 조회하기 위한 쿼리
    @Query("SELECT i FROM ItineraryItem i JOIN i.category c WHERE i.id IN :itemIds AND c.trip.id = :tripId")
    fun findByIdInAndTripId(@Param("itemIds") itemIds: List<Long>, @Param("tripId") tripId: Long): List<ItineraryItem>
}
