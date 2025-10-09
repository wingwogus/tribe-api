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
import com.fasterxml.jackson.databind.ObjectMapper
import com.tribe.tribe_api.common.util.service.GeminiApiClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import org.springframework.web.multipart.MultipartFile

@Service
class ExpenseService(
    private val expenseRepository: ExpenseRepository,
    private val expenseAssignmentRepository: ExpenseAssignmentRepository,
    private val tripRepository: TripRepository,
    private val tripMemberRepository: TripMemberRepository,
    private val itineraryItemRepository: ItineraryItemRepository,
    private val geminiApiClient: GeminiApiClient,
    private val objectMapper: ObjectMapper
) {

    private fun verifyTripIdParticipation(tripId: Long){
        val currentMemberId = SecurityUtil.getCurrentMemberId()
            ?: throw BusinessException(ErrorCode.UNAUTHORIZED_ACCESS)

        if(!tripMemberRepository.existsByTripIdAndMemberId(tripId, currentMemberId)){
            throw BusinessException(ErrorCode.NOT_A_TRIP_MEMBER)
        }
    }

    private fun findExpenseAndValidate(expenseId: Long, tripId: Long): Expense {
        val expense = expenseRepository.findById(expenseId)
            .orElseThrow { BusinessException(ErrorCode.EXPENSE_NOT_FOUND) }

        if (expense.trip.id != tripId) { throw BusinessException(ErrorCode.NO_AUTHORITY_TRIP) }

        return expense
    }

    //특정 일정에 대한 새로운 비용(지출) 내역을 등록
    @Transactional
    fun createExpense(
        tripId: Long,
        itineraryItemId: Long,
        request: ExpenseDto.CreateRequest,
        imageFile: MultipartFile?
    ): ExpenseDto.CreateResponse {

        verifyTripIdParticipation(tripId)

        val trip = tripRepository.findById(tripId)
            .orElseThrow { BusinessException(ErrorCode.TRIP_NOT_FOUND) }
        val payer = tripMemberRepository.findById(request.payerId)
            .orElseThrow { BusinessException(ErrorCode.MEMBER_NOT_FOUND) }
        val itineraryItem = itineraryItemRepository.findById(itineraryItemId)
            .orElseThrow { BusinessException(ErrorCode.ITINERARY_ITEM_NOT_FOUND) }

        val processedData = when (request.inputMethod.uppercase()){
            "SCAN" -> {
                val file = imageFile ?: throw BusinessException(ErrorCode.INVALID_INPUT_VALUE)
                processReceipt(file)
            }
            "HANDWRITE" -> {
                ExpenseDto.OcrResponse(
                    totalAmount = request.totalAmount,
                    items = request.items.map { ExpenseDto.OcrItem(it.itemName, it.price) }
                )
            }
            else -> throw BusinessException(ErrorCode.INVALID_INPUT_VALUE)
        }

        val itemsTotal = processedData.items.fold(BigDecimal.ZERO) { acc, item -> acc + item.price }
        if (processedData.totalAmount.compareTo(itemsTotal) != 0) {
            throw BusinessException(ErrorCode.EXPENSE_TOTAL_AMOUNT_MISMATCH)
        }

        val expense = Expense(
            trip = trip,
            itineraryItem = itineraryItem,
            payer = payer,
            title = request.expenseTitle,
            totalAmount = processedData.totalAmount,
            entryMethod = InputMethod.valueOf(request.inputMethod.uppercase()),
            paymentDate = request.paymentDate,
            receiptImageUrl = request.receiptImageUrl
        )

        processedData.items.forEach { itemDto ->
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

    private fun processReceipt(imageFile: MultipartFile): ExpenseDto.OcrResponse {
        val base64Image = java.util.Base64.getEncoder().encodeToString(imageFile.bytes)
        val prompt = """
            이 영수증 이미지에서 지출 총액(totalAmount)과 모든 지출 항목(items)을 추출해줘.
            각 항목은 이름(itemName)과 가격(price)을 가져야 해.
            결과는 반드시 아래와 같은 JSON 형식으로만 응답해줘.
            
            {
              "totalAmount": 15000,
              "items": [
                { "itemName": "아메리카노", "price": 4500 },
                { "itemName": "카페라떼", "price": 5000 }
              ]
            }
        """.trimIndent()

        val geminiResponseJson = geminiApiClient.generateContentFromImage(
            prompt = prompt,
            base64Image = base64Image,
            mimeType = imageFile.contentType ?: "image/jpeg"
        ) ?: throw BusinessException(ErrorCode.AI_FEEDBACK_ERROR)

        val firstBraceIndex = geminiResponseJson.indexOf('{')
        val lastBraceIndex = geminiResponseJson.lastIndexOf('}')

        if (firstBraceIndex == -1 || lastBraceIndex == -1) {
            throw BusinessException(ErrorCode.AI_RESPONSE_PARSING_ERROR)
        }

        val sanitizedJson = geminiResponseJson.substring(firstBraceIndex, lastBraceIndex + 1)

        return try {
            objectMapper.readValue(sanitizedJson, ExpenseDto.OcrResponse::class.java)
        } catch (e: Exception) {
            throw BusinessException(ErrorCode.AI_RESPONSE_PARSING_ERROR)
        }
    }

    //특정 비용 상세 조회
    @Transactional(readOnly = true)
    fun getExpenseDetail(tripId: Long, expenseId: Long): ExpenseDto.DetailResponse {
        val expense = findExpenseAndValidate(expenseId, tripId)

        expense.trip.id?.let { tripId ->
            verifyTripIdParticipation(tripId)
        } ?: throw BusinessException(ErrorCode.SERVER_ERROR)

        return ExpenseDto.DetailResponse.from(expense)
    }

    //특정 비용 수정
    @Transactional
    fun updateExpense(tripId: Long, expenseId: Long, request: ExpenseDto.UpdateRequest): ExpenseDto.DetailResponse {
        val expense = findExpenseAndValidate(expenseId, tripId)

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
    }

    // 멤버별 배분 정보 등록/수정
    @Transactional
    fun assignParticipants(tripId: Long, expenseId: Long, request: ExpenseDto.ParticipantAssignRequest): ExpenseDto.DetailResponse {
        val expense = findExpenseAndValidate(expenseId, tripId)

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