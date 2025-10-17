package com.tribe.tribe_api.expense.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.tribe.tribe_api.common.exception.BusinessException
import com.tribe.tribe_api.common.exception.ErrorCode
import com.tribe.tribe_api.common.util.service.GeminiApiClient
import com.tribe.tribe_api.common.util.service.CloudinaryUploadService
import com.tribe.tribe_api.common.util.service.TripSecurityService
import com.tribe.tribe_api.expense.dto.ExpenseDto
import com.tribe.tribe_api.expense.entity.Expense
import com.tribe.tribe_api.expense.entity.ExpenseAssignment
import com.tribe.tribe_api.expense.entity.ExpenseItem
import com.tribe.tribe_api.expense.enumeration.InputMethod
import com.tribe.tribe_api.expense.repository.ExpenseAssignmentRepository
import com.tribe.tribe_api.expense.repository.ExpenseRepository
import com.tribe.tribe_api.itinerary.repository.ItineraryItemRepository
import com.tribe.tribe_api.trip.entity.TripMember
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
    private val cloudinaryUploadService: CloudinaryUploadService,
    private val objectMapper: ObjectMapper,
    private val tripSecurityService: TripSecurityService
) {

    private fun findExpenseById(expenseId: Long): Expense {
        return expenseRepository.findById(expenseId)
            .orElseThrow { BusinessException(ErrorCode.EXPENSE_NOT_FOUND) }
    }

    //특정 일정에 대한 새로운 비용(지출) 내역을 등록
    @Transactional
    fun createExpense(
        tripId: Long,
        itineraryItemId: Long,
        request: ExpenseDto.CreateRequest,
        imageFile: MultipartFile?
    ): ExpenseDto.CreateResponse {

        tripSecurityService.isTripMember(tripId)

        val trip = tripRepository.findById(tripId)
            .orElseThrow { BusinessException(ErrorCode.TRIP_NOT_FOUND) }

        val payer = tripMemberRepository.findById(request.payerId)
            .orElseThrow { BusinessException(ErrorCode.MEMBER_NOT_FOUND) }
        if (payer.trip.id != tripId) {
            throw BusinessException(ErrorCode.NO_AUTHORITY_TRIP)
        }

        val itineraryItem = itineraryItemRepository.findById(itineraryItemId)
            .orElseThrow { BusinessException(ErrorCode.ITINERARY_ITEM_NOT_FOUND) }
        if (itineraryItem.category.trip.id != tripId) {
            throw BusinessException(ErrorCode.NO_AUTHORITY_TRIP)
        }

        val dayNumber = itineraryItem.category.day
        val paymentDate = trip.startDate.plusDays(dayNumber.toLong() - 1) //날짜 확인

        val processedData = when (request.inputMethod.uppercase()){
            "SCAN" -> {
                val file = imageFile ?: throw BusinessException(ErrorCode.INVALID_INPUT_VALUE)
                processReceipt(file)
            }
            "HANDWRITE" -> {
                val totalAmount = request.totalAmount
                    ?: throw BusinessException(ErrorCode.INVALID_INPUT_VALUE) //수기 입력시 필수 입력
                ExpenseDto.OcrResponse(
                    totalAmount = totalAmount,
                    items = request.items.map { ExpenseDto.OcrItem(it.itemName, it.price) }
                )
            }
            else -> throw BusinessException(ErrorCode.INVALID_INPUT_VALUE)
        }

        var imageUrl: String? = null
        if (imageFile != null && !imageFile.isEmpty) {
            imageUrl = cloudinaryUploadService.upload(imageFile)
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
            paymentDate = paymentDate,
            receiptImageUrl = imageUrl
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
    fun getExpenseDetail(expenseId: Long): ExpenseDto.DetailResponse {
        tripSecurityService.isTripMemberByExpenseId(expenseId)
        val expense = findExpenseById(expenseId)
        return ExpenseDto.DetailResponse.from(expense)
    }

    //특정 비용 수정
    @Transactional
    fun updateExpense(expenseId: Long, request: ExpenseDto.UpdateRequest): ExpenseDto.DetailResponse {
        tripSecurityService.isTripMemberByExpenseId(expenseId)
        val expense = findExpenseById(expenseId)
        val tripId = expense.trip.id!!

        val payer = tripMemberRepository.findById(request.payerId)
            .orElseThrow { BusinessException(ErrorCode.MEMBER_NOT_FOUND) }
        if (payer.trip.id != tripId) {
            throw BusinessException(ErrorCode.NO_AUTHORITY_TRIP)
        }

        // 요청된 아이템들의 가격 합계를 계산합니다.
        val itemsTotal = request.items.fold(BigDecimal.ZERO) { acc, item -> acc + item.price }

        // 요청된 totalAmount와 아이템 합계가 일치하는지 검증
        if (request.totalAmount.compareTo(itemsTotal) != 0) {
            throw BusinessException(ErrorCode.EXPENSE_TOTAL_AMOUNT_MISMATCH)
        }

        expense.title = request.expenseTitle
        expense.totalAmount = request.totalAmount
        expense.payer = payer

        updateExpenseItems(expense, request.items)

        return ExpenseDto.DetailResponse.from(expense)
    }

    // Item 리스트를 요청 DTO의 상태와 동일하게 업데이트
    private fun updateExpenseItems(expense: Expense, itemUpdateRequests: List<ExpenseDto.ItemUpdate>) {
        // 기존 항목들을 ID를 키로 하는 맵으로 변환
        val existingItemsMap = expense.expenseItems.associateBy { it.id }
        val requestItemsIds = itemUpdateRequests.mapNotNull { it.itemId }.toSet()

        //기존 항목 중 요청에 포함되지 않는 항목 검색
        val itemsToRemove = existingItemsMap.filterKeys { it !in requestItemsIds } .values
        expense.expenseItems.removeAll(itemsToRemove.toList())

        //item Id가 null이라면 새 항목으로 간주하고 추가, 수정
        itemUpdateRequests.forEach { request ->
            if (request.itemId == null){
                val newItem = ExpenseItem(
                    expense = expense,
                    name = request.itemName,
                    price = request.price
                )
                expense.addExpenseItem(newItem)
            } else {
                val existingItem = existingItemsMap[request.itemId]
                    ?: throw BusinessException(ErrorCode.EXPENSE_ITEM_NOT_FOUND)

                //가격 변경시 기존 배정 내역이 있는 경우
                val priceChanged = existingItem.price.compareTo(request.price) != 0
                if (priceChanged && existingItem.assignments.isNotEmpty()) {
                    // 기존 참여자 목록을 가져옴
                    val participants = existingItem.assignments.map { it.tripMember }
                    // 새로운 가격으로 N빵 재계산
                    val newAmounts = calculateFairShare(request.price, participants)
                    // 기존 배정 내역에 새 금액을 덮어씀
                    existingItem.assignments.zip(newAmounts).forEach { (assignment, newAmount) ->
                        assignment.amount = newAmount
                    }
                }

                existingItem.name = request.itemName
                existingItem.price = request.price
            }
        }
    }

    // 멤버별 배분 정보 등록/수정
    @Transactional
    fun assignParticipants(expenseId: Long, request: ExpenseDto.ParticipantAssignRequest): ExpenseDto.DetailResponse {
        tripSecurityService.isTripMemberByExpenseId(expenseId)
        val expense = findExpenseById(expenseId)
        val tripId = expense.trip.id!!

        val expenseItemsById = expense.expenseItems.associateBy { it.id }

        request.items.forEach { itemAssignmentDto ->
            val itemId = itemAssignmentDto.itemId
            val expenseItem = expenseItemsById[itemId]
                ?: throw BusinessException(ErrorCode.EXPENSE_ITEM_NOT_IN_EXPENSE)

            // 기존 분배 내역 삭제
            expenseAssignmentRepository.deleteByExpenseItemId(itemId)
            expenseItem.assignments.clear()

            // 참여자 정보 확인
            val participants = tripMemberRepository.findAllById(itemAssignmentDto.participantIds)
            if (participants.size != itemAssignmentDto.participantIds.size) {
                throw BusinessException(ErrorCode.MEMBER_NOT_FOUND)
            }

            participants.forEach { participant ->
                if (participant.trip.id != tripId) {
                    throw BusinessException(ErrorCode.NO_AUTHORITY_TRIP)
                }
            }

            // N빵 계산
            if (participants.isNotEmpty()) {
                val amounts = calculateFairShare(expenseItem.price, participants)

                // 계산된 금액으로 ExpenseAssignment 생성
                participants.zip(amounts).forEach { (participant, amount) ->
                    val newAssignment = ExpenseAssignment(
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

    // 1/n 계산 로직
    private fun calculateFairShare(totalAmount: BigDecimal, participants: List<TripMember>): List<BigDecimal> {
        val participantCount = participants.size.toBigDecimal()
        if (participantCount == BigDecimal.ZERO) {
            return emptyList()
        }

        val baseAmount = totalAmount.divide(participantCount, 0, RoundingMode.DOWN)
        val remainder = totalAmount.subtract(baseAmount.multiply(participantCount))

        val amounts = MutableList(participants.size) { baseAmount }
        if (amounts.isNotEmpty()) {
            amounts[0] = amounts[0] + remainder
        }
        return amounts
    }

    // 지출 내역 삭제
    @Transactional
    fun deleteExpense(expenseId: Long) {
        tripSecurityService.isTripMemberByExpenseId(expenseId)
        val expense = findExpenseById(expenseId)
        expenseRepository.delete(expense)
    }

    // 특정 지출 항목의 배분 내역 삭제
    @Transactional
    fun clearExpenseAssignments(expenseId: Long, request: ExpenseDto.AssignmentClearRequest): ExpenseDto.DetailResponse {
        tripSecurityService.isTripMemberByExpenseId(expenseId)
        val expense = findExpenseById(expenseId)

        val expenseItemsById = expense.expenseItems.associateBy { it.id }

        request.itemIds.forEach { itemId ->
            val expenseItem = expenseItemsById[itemId]
                ?: throw BusinessException(ErrorCode.EXPENSE_ITEM_NOT_IN_EXPENSE)

            // 데이터베이스에서 배분 내역 삭제
            expenseAssignmentRepository.deleteByExpenseItemId(itemId)
            // 영속성 컨텍스트의 1차 캐시와 DB의 동기화를 위해 collection도 clear
            expenseItem.assignments.clear()
        }

        return ExpenseDto.DetailResponse.from(expense)
    }
}