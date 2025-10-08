package com.tribe.tribe_api.expense.service

import com.tribe.tribe_api.common.exception.BusinessException
import com.tribe.tribe_api.common.exception.ErrorCode
import com.tribe.tribe_api.common.util.security.SecurityUtil
import com.tribe.tribe_api.expense.dto.ExpenseDto
import com.tribe.tribe_api.expense.entity.Expense
import com.tribe.tribe_api.expense.entity.ExpenseItem
import com.tribe.tribe_api.expense.enumeration.InputMethod
import com.tribe.tribe_api.expense.repository.ExpenseAssignmentRepository
import com.tribe.tribe_api.expense.repository.ExpenseRepository
import com.tribe.tribe_api.itinerary.repository.ItineraryItemRepository
import com.tribe.tribe_api.trip.repository.TripMemberRepository
import com.tribe.tribe_api.trip.repository.TripRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ExpenseService(
    private val expenseRepository: ExpenseRepository,
    private val expenseAssignmentRepository: ExpenseAssignmentRepository,
    private val tripRepository: TripRepository,
    private val tripMemberRepository: TripMemberRepository,
    private val itineraryItemRepository: ItineraryItemRepository
) {

    private fun verifyTripIdParticipation(tripId: Long){
        val currentMemberId = SecurityUtil.getCurrentMemberId()
            ?: throw BusinessException(ErrorCode.UNAUTHORIZED_ACCESS)

        if(!tripMemberRepository.existsByTripIdAndMemberId(tripId, currentMemberId)){
            throw BusinessException(ErrorCode.NOT_A_TRIP_MEMBER)
        }
    }

    //특정 일정에 대한 새로운 비용(지출) 내역을 등록
    @Transactional
    fun createExpense(tripId: Long, itineraryItemId: Long, request: ExpenseDto.CreateRequest): ExpenseDto.CreateResponse {
        verifyTripIdParticipation(tripId)
        val trip = tripRepository.findById(tripId)
            .orElseThrow { BusinessException(ErrorCode.TRIP_NOT_FOUND) }
        val payer = tripMemberRepository.findById(request.payerId)
            .orElseThrow { BusinessException(ErrorCode.MEMBER_NOT_FOUND) }
        val itineraryItem = itineraryItemRepository.findById(itineraryItemId)
            .orElseThrow { BusinessException(ErrorCode.ITINERARY_ITEM_NOT_FOUND) }

        val expense = Expense(
            trip = trip,
            itineraryItem = itineraryItem,
            payer = payer,
            title = request.expenseTitle,
            totalAmount = request.totalAmount,
            entryMethod = InputMethod.valueOf(request.inputMethod.uppercase()),
            paymentDate = request.paymentDate,
            receiptImageUrl = request.receiptImageUrl
        )

        request.items.forEach { itemDto ->
            val expenseItem = ExpenseItem(
                expense = expense,
                name = itemDto.itemName,
                price = itemDto.price
            )
            expense.addExpenseItem(expenseItem)
        }

        val savedExpense = expenseRepository.save(expense)
        return ExpenseDto.CreateResponse.from(savedExpense)
    }

    //특정 비용 상세 조회
    @Transactional(readOnly = true)
    fun getExpenseDetail(expenseId: Long): ExpenseDto.DetailResponse {
        val expense = expenseRepository.findById(expenseId)
            .orElseThrow { BusinessException(ErrorCode.EXPENSE_NOT_FOUND) }

        expense.trip.id?.let { tripId ->
            verifyTripIdParticipation(tripId)
        } ?: throw BusinessException(ErrorCode.SERVER_ERROR)

        return ExpenseDto.DetailResponse.from(expense)
    }

    //특정 비용 수정
    @Transactional
    fun updateExpense(expenseId: Long, request: ExpenseDto.UpdateRequest): ExpenseDto.DetailResponse {
        val expense = expenseRepository.findById(expenseId)
            .orElseThrow { BusinessException(ErrorCode.EXPENSE_NOT_FOUND) }

        expense.trip.id?.let { tripId ->
            verifyTripIdParticipation(tripId)
        } ?: throw BusinessException(ErrorCode.SERVER_ERROR)

        val payer = tripMemberRepository.findById(request.payerId)
            .orElseThrow { BusinessException(ErrorCode.MEMBER_NOT_FOUND) }

        expense.title = request.expenseTitle
        expense.totalAmount = request.totalAmount
        expense.paymentDate = request.paymentDate
        expense.payer = payer

        updateExpenseItems(expense, request.items)

        return ExpenseDto.DetailResponse.from(expense)
    }

    // Item 리스트를 요청 DTO의 상태와 동일하게 업데이트
    private fun updateExpenseItems(expense: Expense, itemUpdateRequests: List<ExpenseDto.ItemUpdate>) {
        val requestedItemIds = itemUpdateRequests.mapNotNull { it.itemId }.toSet()
        val itemsToRemove = expense.expenseItems.filter { it.id !in requestedItemIds }
        expense.expenseItems.removeAll(itemsToRemove)

        itemUpdateRequests.forEach { request ->
            if (request.itemId == null) {
                val newItem = ExpenseItem(
                    expense = expense,
                    name = request.itemName,
                    price = request.price
                )
                expense.addExpenseItem(newItem)
            } else {
                val existingItem = expense.expenseItems.find { it.id == request.itemId }
                    ?: throw BusinessException(ErrorCode.EXPENSE_ITEM_NOT_FOUND)
                existingItem.name = request.itemName
                existingItem.price = request.price
            }
        }

        // 멤버별 배분 정보 등록/수정
        @Transactional
        fun assignParticipants(expenseId: Long, request: ExpenseDto.ParticipantAssignRequest): ExpenseDto.DetailResponse {
            val expense = expenseRepository.findById(expenseId)
                .orElseThrow { BusinessException(ErrorCode.EXPENSE_NOT_FOUND) }

            expense.trip.id?.let { tripId ->
                verifyTripIdParticipation(tripId)
            } ?: throw BusinessException(ErrorCode.SERVER_ERROR)

            val expenseItemsById = expense.expenseItems.associateBy { it.id }

            request.items.forEach { itemAssignmentDto ->
                val itemId = itemAssignmentDto.itemId

                val expenseItem = expenseItemsById[itemId]
                    ?: throw BusinessException(ErrorCode.EXPENSE_ITEM_NOT_IN_EXPENSE)

                expenseAssignmentRepository.deleteByExpenseItemId(itemId)
                expenseItem.assignments.clear() // 영속성 컨텍스트의 캐시와 동기화

                val participants = tripMemberRepository.findAllById(itemAssignmentDto.participantIds)
                if (participants.size != itemAssignmentDto.participantIds.size) {
                    throw BusinessException(ErrorCode.MEMBER_NOT_FOUND)
                }

                participants.forEach { participant ->
                    val newAssignment = com.tribe.tribe_api.expense.entity.ExpenseAssignment(
                        expenseItem = expenseItem,
                        tripMember = participant
                    )
                    expenseItem.assignments.add(newAssignment)
                }
            }

            return ExpenseDto.DetailResponse.from(expense)
        }
    }

}