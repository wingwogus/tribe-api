package com.tribe.tribe_api.itinerary.controller

import com.tribe.tribe_api.common.util.ApiResponse
import com.tribe.tribe_api.common.util.security.CustomUserDetails
import com.tribe.tribe_api.itinerary.dto.WishlistDto
import com.tribe.tribe_api.itinerary.service.WishlistService
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/trips/{tripId}/wishlists")
class WishlistController(
    private val wishlistService: WishlistService
) {
    /**
     * 위시리스트에 장소 추가
     */
    @PostMapping
    fun addWishlistItem(
        @PathVariable tripId: Long,
        @RequestBody request: WishlistDto.WishListAddRequest
    ): ResponseEntity<ApiResponse<WishlistDto.WishlistItemDto>> {
        val response = wishlistService.addWishList(tripId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response))
    }

    /**
     * 위시리스트 목록 조회 (전체 또는 검색어 필터링, 페이지네이션)
     */
    @GetMapping
    fun getWishlistItems(
        @PathVariable tripId: Long,
        @RequestParam(required = false) query: String?,
        pageable: Pageable
    ): ResponseEntity<ApiResponse<WishlistDto.WishlistSearchResponse>> {
        val response = if (query.isNullOrBlank()) {
            wishlistService.getWishList(tripId, pageable)
        } else {
            wishlistService.searchWishList(tripId, query, pageable)
        }
        return ResponseEntity.ok(ApiResponse.success(response))
    }

    /**
     * 위시리스트에서 여러 장소 선택 삭제
     */
    @DeleteMapping
    fun deleteWishlistItems(
        @PathVariable tripId: Long,
        @RequestBody request: WishlistDto.WishlistDeleteRequest
    ): ResponseEntity<ApiResponse<Unit>> {
        wishlistService.deleteWishlistItems(request.wishlistItemIds)
        return ResponseEntity.ok(ApiResponse.success("선택한 항목이 삭제되었습니다.", null))
    }
}