package com.tribe.tribe_api.itinerary.dto

import com.tribe.tribe_api.itinerary.entity.WishlistItem
import com.tribe.tribe_api.trip.dto.TripMemberDto
import org.springframework.data.domain.Page
import java.math.BigDecimal

object WishlistDto {

    data class WishlistItemDto(
        val wishlistItemId: Long,
        val placeId: Long,
        val name: String,
        val address: String?,
        val latitude: BigDecimal,
        val longitude: BigDecimal,
        val adder: TripMemberDto.Details,
    ) {
        companion object {
            fun from(entity: WishlistItem): WishlistItemDto {
                val place = entity.place
                val adder = entity.adder

                return WishlistItemDto(
                    wishlistItemId = entity.id!!,
                    placeId = place.id!!,
                    name = place.name,
                    address = place.address,
                    latitude = place.latitude,
                    longitude = place.longitude,
                    adder = TripMemberDto.Details.from(adder)
                )
            }
        }
    }

    data class WishListAddRequest(
        val externalPlaceId: String,
        val placeName: String,
        val address: String?,
        val latitude: BigDecimal,
        val longitude: BigDecimal
    )

    data class WishlistSearchResponse(
        val content: List<WishlistItemDto>,
        val pageNumber: Int,
        val pageSize: Int,
        val totalPages: Int,
        val totalElements: Long,
        val isLast: Boolean
    ) {
        companion object {
            fun from(page: Page<WishlistItem>): WishlistSearchResponse {
                return WishlistSearchResponse(
                    content = page.content.map { WishlistItemDto.from(it) },
                    pageNumber = page.number,
                    pageSize = page.size,
                    totalPages = page.totalPages,
                    totalElements = page.totalElements,
                    isLast = page.isLast
                )
            }
        }
    }

    data class WishlistDeleteRequest(
        val wishlistItemIds: List<Long>
    )
}