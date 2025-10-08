package com.tribe.tribe_api.expense.controller

import com.tribe.tribe_api.common.util.ApiResponse
import com.tribe.tribe_api.expense.dto.ExpenseDto
import com.tribe.tribe_api.expense.service.ExpenseService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1")
class ExpenseController(
    private val expenseService: ExpenseService
) {
    //특정 일정에 대한 새로운 비용(지출) 내역을 등록
    @PostMapping("/trips/{tripId}/itineraries/{itineraryItemId}/expenses")
    fun createExpense(
        @PathVariable tripId: Long,
        @PathVariable itineraryItemId: Long,
        @Valid @RequestBody request: ExpenseDto.CreateRequest
    ): ResponseEntity<ApiResponse<ExpenseDto.CreateResponse>> {

        val response = expenseService.createExpense(tripId, itineraryItemId, request)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success("지출 내역 생성 성공", response))
    }

    //특정 비용 상세 조회
    @GetMapping("/expenses/{expenseId}")
    fun getExpenseDetail(
        @PathVariable expenseId: Long
    ): ResponseEntity<ApiResponse<ExpenseDto.DetailResponse>> {

        val response = expenseService.getExpenseDetail(expenseId)
        return ResponseEntity.ok(ApiResponse.success("지출 상세 조회 성공", response))
    }

    //특정 비용 수정
    @PatchMapping("/expenses/{expenseId}")
    fun updateExpense(
        @PathVariable expenseId: Long,
        @Valid @RequestBody request: ExpenseDto.UpdateRequest
    ): ResponseEntity<ApiResponse<ExpenseDto.DetailResponse>> {

        val response = expenseService.updateExpense(expenseId, request)
        return ResponseEntity.ok(ApiResponse.success("지출 내역 수정 성공", response))
    }

    //멤버별 배분 정보 등록 및 수정
    @PostMapping("/expenses/{expenseId}/assignments")
    fun assignParticipants(
        @PathVariable expenseId: Long,
        @Valid @RequestBody request: ExpenseDto.ParticipantAssignRequest
    ): ResponseEntity<ApiResponse<ExpenseDto.DetailResponse>> {

        val response = expenseService.assignParticipants(expenseId, request)
        return ResponseEntity.ok(ApiResponse.success("비용 배분 성공", response))
    }
}