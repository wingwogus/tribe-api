package com.tribe.tribe_api.community.entity

import com.tribe.tribe_api.itinerary.entity.ItineraryItem
import com.tribe.tribe_api.trip.entity.TripMember
import jakarta.persistence.*

@Entity
class CommunityPost(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "post_id")
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    val itineraryItem: ItineraryItem,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    val author: TripMember,

    var title: String,

    @Lob
    var content: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: PostStatus = PostStatus.OPEN,
) {
    @OneToMany(mappedBy = "communityPost", cascade = [CascadeType.ALL], orphanRemoval = true)
    var offers: MutableList<CommunityOffer> = mutableListOf()
}