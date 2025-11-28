package com.tribe.tribe_api.community.entity
import com.tribe.tribe_api.common.util.BaseTimeEntity
import jakarta.persistence.*

/**
 * 게시글에 포함되는 "Day N"별 상세 컨텐츠 엔티티
 *
 * @property communityPost 이 Day가 속한 부모 게시글
 * @property day 몇 일차인지 (예: 1, 2, 3)
 * @property content 해당 Day에 대한 사용자의 설명 (블로그 글)
 * @property photos 해당 Day에 첨부된 사진 목록
 */
@Entity
class CommunityPostDay(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    val communityPost: CommunityPost,

    @Column(nullable = false)
    val day: Int,

    @Lob
    var content: String, // Day 전체에 대한 요약 설명

    @OneToMany(mappedBy = "communityPostDay", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("order ASC")
    var itineraries: MutableList<CommunityPostItinerary> = mutableListOf()


) : BaseTimeEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "day_id")
    val id: Long? = null
}