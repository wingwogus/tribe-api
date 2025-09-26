package com.tribe.tribe_api.itinerary.repository

import com.tribe.tribe_api.itinerary.entity.WishlistItem
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface WishlistItemRepository : JpaRepository<WishlistItem, Long>{
    // 전체 조회를 위한 페이지네이션 메서드 (Page 반환)
    fun findAllByTrip_Id(tripId: Long, pageable: Pageable): Page<WishlistItem>

    // ✨ --- 이 부분의 파라미터와 반환 타입을 수정해야 합니다 --- ✨
    // 검색 조회를 위한 페이지네이션 메서드
    fun findAllByTrip_IdAndPlace_NameContainingIgnoreCase(
        tripId: Long,
        query: String,
        pageable: Pageable // ⬅️ 1. Pageable 파라미터 추가
    ): Page<WishlistItem>
}