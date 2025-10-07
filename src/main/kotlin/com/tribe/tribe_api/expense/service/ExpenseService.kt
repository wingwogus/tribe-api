package com.tribe.tribe_api.expense.service

import com.tribe.tribe_api.common.exception.BusinessException
import com.tribe.tribe_api.common.exception.ErrorCode
import com.tribe.tribe_api.expense.dto.ExpenseDto
import com.tribe.tribe_api.expense.entity.Expense
import com.tribe.tribe_api.expense.entity.ExpenseItem
import com.tribe.tribe_api.expense.enumeration.InputMethod
import com.tribe.tribe_api.expense.repository.ExpenseAssignmentRepository
import com.tribe.tribe_api.expense.repository.ExpenseRepository
import com.tribe.tribe_api.itinerary.repository.ItineraryItemRepository
import com.tribe.tribe_api.trip.repository.TripMemberRepository
import com.tribe.tribe_api.trip.repository.TripRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class ExpenseService(
    private val expenseRepository: ExpenseRepository,
    private val expenseAssignmentRepository: ExpenseAssignmentRepository,
    private val tripRepository: TripRepository,
    private val tripMemberRepository: TripMemberRepository,
    private val itineraryItemRepository: ItineraryItemRepository
) {

    //특정 일정에 대한 새로운 비용(지출) 내역을 등록
    @Transactional
    fun createExpense(tripId: Long, itineraryItemId: Long, request: ExpenseDto.CreateRequest): ExpenseDto.CreateResponse {

        // --- 👇 [추가] 품목 합계와 총액 비교 검증 로직 ---
        val itemsTotal = request.items.fold(BigDecimal.ZERO) { acc, item -> acc + item.price }
        if (request.totalAmount.compareTo(itemsTotal) != 0) {
            throw BusinessException(ErrorCode.EXPENSE_TOTAL_AMOUNT_MISMATCH)
        }
        // --- 검증 로직 끝 ---

        val trip = tripRepository.findById(tripId)
            .orElseThrow { EntityNotFoundException("Trip not found") }
        val payer = tripMemberRepository.findById(request.payerId)
            .orElseThrow { EntityNotFoundException("Member not found") }
        val itineraryItem = itineraryItemRepository.findById(itineraryItemId)
            .orElseThrow { EntityNotFoundException("ItineraryItem not found with id: $itineraryItemId") }

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
            .orElseThrow { EntityNotFoundException("Expense Not Found: $expenseId") }

        return ExpenseDto.DetailResponse.from(expense)
    }

    //특정 비용 수정
    @Transactional
    fun updateExpense(expenseId: Long, request: ExpenseDto.UpdateRequest): ExpenseDto.DetailResponse {
        val expense = expenseRepository.findById(expenseId)
            .orElseThrow { EntityNotFoundException("Expense Not Found: $expenseId") }

        val payer = tripMemberRepository.findById(request.payerId)
            .orElseThrow { EntityNotFoundException("Payer Not Found ${request.payerId}") }

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
                    ?: throw EntityNotFoundException("Item Not Found: ${request.itemId}")

                existingItem.name = request.itemName
                existingItem.price = request.price
            }
        }
    }

    // 멤버별 배분 정보 등록/수정
    @Transactional
    fun assignParticipants(expenseId: Long, request: ExpenseDto.ParticipantAssignRequest): ExpenseDto.DetailResponse {
        val expense = expenseRepository.findById(expenseId)
            .orElseThrow { EntityNotFoundException("Expense Not Found $expenseId") }

        val expenseItemsById = expense.expenseItems.associateBy { it.id }

        request.items.forEach { itemAssignmentDto ->
            val itemId = itemAssignmentDto.itemId

            val expenseItem = expenseItemsById[itemId]
                ?: throw IllegalArgumentException("지출(ID: $expenseId)에 해당 항목(ID: $itemId)이 존재하지 않습니다.")

            expenseAssignmentRepository.deleteByExpenseItemId(itemId)
            expenseItem.assignments.clear() // 영속성 컨텍스트의 캐시와 동기화

            val participants = tripMemberRepository.findAllById(itemAssignmentDto.participantIds)
            if (participants.size != itemAssignmentDto.participantIds.size) {
                throw EntityNotFoundException("Participant Not Found")
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