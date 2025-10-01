package com.tribe.tribe_api.itinerary.repository

import com.tribe.tribe_api.itinerary.entity.WishlistItem
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface WishlistItemRepository : JpaRepository<WishlistItem, Long>{
    // 전체 조회를 위한 페이지네이션 메서드 (Page 반환)
    fun findAllByTrip_Id(tripId: Long, pageable: Pageable): Page<WishlistItem>
    // 검색 조회를 위한 페이지네이션 메서드
    fun findAllByTrip_IdAndPlace_NameContainingIgnoreCase(
        tripId: Long,
        query: String,
        pageable: Pageable
    ): Page<WishlistItem>

    @Query("SELECT wi.id FROM WishlistItem wi WHERE wi.id IN :ids")
    fun findExistingIds(@Param("ids") ids: List<Long>): List<Long>
}