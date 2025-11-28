package com.tribe.tribe_api.community.entity

import com.tribe.tribe_api.common.util.BaseTimeEntity
import com.tribe.tribe_api.itinerary.entity.Place
import jakarta.persistence.*

/**
 * 커뮤니티 게시글의 Day에 속한 개별 일정(itinerary) 아이템 엔티티
 * 예: Day 1의 '에펠탑 방문'
 *
 * @property communityPostDay 이 일정이 속한 부모 Day
 * @property place 이 일정과 연결된 장소 정보 (지도 마커 표시용)
 * @property order Day 내에서의 일정 순서
 * @property content 이 일정에 대한 사용자 작성 소개글
 * @property photos 이 일정에 첨부된 사진 목록
 */
@Entity
class CommunityPostItinerary(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "day_id", nullable = false)
    val communityPostDay: CommunityPostDay,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id") // 장소 정보는 선택적일 수 있음 ('호텔에서 휴식' 등)
    val place: Place?,

    @Column(name = "item_order", nullable = false)
    val order: Int,

    @Lob
    var memo: String?,

    @Lob
    var content: String,

    @OneToMany(mappedBy = "communityPostItinerary", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("id ASC")
    var photos: MutableList<CommunityPostItineraryPhoto> = mutableListOf()
) : BaseTimeEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "post_itinerary_id")
    val id: Long? = null
}
