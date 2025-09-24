package com.tribe.tribe_api.community.entity

import com.tribe.tribe_api.member.entity.Member
import jakarta.persistence.*

@Entity
class CommunityOffer(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    val communityPost: CommunityPost,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    val provider: Member,

    @Lob
    var content: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: OfferStatus = OfferStatus.PENDING
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "offer_id")
    val id: Long? = null
}