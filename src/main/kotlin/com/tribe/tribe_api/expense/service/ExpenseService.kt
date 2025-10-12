package com.tribe.tribe_api.expense.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.tribe.tribe_api.common.exception.BusinessException
import com.tribe.tribe_api.common.exception.ErrorCode
import com.tribe.tribe_api.common.util.security.SecurityUtil
import com.tribe.tribe_api.common.util.service.GeminiApiClient
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
import org.springframework.web.multipart.MultipartFile
import java.math.BigDecimal
import java.math.RoundingMode

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

        // --- 💡 수정된 부분 시작 ---
        // 1. 요청된 아이템들의 가격 합계를 계산합니다.
        val itemsTotal = request.items.fold(BigDecimal.ZERO) { acc, item -> acc + item.price }

        // 2. 요청된 totalAmount와 아이템 합계가 일치하는지 검증합니다.
        if (request.totalAmount.compareTo(itemsTotal) != 0) {
            throw BusinessException(ErrorCode.EXPENSE_TOTAL_AMOUNT_MISMATCH)
        }
        // --- 💡 수정된 부분 끝 ---

        expense.title = request.expenseTitle
        expense.totalAmount = request.totalAmount
        expense.paymentDate = request.paymentDate
        expense.payer = payer

        updateExpenseItems(expense, request.items)

        // --- 💡 추가된 부분 시작 ---
        // 3. 금액이 변경되었으므로, 기존 배분 내역을 모두 삭제하여 데이터 정합성을 유지합니다.
        //    사용자는 이 API 호출 후에 다시 배분(/assignments)을 설정해야 합니다.
        expenseAssignmentRepository.deleteByExpenseId(expenseId)
        // --- 💡 추가된 부분 끝 ---

        return ExpenseDto.DetailResponse.from(expense)
    }


    // Item 리스트를 요청 DTO의 상태와 동일하게 업데이트
    private fun updateExpenseItems(expense: Expense, itemUpdateRequests: List<ExpenseDto.ItemUpdate>) {
        val requestedItemIds = itemUpdateRequests.mapNotNull { it.itemId }.toSet()
        val itemsToRemove = expense.expenseItems.filter { it.id !in requestedItemIds }
        expense.expenseItems.removeAll(itemsToRemove)

        itemUpdateRequests.forEach { request ->
            // itemId가 null(또는 0)이면 새 항목으로 간주하고 추가
            if (request.itemId <= 0L) {
                val newItem = ExpenseItem(
                    expense = expense,
                    name = request.itemName,
                    price = request.price
                )
                expense.addExpenseItem(newItem)
            } else { // 기존 항목은 수정
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

        verifyTripIdParticipation(tripId)

        val expenseItemsById = expense.expenseItems.associateBy { it.id }

        request.items.forEach { itemAssignmentDto ->
            val itemId = itemAssignmentDto.itemId
            val expenseItem = expenseItemsById[itemId]
                ?: throw BusinessException(ErrorCode.EXPENSE_ITEM_NOT_IN_EXPENSE)

            // 1/N 분배 로직을 여기에 구현

            // 1. 기존 분배 내역 삭제
            expenseAssignmentRepository.deleteByExpenseItemId(itemId)
            expenseItem.assignments.clear()

            // 2. 참여자 정보 확인
            val participants = tripMemberRepository.findAllById(itemAssignmentDto.participantIds)
            if (participants.size != itemAssignmentDto.participantIds.size) {
                throw BusinessException(ErrorCode.MEMBER_NOT_FOUND)
            }

            val participantCount = participants.size.toBigDecimal()
            if (participantCount > BigDecimal.ZERO) {
                // 3. 1/N 금액 계산 (1원 오차 처리 포함)
                val baseAmount = expenseItem.price.divide(participantCount, 0, RoundingMode.DOWN)
                val remainder = expenseItem.price.subtract(baseAmount.multiply(participantCount))

                participants.forEachIndexed { index, participant ->
                    // 첫 번째 참여자에게 나머지 금액을 더해줍니다.
                    val amount = if (index == 0) baseAmount + remainder else baseAmount

                    // 4. 계산된 금액으로 ExpenseAssignment 생성
                    val newAssignment = com.tribe.tribe_api.expense.entity.ExpenseAssignment(
                        expenseItem = expenseItem,
                        tripMember = participant,
                        amount = amount
                    )
                    expenseItem.assignments.add(newAssignment)
                }
            }
        }

        return ExpenseDto.DetailResponse.from(expense)
    }
}