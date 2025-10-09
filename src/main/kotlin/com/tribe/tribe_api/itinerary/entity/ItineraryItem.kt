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
    @JoinColumn(name = "place_id", nullable = false)
    val place: Place?,

    var title: String?, // 사용자가 직접 입력하는 일정 (placeId를 통해 지도에서 받아오는 장소가 없을경우나
                        // '호텔에서 휴식' 같은 직접 입력해야하는 일정용

    var time: LocalDateTime? , // 사용자가 직접 입력하는 시간 (할수도 있고 안할수도 있음)

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
