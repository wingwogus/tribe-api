package com.tribe.tribe_api.itinerary.entity

import com.tribe.tribe_api.common.util.BaseTimeEntity
import com.tribe.tribe_api.community.entity.CommunityPost
import com.tribe.tribe_api.expense.entity.Expense
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
class ItineraryItem(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    val category: Category,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id")
    val place: Place?,

    @Column(nullable = false)
    var title: String, // **[추가]** 일정 자체의 제목 (장소 이름과 다를 수 있음)

    var startTime: LocalDateTime?, // **[추가]** 일정 시작 시간

    var endTime: LocalDateTime?, // **[추가]** 일정 종료 시간

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
