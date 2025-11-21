package com.tribe.tribe_api.expense.entity

import com.tribe.tribe_api.trip.entity.TripMember
import jakarta.persistence.*
import java.math.BigDecimal

@Entity
class ExpenseAssignment(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expense_item_id", nullable = false)
    val expenseItem: ExpenseItem,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_member_id", nullable = false)
    val tripMember: TripMember,

    // 분담금액 저장할 필드
    @Column(nullable = false)
    var amount: BigDecimal

) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "assignment_id")
    val id: Long? = null
}