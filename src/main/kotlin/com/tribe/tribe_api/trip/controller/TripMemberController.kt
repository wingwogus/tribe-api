package com.tribe.tribe_api.trip.controller

import com.tribe.tribe_api.common.util.ApiResponse
import com.tribe.tribe_api.trip.dto.TripMemberDto
import com.tribe.tribe_api.trip.service.TripMemberService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/trips/{tripId}/members")
class TripMemberController(
    private val tripMemberService: TripMemberService
){
    // 게스트 추가
    @PostMapping("/guest")
    fun addGuest(
        @PathVariable("tripId") tripId: Long,
        @Valid @RequestBody request: TripMemberDto.AddGuestRequest
    ): ResponseEntity<ApiResponse<TripMemberDto.Simple>> {
        // DTO에 포함된 tripId를 서비스 메서드로 전달
        val response = tripMemberService.addGuest(tripId, request)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success("게스트 추가 성공", response))
    }

    // 게스트 삭제
    @DeleteMapping("/guest/{guestId}")
    fun deleteGuest(
        @PathVariable("tripId") tripId: Long,
        @PathVariable("guestId") guestId: Long
    ): ResponseEntity<ApiResponse<Any>> {
        tripMemberService.deleteGuest(tripId, guestId)
        return ResponseEntity
            .ok(ApiResponse.success("게스트 삭제 성공"))
    }

    // 멤버 권한 수정
    @PatchMapping("{memberId}/role")
    fun assignRole(
        @PathVariable("tripId") tripId: Long,
        @PathVariable("memberId") memberId: Long,
        @Valid @RequestBody request: TripMemberDto.AssignRoleRequest
    ) : ResponseEntity<ApiResponse<TripMemberDto.Simple>>{
        val response = tripMemberService.assignRole(tripId, memberId, request)
        return ResponseEntity.ok(ApiResponse.success("멤버 권한 수정 성공", response))
    }

    // OWNER가 특정 MEMBER 강퇴
    @DeleteMapping("/{memberId}")
    fun kickMember(
        @PathVariable("tripId") tripId: Long,
        @PathVariable("memberId") memberId: Long,
    ): ResponseEntity<ApiResponse<Any>> {
        tripMemberService.kickMember(tripId, memberId)
        return ResponseEntity
            .ok(ApiResponse.success("멤버 강퇴 성공"))
    }

    // 여행 탈퇴
    @DeleteMapping("/me")
    fun leaveTrip(
        @PathVariable("tripId") tripId: Long
    ): ResponseEntity<ApiResponse<Any>> {
        tripMemberService.leaveTrip(tripId)
        return ResponseEntity
            .ok(ApiResponse.success("여행 탈퇴 성공"))
    }
}