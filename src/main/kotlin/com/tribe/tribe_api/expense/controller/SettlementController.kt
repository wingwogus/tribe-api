package com.tribe.tribe_api.expense.controller

import com.tribe.tribe_api.common.util.ApiResponse
import com.tribe.tribe_api.expense.dto.SettlementDto
import com.tribe.tribe_api.expense.service.SettlementService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/trips/{tripId}/settlements")
class SettlementController(
    private val settlementService: SettlementService
) {
    @GetMapping("/daily")
    fun getDailySettlement(
        @PathVariable tripId: Long,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate
    ): ResponseEntity<ApiResponse<SettlementDto.DailyResponse>> {
        val dailySettlement = settlementService.getDailySettlement(tripId, date)
        return ResponseEntity.ok(ApiResponse.success("일별 정산 조회 성공", dailySettlement))
    }

    @GetMapping("/total")
    fun getTotalSettlement(
        @PathVariable tripId: Long
    ): ResponseEntity<ApiResponse<SettlementDto.TotalResponse>> {
        val totalSettlement = settlementService.getTotalSettlement(tripId)
        return ResponseEntity.ok(ApiResponse.success("전체 정산 조회 성공", totalSettlement))
    }
}