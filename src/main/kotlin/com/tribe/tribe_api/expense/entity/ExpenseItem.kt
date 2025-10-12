package com.tribe.tribe_api.expense.entity

import jakarta.persistence.*
import java.math.BigDecimal

@Entity
class ExpenseItem(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expense_id", nullable = false)
    var expense: Expense,

    var name: String,

    var price: BigDecimal
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "expense_item_id")
    val id: Long? = null

    @OneToMany(mappedBy = "expenseItem", cascade = [CascadeType.ALL], orphanRemoval = true)
    var assignments: MutableList<ExpenseAssignment> = mutableListOf()
}