package com.tribe.tribe_api.community.entity

import com.tribe.tribe_api.common.util.BaseTimeEntity
import jakarta.persistence.*

/**
 * 커뮤니티 게시글의 Day에 속한 카테고리 그룹 엔티티
 * 예: Day 1의 '식당', '관광' 등
 *
 * @property communityPostDay 이 카테고리가 속한 부모 Day
 * @property name 카테고리 이름 (예: "식당", "관광")
 * @property itineraries 이 카테고리에 속한 일정 목록
 */
@Entity
class CommunityPostCategory(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_day_id", nullable = false)
    val communityPostDay: CommunityPostDay, // 부모

    @Column(nullable = false)
    val name: String, // 카테고리 이름

    @OneToMany(mappedBy = "communityPostCategory", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("order ASC")
    var itineraries: MutableList<CommunityPostItinerary> = mutableListOf() // 자식 (일정)

) : BaseTimeEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "post_category_id")
    val id: Long? = null
}
