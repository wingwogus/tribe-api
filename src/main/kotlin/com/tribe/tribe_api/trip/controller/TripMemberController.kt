package com.tribe.tribe_api.trip.controller

import com.tribe.tribe_api.common.util.ApiResponse
import com.tribe.tribe_api.trip.dto.TripMemberDto
import com.tribe.tribe_api.trip.service.TripMemberService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/trip-members")
class TripMemberController(
    private val tripMemberService: TripMemberService
) {
    // 게스트 추가
    @PostMapping("/expenses/guest")
    fun addGuest(
        @Valid @RequestBody request: TripMemberDto.AddGuestRequest
    ): ResponseEntity<ApiResponse<TripMemberDto.Simple>> {
        // DTO에 포함된 tripId를 서비스 메서드로 전달
        val response = tripMemberService.addGuest(request.tripId, request)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success("게스트 추가 성공", response))
    }

    // 멤버 권한 수정
    @PatchMapping("/role")
    fun assignRole(
        @Valid @RequestBody request: TripMemberDto.AssignRoleRequest
    ) : ResponseEntity<ApiResponse<TripMemberDto.Simple>>{
        val response = tripMemberService.assignRole(request.tripId, request)
        return ResponseEntity.ok(ApiResponse.success("멤버 권한 수정 성공", response))
    }
}