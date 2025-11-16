package com.tribe.tribe_api.trip.controller

import com.tribe.tribe_api.common.util.ApiResponse
import com.tribe.tribe_api.trip.dto.TripMemberDto
import com.tribe.tribe_api.trip.service.TripMemberService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/trips")
class TripMemberController (
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

    // 게스트 삭제
    @DeleteMapping("/members/guest")
    fun deleteGuest(
        @Valid @RequestBody request: TripMemberDto.DeleteGuestRequest
    ): ResponseEntity<ApiResponse<Any>> {
        tripMemberService.deleteGuest(request.tripId, request.guestTripMemberId)
        return ResponseEntity
            .ok(ApiResponse.success("게스트 삭제 성공"))
    }

    // OWNER가 특정 MEMBER 강퇴
    @DeleteMapping("/members/kick")
    fun kickMember(
        @Valid @RequestBody request: TripMemberDto.KickMemberRequest
    ): ResponseEntity<ApiResponse<Any>> {
        tripMemberService.kickMember(request.tripId, request.targetMemberId)
        return ResponseEntity
            .ok(ApiResponse.success("멤버 강퇴 성공"))
    }

    // 여행 탈퇴
    @DeleteMapping("/members/leave")
    fun leaveTrip(
        @Valid @RequestBody request: TripMemberDto.LeaveTripRequest
    ): ResponseEntity<ApiResponse<Any>> {
        tripMemberService.leaveTrip(request.tripId, request.memberId)
        return ResponseEntity
            .ok(ApiResponse.success("여행 탈퇴 성공"))
    }
}