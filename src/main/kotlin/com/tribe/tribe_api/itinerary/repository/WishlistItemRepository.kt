package com.tribe.tribe_api.itinerary.repository

import com.tribe.tribe_api.itinerary.entity.WishlistItem
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface WishlistItemRepository : JpaRepository<WishlistItem, Long>{
    /**
     * 위시리스트 전체 조회 (getWishList)
     * N+1 방지를 위해 place, adder, adder.member를 fetch join
     */
    @Query(
        value = """
            SELECT wi FROM WishlistItem wi
            JOIN FETCH wi.place p
            JOIN FETCH wi.adder a
            LEFT JOIN FETCH a.member m
            WHERE wi.trip.id = :tripId
        """,
        countQuery = "SELECT COUNT(wi) FROM WishlistItem wi WHERE wi.trip.id = :tripId"
    )
    fun findAllByTrip_Id(@Param("tripId") tripId: Long, pageable: Pageable): Page<WishlistItem>
    // 검색 조회를 위한 페이지네이션 메서드
    // N+1 방지 위해 place, adder, adder.member를 fetch join
    @Query(
        value = """
            SELECT wi FROM WishlistItem wi
            JOIN FETCH wi.place p
            JOIN FETCH wi.adder a
            LEFT JOIN FETCH a.member m
            WHERE wi.trip.id = :tripId AND LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%'))
        """,
        countQuery = """
            SELECT COUNT(wi) FROM WishlistItem wi
            JOIN wi.place p
            WHERE wi.trip.id = :tripId AND LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%'))
        """
    )
    fun findAllByTrip_IdAndPlace_NameContainingIgnoreCase(
        @Param("tripId") tripId: Long,
        @Param("query") query: String,
        pageable: Pageable
    ): Page<WishlistItem>
    fun existsByTrip_IdAndPlace_ExternalPlaceId(tripId: Long, externalPlaceId: String): Boolean

    @Query("SELECT w.id FROM WishlistItem w WHERE w.trip.id = :tripId AND w.id IN :ids")
    fun findIdsByTripIdAndIdIn(@Param("tripId") tripId: Long, @Param("ids") ids: List<Long>): List<Long>

    @Modifying
    @Query("DELETE FROM WishlistItem w WHERE w.adder.id = :adderId")
    fun deleteByAdderId(adderId: Long)
}