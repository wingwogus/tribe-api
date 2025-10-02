package com.tribe.tribe_api.expense.dto

import com.tribe.tribe_api.expense.entity.Expense
import com.tribe.tribe_api.expense.entity.ExpenseItem
import com.tribe.tribe_api.trip.entity.TripMember
import java.math.BigDecimal
import java.time.LocalDate

object ExpenseDto {
    data class CreateRequest(
        val expenseTitle: String,
        val totalAmount: BigDecimal,
        val receiptImageUrl: String?,
        val payerId: Long,
        val paymentDate: LocalDate,
        val inputMethod: String,
        val items: List<ItemCreate> = emptyList()
    )

    data class ItemCreate(
        val itemName: String,
        val price: BigDecimal
    )

    data class UpdateRequest(
        val expenseTitle: String,
        val totalAmount: BigDecimal,
        val paymentDate: LocalDate,
        val payerId: Long,
        val items: List<ItemUpdate> = emptyList()
    )

    data class ItemUpdate(
        val itemId: Long?,
        val itemName: String,
        val price: BigDecimal
    )

    data class ParticipantAssignRequest(
        val items: List<ItemAssignment> = emptyList()
    )

    data class ItemAssignment(
        val itemId: Long,
        val participantIds: List<Long> = emptyList()
    )

    data class CreateResponse(
        val expenseId: Long,
        val expenseTitle: String,
        val totalAmount: BigDecimal,
        val payer: ParticipantInfo,
        val paymentDate: LocalDate,
        val items: List<ItemSimpleResponse>
    ) {
        companion object {
            fun from(expense: Expense): CreateResponse {
                return CreateResponse(
                    expenseId = expense.id!!,
                    expenseTitle = expense.title,
                    totalAmount = expense.totalAmount,
                    payer = ParticipantInfo.from(expense.payer),
                    paymentDate = expense.paymentDate,
                    items = expense.expenseItems.map { ItemSimpleResponse.from(it) }
                )
            }
        }
    }

    data class DetailResponse(
        val expenseId: Long,
        val expenseTitle: String,
        val totalAmount: BigDecimal,
        val paymentDate: LocalDate,
        val payer: ParticipantInfo,
        val items: List<ItemDetailResponse>
    ) {
        companion object {
            fun from(expense: Expense): DetailResponse {
                return DetailResponse(
                    expenseId = expense.id!!,
                    expenseTitle = expense.title,
                    totalAmount = expense.totalAmount,
                    paymentDate = expense.paymentDate,
                    payer = ParticipantInfo.from(expense.payer),
                    items = expense.expenseItems.map { ItemDetailResponse.from(it) }
                )
            }
        }
    }

    data class ItemSimpleResponse(
        val itemId: Long,
        val itemName: String,
        val price: BigDecimal
    ) {
        companion object {
            fun from(item: ExpenseItem): ItemSimpleResponse {
                return ItemSimpleResponse(itemId = item.id!!, itemName = item.name, price = item.price)
            }
        }
    }

    data class ItemDetailResponse(
        val itemId: Long,
        val itemName: String,
        val price: BigDecimal,
        val participants: List<ParticipantInfo>
    ) {
        companion object {
            fun from(item: ExpenseItem): ItemDetailResponse {
                return ItemDetailResponse(
                    itemId = item.id!!,
                    itemName = item.name,
                    price = item.price,
                    participants = item.assignments.map { ParticipantInfo.from(it.tripMember) }
                )
            }
        }
    }

    data class ParticipantInfo(
        val id: Long,
        val name: String,
        val isGuest: Boolean
    ) {
        companion object {
            fun from(tripMember: TripMember): ParticipantInfo {
                return ParticipantInfo(
                    id = tripMember.id!!,
                    name = tripMember.name,
                    isGuest = tripMember.isGuest
                )
            }
        }
    }

}