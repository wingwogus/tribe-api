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

    // 특정 카테고리에 속한 아이템의 개수를 세는 메서드
    fun countByCategoryId(categoryId: Long): Int

    @Query("""
        SELECT i FROM ItineraryItem i 
        JOIN i.category c
        JOIN FETCH i.place p
        WHERE c.trip.id = :tripId
        AND p IS NOT NULL
        ORDER BY c.day ASC, c.order ASC, i.order ASC
    """)
    fun findByTripIdOrderByCategoryAndOrder(@Param("tripId") tripId: Long): List<ItineraryItem>
}
