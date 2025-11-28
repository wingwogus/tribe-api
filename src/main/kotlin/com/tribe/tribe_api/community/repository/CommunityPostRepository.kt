package com.tribe.tribe_api.community.repository

import com.tribe.tribe_api.community.entity.CommunityPost
import com.tribe.tribe_api.trip.entity.Country
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CommunityPostRepository : JpaRepository<CommunityPost, Long> {

    // 국가별 필터링 N+1 문제 해결 (author, trip JOIN FETCH)
    @Query(
        value = "SELECT DISTINCT cp FROM CommunityPost cp JOIN FETCH cp.author JOIN FETCH cp.trip WHERE cp.trip.country = :country",
        countQuery = "SELECT COUNT(cp) FROM CommunityPost cp WHERE cp.trip.country = :country"
    )
    fun findByTripCountry(@Param("country") country: Country, pageable: Pageable): Page<CommunityPost>

    // 전체 조회 N+1 문제 해결 (author, trip JOIN FETCH)
    @Query(
        value = "SELECT DISTINCT cp FROM CommunityPost cp JOIN FETCH cp.author JOIN FETCH cp.trip",
        countQuery = "SELECT COUNT(cp) FROM CommunityPost cp"
    )
    override fun findAll(pageable: Pageable): Page<CommunityPost>

    /**
     * [수정!] 상세 조회 시 N+1 문제를 방지하기 위해 모든 연관 엔티티를 JOIN FETCH
     * (Post -> Author, Trip, Days, Photos)
     */

    @Query(
        "SELECT DISTINCT cp FROM CommunityPost cp " +
                "JOIN FETCH cp.author " +
                "JOIN FETCH cp.trip t " +
                "WHERE cp.id = :postId"
    )
    fun findByIdWithDetails(@Param("postId") postId: Long): CommunityPost?

    @Query(
        value = "SELECT p FROM CommunityPost p JOIN FETCH p.author a JOIN FETCH p.trip t WHERE a.id = :memberId ORDER BY p.createdAt DESC",
        countQuery = "SELECT COUNT(p) FROM CommunityPost p WHERE p.author.id = :memberId"
    )
    fun findByAuthorMemberIdWithDetails(@Param("memberId") memberId: Long, pageable: Pageable): Page<CommunityPost>
}