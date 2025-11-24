package com.tribe.tribe_api.community.entity

import com.tribe.tribe_api.common.util.BaseTimeEntity
import jakarta.persistence.*

/**
 * "Day N" 컨텐츠에 첨부된 개별 사진 엔티티
 *
 * @property communityPostDay 이 사진이 속한 부모 Day
 * @property imageUrl Cloudinary 등에 업로드된 이미지의 URL
 */
@Entity
class CommunityPostDayPhoto(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "day_id", nullable = false)
    val communityPostDay: CommunityPostDay,

    @Column(nullable = false)
    val imageUrl: String

) : BaseTimeEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "photo_id")
    val id: Long? = null
}