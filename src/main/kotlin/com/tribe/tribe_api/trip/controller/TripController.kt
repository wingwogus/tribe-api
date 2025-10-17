package com.tribe.tribe_api.trip.controller

import com.tribe.tribe_api.common.util.ApiResponse
import com.tribe.tribe_api.trip.dto.TripRequest
import com.tribe.tribe_api.trip.dto.TripResponse
import com.tribe.tribe_api.trip.service.TripService
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/trips")
class TripController(
    private val tripService: TripService // 주 생성자를 통한 의존성 주입
) {

    /**
     * 새 여행 생성
     */
    @PostMapping
    fun createTrip(
        @Valid @RequestBody request: TripRequest.Create
    ): ResponseEntity<ApiResponse<TripResponse.TripDetail>> {
        val response = tripService.createTrip(request)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success("여행 생성 성공", response))
    }

    /**
     * 내 여행 목록 조회 (페이징)
     */
    @GetMapping
    fun getAllTrips(
        @PageableDefault(size = 10, sort = ["startDate"], direction = Sort.Direction.DESC) pageable: Pageable
    ): ResponseEntity<Any> {

        val response = tripService.getAllTrips(pageable)
        return ResponseEntity.ok(ApiResponse.success("여행 목록 조회 성공", response))
    }

    /**
     * 특정 여행 상세 조회
     */
    @GetMapping("/{tripId}")
    fun getTripDetails(
        @PathVariable tripId: Long
    ): ResponseEntity<ApiResponse<TripResponse.TripDetail>> {

        val response = tripService.getTripDetails(tripId)
        return ResponseEntity.ok(ApiResponse.success("특정 여행 조회 성공", response))
    }

    /**
     * 특정 여행 정보 수정
     */
    @PatchMapping("/{tripId}")
    fun updateTrip(
        @PathVariable tripId: Long,
        @Valid @RequestBody request: TripRequest.Update
    ): ResponseEntity<ApiResponse<TripResponse.TripDetail>> {

        val response = tripService.updateTrip(tripId, request)
        return ResponseEntity.ok(ApiResponse.success("여행 수정 성공", response))
    }

    /**
     * 특정 여행 삭제
     */
    @DeleteMapping("/{tripId}")
    fun deleteTrip(
        @PathVariable tripId: Long
    ): ResponseEntity<ApiResponse<Unit>> {

        tripService.deleteTrip(tripId)
        return ResponseEntity.ok(ApiResponse.success("여행 삭제 성공", null))
    }

    /**
     * 초대 링크 생성
     */
    @PostMapping("/{tripId}/invite")
    fun createInvitation(
        @PathVariable tripId: Long
    ): ResponseEntity<ApiResponse<TripResponse.Invitation>> {

        val response = tripService.createInvitation(tripId)
        return ResponseEntity.ok(ApiResponse.success("초대 링크 생성 성공", response))
    }

    /**
     * 초대 수락 및 여행 참여
     */
    @PostMapping("/join")
    fun joinTrip(
        @Valid @RequestBody request: TripRequest.Join
    ): ResponseEntity<ApiResponse<TripResponse.TripDetail>> {
        val response = tripService.joinTrip(request)
        return ResponseEntity.ok(ApiResponse.success("여행 참여 성공", response))
    }
}