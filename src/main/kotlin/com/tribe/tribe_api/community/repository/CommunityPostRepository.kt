package com.tribe.tribe_api.community.repository

import com.tribe.tribe_api.community.entity.CommunityPost
import com.tribe.tribe_api.trip.entity.Country
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CommunityPostRepository : JpaRepository<CommunityPost, Long> {

    /**
     * N+1 방지를 위해 JOIN FETCH로 author와 trip을 함께 조회
     */
    @Query(
        value = "SELECT cp FROM CommunityPost cp JOIN FETCH cp.author JOIN FETCH cp.trip WHERE cp.trip.country = :country",
        countQuery = "SELECT COUNT(cp) FROM CommunityPost cp WHERE cp.trip.country = :country" // Pageable을 위한 카운트 쿼리
    )
    fun findByTripCountry(@Param("country") country: Country, pageable: Pageable): Page<CommunityPost>

    @Query(
        value = "SELECT cp FROM CommunityPost cp JOIN FETCH cp.author JOIN FETCH cp.trip",
        countQuery = "SELECT COUNT(cp) FROM CommunityPost cp"
    )
    override fun findAll(pageable: Pageable): Page<CommunityPost>


    @Query("SELECT cp FROM CommunityPost cp JOIN FETCH cp.author JOIN FETCH cp.trip WHERE cp.id = :postId")
    fun findByIdWithDetails(@Param("postId") postId: Long): CommunityPost?

    @Query(
        value = "SELECT p FROM CommunityPost p JOIN FETCH p.author a JOIN FETCH p.trip t WHERE a.id = :memberId ORDER BY p.createdAt DESC",
        countQuery = "SELECT COUNT(p) FROM CommunityPost p WHERE p.author.id = :memberId"
    )
    fun findByAuthorMemberIdWithDetails(@Param("memberId") memberId: Long, pageable: Pageable): Page<CommunityPost>
}