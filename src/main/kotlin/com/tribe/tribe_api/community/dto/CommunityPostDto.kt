package com.tribe.tribe_api.community.dto

import com.tribe.tribe_api.community.entity.CommunityPost
import com.tribe.tribe_api.itinerary.dto.ItineraryResponse.ItineraryDetail.LocationInfo
import com.tribe.tribe_api.itinerary.entity.Category
import com.tribe.tribe_api.itinerary.entity.ItineraryItem
import com.tribe.tribe_api.trip.entity.Trip
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.LocalDate
import java.time.LocalDateTime


sealed class CommunityPostDto {

    data class CreateRequest(
        @field:NotNull(message = "공유할 여행 ID는 필수입니다.")
        val tripId: Long,

        @field:NotBlank(message = "게시글 제목은 필수입니다.")
        val title: String,

        @field:NotBlank(message = "게시글 내용은 필수입니다.")
        val content: String,
    )

    data class UpdateRequest(
        @field:NotBlank(message = "게시글 제목은 필수입니다.")
        val title: String,

        @field:NotBlank(message = "게시글 내용은 필수입니다.")
        val content: String,
    )


    data class SimpleResponse(
        val postId: Long,
        val title: String,
        val authorNickname: String,
        val country: String,
        val representativeImageUrl: String?
    ) {
        companion object {
            fun from(post: CommunityPost): SimpleResponse {
                return SimpleResponse(
                    postId = post.id!!,
                    title = post.title,
                    authorNickname = post.author.nickname,
                    country = post.trip.country.koreanName,
                    representativeImageUrl = post.representativeImageUrl
                )
            }
        }
    }

    /**
     * 게시글 상세 조회 시 사용되는 응답
     * 요구사항대로 정산, 멤버 등 민감 정보가 제외된 'SharedTripDetail'을 포함
     */
    data class DetailResponse(
        val postId: Long,
        val title: String,
        val content: String,
        val authorNickname: String,
        val country: String,
        val representativeImageUrl: String?,
        val trip: SharedTripDetail // 정산, 리뷰 등이 제외된 공유용 여행 DTO
    ) {
        companion object {
            fun from(post: CommunityPost): DetailResponse {
                return DetailResponse(
                    postId = post.id!!,
                    title = post.title,
                    content = post.content,
                    authorNickname = post.author.nickname,
                    country = post.trip.country.koreanName,
                    representativeImageUrl = post.representativeImageUrl,
                    trip = SharedTripDetail.from(post.trip)
                )
            }
        }
    }
}


/**
 * 공유용 여행 상세 정보 DTO (정산, 멤버 목록, 리뷰 등 민감 정보 제외)
 */
data class SharedTripDetail(
    val tripId: Long,
    val title: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val categories: List<SharedCategory>
) {
    companion object {
        fun from(trip: Trip): SharedTripDetail {
            return SharedTripDetail(
                tripId = trip.id!!,
                title = trip.title,
                startDate = trip.startDate,
                endDate = trip.endDate,
                // [중요] 엔티티를 그대로 반환하지 않고, 공유용 DTO로 변환하여 반환
                categories = trip.categories
                    .sortedBy { it.day }
                    .map { SharedCategory.from(it) }
            )
        }
    }
}

/**
 * 공유용 카테고리 DTO
 */
data class SharedCategory(
    val categoryId: Long,
    val name: String,
    val day: Int,
    val itineraries: List<SharedItineraryItem>
) {
    companion object {
        fun from(category: Category): SharedCategory {
            return SharedCategory(
                categoryId = category.id!!,
                name = category.name,
                day = category.day,
                // 일정 또한 공유용 DTO로 변환
                itineraries = category.itineraryItems
                    .sortedBy { it.order }
                    .map { SharedItineraryItem.from(it) }
            )
        }
    }
}

/**
 * 공유용 일정 아이템 DTO (정산 정보 제외)
 */
data class SharedItineraryItem(
    val itineraryId: Long,
    val name: String,
    val time: LocalDateTime?,
    val order: Int,
    val memo: String?,
    val location: LocationInfo?
) {
    companion object {
        fun from(item: ItineraryItem): SharedItineraryItem {
            return SharedItineraryItem(
                itineraryId = item.id!!,
                // 장소/텍스트 기반 일정을 하나의 이름으로 통일
                name = item.place?.name ?: item.title!!,
                time = item.time,
                order = item.order,
                memo = item.memo,
                // 장소가 있을 때만 location 정보를 생성
                location = item.place?.let {
                    LocationInfo(
                        it.latitude.toDouble(),
                        it.longitude.toDouble(),
                        it.address
                    )
                }
            )
        }
    }
}
