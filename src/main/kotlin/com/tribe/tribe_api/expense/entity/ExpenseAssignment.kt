package com.tribe.tribe_api.expense.entity

import com.tribe.tribe_api.trip.entity.TripMember
import jakarta.persistence.*

@Entity
class ExpenseAssignment(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "assignment_id")
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expense_item_id", nullable = false)
    val expenseItem: ExpenseItem,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_member_id", nullable = false)
    val tripMember: TripMember
)