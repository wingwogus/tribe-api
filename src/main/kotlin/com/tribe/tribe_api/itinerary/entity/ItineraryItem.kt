package com.tribe.tribe_api.itinerary.entity

import com.tribe.tribe_api.common.util.BaseTimeEntity
import com.tribe.tribe_api.community.entity.CommunityPost
import com.tribe.tribe_api.expense.entity.Expense
import jakarta.persistence.*

@Entity
class ItineraryItem(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    val category: Category,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id")
    val place: Place?,

    @Column(name = "item_order")
    var order: Int,

    @Lob
    var memo: String? = null
) : BaseTimeEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "item_id")
    val id: Long? = null

    @OneToMany(mappedBy = "itineraryItem", cascade = [CascadeType.ALL])
    var expenses: MutableList<Expense> = mutableListOf()

    @OneToMany(mappedBy = "itineraryItem", cascade = [CascadeType.ALL])
    var communityPosts: MutableList<CommunityPost> = mutableListOf()
}