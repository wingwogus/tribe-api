package com.tribe.tribe_api.community.entity

import com.tribe.tribe_api.common.util.BaseTimeEntity
import jakarta.persistence.*

/**
 * 커뮤니티 게시글의 개별 일정(Itinerary)에 첨부된 사진 엔티티
 *
 * @property communityPostItinerary 이 사진이 속한 부모 Itinerary
 * @property imageUrl 이미지 URL
 */
@Entity
class CommunityPostItineraryPhoto(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_itinerary_id", nullable = false)
    val communityPostItinerary: CommunityPostItinerary,

    @Column(nullable = false)
    val imageUrl: String
) : BaseTimeEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "post_photo_id")
    val id: Long? = null
}
