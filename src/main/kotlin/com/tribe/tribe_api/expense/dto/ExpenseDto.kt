package com.tribe.tribe_api.expense.dto

import com.tribe.tribe_api.expense.entity.Expense
import com.tribe.tribe_api.expense.entity.ExpenseItem
import com.tribe.tribe_api.trip.entity.TripMember
import jakarta.validation.Valid
import jakarta.validation.constraints.*
import java.math.BigDecimal
import java.time.LocalDate

object ExpenseDto {
    data class CreateRequest(
        val tripId: Long,

        @field:NotBlank(message = "지출 이름은 필수입니다.")
        val expenseTitle: String,

        @field:PositiveOrZero(message = "총액은 0 또는 양수여야 합니다.")
        val totalAmount: BigDecimal?, //SCAN 시에는 값을 보내지 않아도 되도록

        @field:NotNull(message = "여정 아이템 ID는 필수입니다.")
        val itineraryItemId: Long,

        @field:NotNull(message = "결제자 ID는 필수입니다.")
        val payerId: Long,

        @field:NotBlank(message = "입력 방식은 필수입니다.")
        val inputMethod: String,

        // 통화 필드 추가 (USD, JPY 등)
        @field:NotBlank(message = "통화 정보는 필수입니다.")
        val currency: String = "KRW",

        @field:Valid
        val items: List<ItemCreate> = emptyList()
    )

    data class ItemCreate(
        @field:NotBlank(message = "항목 이름은 필수입니다.")
        val itemName: String,

        @field:NotNull(message = "항목 가격은 필수입니다.")
        @field:PositiveOrZero(message = "항목 가격은 0 또는 양수여야 합니다.")
        val price: BigDecimal
    )

    data class UpdateRequest(

        @field:NotBlank(message = "지출 제목은 필수입니다.")
        val expenseTitle: String,

        @field:NotNull(message = "총액은 필수입니다.")
        @field:PositiveOrZero(message = "총액은 0 또는 양수여야 합니다.")
        val totalAmount: BigDecimal,

        @field:NotNull(message = "결제자 ID는 필수입니다.")
        val payerId: Long,

        @field:Valid
        val items: List<ItemUpdate> = emptyList()
    )

    data class ItemUpdate(
        val itemId: Long?, //Null이라면 새 항목

        @field:NotBlank(message = "항목 이름은 비워둘 수 없습니다.")
        val itemName: String,

        @field:NotNull(message = "항목 가격은 필수입니다.")
        val price: BigDecimal
    )

    data class ParticipantAssignRequest(

        @field:Valid
        @field:NotEmpty(message = "items는 필수입니다.")
        val items: List<ItemAssignment> = emptyList()
    )

    data class ItemAssignment(
        @field:NotNull(message = "항목 ID는 필수입니다.")
        val itemId: Long,

        @field:NotNull(message = "참여자 목록은 필수입니다.")
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

    // Gemini의 JSON 응답을 파싱하기 위한 DTO
    data class OcrResponse(
        val totalAmount: BigDecimal,
        val items: List<OcrItem>,
        val subtotal: BigDecimal?, // 항목 합계 (소계)
        val tax: BigDecimal?,      // 세금
        val tip: BigDecimal?,      // 팁
        val discount: BigDecimal?  // 할인
    )

    data class OcrItem(
        val itemName: String,
        val price: BigDecimal
    )

    // 어떤 항목의 배분 내역을 삭제할지 서버에 알려주는 역할
    data class AssignmentClearRequest(

        @field:NotEmpty(message = "삭제할 배분 항목 ID 리스트는 비어있을 수 없습니다.")
        val itemIds: List<Long>
    )

}