package com.tribe.tribe_api.trip.controller

import com.tribe.tribe_api.common.util.ApiResponse
import com.tribe.tribe_api.trip.dto.TripMemberDto
import com.tribe.tribe_api.trip.service.TripMemberService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/trip-members")
class TripMemberController(
    private val tripMemberService: TripMemberService
){
    // 게스트 추가
    @PostMapping("/guest")
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

    // 게스트 삭제
    @DeleteMapping("/guest")
    fun deleteGuest(
        @Valid @RequestBody request: TripMemberDto.DeleteGuestRequest
    ): ResponseEntity<ApiResponse<Any>> {
        tripMemberService.deleteGuest(request.tripId, request.guestTripMemberId)
        return ResponseEntity
            .ok(ApiResponse.success("게스트 삭제 성공"))
    }

    // OWNER가 특정 MEMBER 강퇴
    @DeleteMapping("/kick")
    fun kickMember(
        @Valid @RequestBody request: TripMemberDto.KickMemberRequest
    ): ResponseEntity<ApiResponse<Any>> {
        tripMemberService.kickMember(request.tripId, request.targetMemberId)
        return ResponseEntity
            .ok(ApiResponse.success("멤버 강퇴 성공"))
    }

    // 여행 탈퇴
    @DeleteMapping("/leave")
    fun leaveTrip(
        @Valid @RequestBody request: TripMemberDto.LeaveTripRequest
    ): ResponseEntity<ApiResponse<Any>> {
        tripMemberService.leaveTrip(request.tripId, request.memberId)
        return ResponseEntity
            .ok(ApiResponse.success("여행 탈퇴 성공"))
    }
}