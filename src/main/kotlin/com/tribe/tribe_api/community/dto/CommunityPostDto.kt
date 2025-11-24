package com.tribe.tribe_api.community.dto

import com.tribe.tribe_api.community.entity.CommunityPost
import com.tribe.tribe_api.community.entity.CommunityPostDay
import com.tribe.tribe_api.community.entity.CommunityPostDayPhoto
import com.tribe.tribe_api.itinerary.dto.ItineraryResponse
import com.tribe.tribe_api.itinerary.entity.Category
import com.tribe.tribe_api.itinerary.entity.ItineraryItem
import com.tribe.tribe_api.trip.entity.Trip
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime


object CommunityPostDto {

    data class CreateRequest(
        @field:NotNull(message = "공유할 여행 ID는 필수입니다.")
        val tripId: Long,
        @field:NotBlank(message = "게시글 제목은 필수입니다.")
        val title: String,
        @field:NotBlank(message = "게시글 소개 내용은 필수입니다.")
        val content: String, // 소개글
        val representativeImageUrl: String?, // 대표 이미지 URL
        val days: List<DayCreateRequest> = emptyList() // Day별 컨텐츠 목록
    )

    data class DayCreateRequest(
        @field:NotNull
        val day: Int,
        @field:NotBlank
        val content: String,
        val photoUrls: List<String> = emptyList() // 해당 Day의 이미지 URL 목록
    )

    data class UpdateRequest(
        @field:NotBlank(message = "게시글 제목은 필수입니다.")
        val title: String,
        @field:NotBlank(message = "게시글 내용은 필수입니다.")
        val content: String,
        val representativeImageUrl: String?,
        val days: List<DayCreateRequest> = emptyList()
    )

    data class SimpleResponse(
        val postId: Long,
        val title: String,
        val authorId: Long,
        val authorNickname: String,
        val country: String,
        val representativeImageUrl: String?
    ) {
        companion object {
            fun from(post: CommunityPost): SimpleResponse {
                return SimpleResponse(
                    postId = post.id!!,
                    title = post.title,
                    authorId = post.author.id!!,
                    authorNickname = post.author.nickname,
                    country = post.trip.country.koreanName,
                    representativeImageUrl = post.representativeImageUrl
                )
            }
        }
    }

    /**
     * 게시글 상세 조회 시 사용되는 응답
     */
    data class DetailResponse(
        val postId: Long,
        val title: String,
        val content: String, // 소개글
        val authorId: Long,
        val authorNickname: String,
        val country: String,
        val representativeImageUrl: String?,
        val createdAt: LocalDateTime,
        val tripMapData: SharedTripMapData,
        val days: List<DayDetailResponse>
    ) {
        companion object {
            fun from(post: CommunityPost): DetailResponse {
                return DetailResponse(
                    postId = post.id!!,
                    title = post.title,
                    content = post.content,
                    authorId = post.author.id!!,
                    authorNickname = post.author.nickname,
                    country = post.trip.country.koreanName,
                    representativeImageUrl = post.representativeImageUrl,
                    createdAt = post.createdAt!!,
                    tripMapData = SharedTripMapData.from(post.trip),
                    days = post.days?.sortedBy { it.day }?.map { DayDetailResponse.from(it) } ?: emptyList()
                )
            }
        }
    }

    data class DayDetailResponse(
        val dayId: Long,
        val day: Int,
        val content: String,
        val photos: List<PhotoDetailResponse>
    ) {
        companion object {
            fun from(postDay: CommunityPostDay): DayDetailResponse {
                return DayDetailResponse(
                    dayId = postDay.id!!,
                    day = postDay.day,
                    content = postDay.content,
                    photos = postDay.photos?.map { PhotoDetailResponse.from(it) } ?: emptyList()
                )
            }
        }
    }

    data class PhotoDetailResponse(
        val photoId: Long,
        val imageUrl: String
    ) {
        companion object {
            fun from(photo: CommunityPostDayPhoto): PhotoDetailResponse {
                return PhotoDetailResponse(
                    photoId = photo.id!!,
                    imageUrl = photo.imageUrl
                )
            }
        }
    }
}

data class SharedTripMapData(
    val tripId: Long,
    val title: String,
    // [제거!] country 필드는 DetailResponse 최상위로 올렸습니다.
    val categories: List<SharedCategoryMapData>
) {
    companion object {
        fun from(trip: Trip): SharedTripMapData {
            return SharedTripMapData(
                tripId = trip.id!!,
                title = trip.title,
                categories = trip.categories
                    .sortedBy { it.day }
                    .map { SharedCategoryMapData.from(it) }
            )
        }
    }
}

data class SharedCategoryMapData(
    val day: Int,
    val itineraries: List<SharedItineraryMapData>
) {
    companion object {
        fun from(category: Category): SharedCategoryMapData {
            return SharedCategoryMapData(
                day = category.day,
                itineraries = category.itineraryItems
                    .sortedBy { it.order }
                    .map { SharedItineraryMapData.from(it) }
            )
        }
    }
}

data class SharedItineraryMapData(
    val name: String,
    val location: ItineraryResponse.ItineraryDetail.LocationInfo? // 지도에 마커를 찍기 위한 핵심 정보
) {
    companion object {
        fun from(item: ItineraryItem): SharedItineraryMapData {
            return SharedItineraryMapData(
                name = item.place?.name ?: item.title!!,
                location = item.place?.let { ItineraryResponse.ItineraryDetail.LocationInfo.from(it) }
            )
        }
    }
}