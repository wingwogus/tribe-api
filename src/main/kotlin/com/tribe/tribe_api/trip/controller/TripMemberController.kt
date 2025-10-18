package com.tribe.tribe_api.trip.controller

import com.tribe.tribe_api.common.util.ApiResponse
import com.tribe.tribe_api.trip.dto.TripMemberDto
import com.tribe.tribe_api.trip.service.TripMemberService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/expenses")
class TripMemberController (
    private val tripMemberService: TripMemberService
){
    // 게스트 추가
    @PostMapping("/guest")
    fun addGuest(
        @Valid @RequestBody request: TripMemberDto.AddGuestRequest
    ): ResponseEntity<ApiResponse<TripMemberDto.Info>> {
        // DTO에 포함된 tripId를 서비스 메서드로 전달
        val response = tripMemberService.addGuest(request)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success("게스트 추가 성공", response))
    }
}