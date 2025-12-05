package com.tribe.tribe_api.community.entity
import com.tribe.tribe_api.common.util.BaseTimeEntity
import jakarta.persistence.*

/**
 * 게시글에 포함되는 "Day N"별 상세 컨텐츠 엔티티
 *
 * @property communityPost 이 Day가 속한 부모 게시글
 * @property day 몇 일차인지 (예: 1, 2, 3)
 */
@Entity
class CommunityPostDay(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    val communityPost: CommunityPost,

    @Column(nullable = false)
    val day: Int,

    @OneToMany(mappedBy = "communityPostDay", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("id ASC")
    var categories: MutableList<CommunityPostCategory> = mutableListOf()


) : BaseTimeEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "post_day_id")
    val id: Long? = null
}