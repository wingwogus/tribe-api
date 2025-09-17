package com.tribe.tribe_api.trip.entity

import com.tribe.tribe_api.community.entity.CommunityPost
import com.tribe.tribe_api.expense.entity.Expense
import com.tribe.tribe_api.expense.entity.ExpenseAssignment
import com.tribe.tribe_api.member.entity.Member
import jakarta.persistence.*

@Entity
class TripMember(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "trip_member_id")
    val id: Long? = null,

    // 게스트 기능을 위해 Nullable('?')로 선언
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = true)
    val member: Member?,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    val trip: Trip,

    var guestNickname: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var role: TripRole,
) {
    @OneToMany(mappedBy = "payer", cascade = [CascadeType.ALL])
    var paidExpenses: MutableList<Expense> = mutableListOf()

    @OneToMany(mappedBy = "tripMember", cascade = [CascadeType.ALL])
    var assignments: MutableList<ExpenseAssignment> = mutableListOf()

    @OneToMany(mappedBy = "author", cascade = [CascadeType.ALL])
    var communityPosts: MutableList<CommunityPost> = mutableListOf()
}