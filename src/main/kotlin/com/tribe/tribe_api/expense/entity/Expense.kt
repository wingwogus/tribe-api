package com.tribe.tribe_api.expense.entity

import com.tribe.tribe_api.common.util.BaseTimeEntity
import com.tribe.tribe_api.expense.enumeration.InputMethod
import com.tribe.tribe_api.itinerary.entity.ItineraryItem
import com.tribe.tribe_api.trip.entity.Trip
import com.tribe.tribe_api.trip.entity.TripMember
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate

@Entity
class Expense(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    val trip: Trip,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    val itineraryItem: ItineraryItem,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payer_id", nullable = false)
    var payer: TripMember,

    var title: String,

    var totalAmount: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_method", nullable = false)
    var entryMethod: InputMethod,

    @Column(name = "payment_date", nullable = true)
    var paymentDate: LocalDate,

    @Column(name = "receipt_image_url", nullable = true)
    var receiptImageUrl: String? = null,

    // 통화 정보 필드 추가
    @Column(name = "currency", nullable = true)
    var currency: String? = null,

    ) : BaseTimeEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "expense_id")
    val id: Long? = null

    @OneToMany(mappedBy = "expense", cascade = [CascadeType.ALL], orphanRemoval = true)
    var expenseItems: MutableSet<ExpenseItem> = mutableSetOf()

    fun addExpenseItem(expenseItem: ExpenseItem) {
        this.expenseItems.add(expenseItem)
        expenseItem.expense = this
    }
}