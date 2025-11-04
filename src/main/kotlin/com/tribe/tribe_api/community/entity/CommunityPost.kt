package com.tribe.tribe_api.community.entity

import com.tribe.tribe_api.common.util.BaseTimeEntity
import com.tribe.tribe_api.member.entity.Member
import com.tribe.tribe_api.trip.entity.Trip
import jakarta.persistence.*

/**
 * 여행 일정 공유 게시글 엔티티
 *
 * @property author 게시글 작성자 (Member)
 * @property trip 공유된 여행 (Trip)
 * @property title 사용자가 입력한 게시글 제목
 * @property content 사용자가 입력한 게시글 내용
 * @property representativeImageUrl 사용자가 업로드한 대표 이미지 URL (선택 사항)
 */

@Entity
class CommunityPost(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    val author: Member,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    val trip: Trip,

    @Column(nullable = false)
    var title: String,

    @Lob
    var content: String,

    var representativeImageUrl: String? = null // 사진 (선택 사항)

) : BaseTimeEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "post_id")
    val id: Long? = null
}

