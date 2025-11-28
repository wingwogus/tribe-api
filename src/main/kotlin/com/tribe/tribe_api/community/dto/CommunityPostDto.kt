package com.tribe.tribe_api.community.dto

import com.tribe.tribe_api.community.entity.CommunityPost
import com.tribe.tribe_api.community.entity.CommunityPostDay
import com.tribe.tribe_api.itinerary.dto.ItineraryResponse
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime


object CommunityPostDto {

    // --- 요청(Request) DTOs ---

    data class CreateRequest(
        @field:NotNull(message = "공유할 여행 ID는 필수입니다.")
        val tripId: Long,
        @field:NotBlank(message = "게시글 제목은 필수입니다.")
        val title: String,
        @field:NotBlank(message = "게시글 소개 내용은 필수입니다.")
        val content: String, // 게시글 전체 소개글
        val representativeImageUrl: String?, //대표 사진
        val days: List<DayContentCreateRequest> = emptyList()
    )

    data class DayContentCreateRequest(
        val categoryId: Long,
        val content: String,
        val itineraries: List<ItineraryContentCreateRequest> = emptyList()
    )

    data class ItineraryContentCreateRequest(
        val itineraryItemId: Long,
        val content: String,
        val imageUrls: List<String> = emptyList() //일정별 사진
    )

    // --- 수정 요청 DTOs ---
    data class UpdateRequest(
        @field:NotBlank(message = "게시글 제목은 필수입니다.")
        val title: String,
        @field:NotBlank(message = "게시글 내용은 필수입니다.")
        val content: String,
        val representativeImageUrl: String?,
        val days: List<DayUpdateRequest> = emptyList(),
        val dayIdsToDelete: List<Long> = emptyList()
    )

    data class DayUpdateRequest(
        val id: Long?, // 기존 Day의 ID (새로 추가 시 null)
        @field:NotNull
        val day: Int,
        val content: String?,
        val itineraries: List<ItineraryUpdateRequest> = emptyList(),
        val itineraryIdsToDelete: List<Long> = emptyList()
    )

    data class ItineraryUpdateRequest(
        val id: Long?, // 기존 Itinerary의 ID (새로 추가 시 null)
        val placeId: Long?, // 새로운 Itinerary를 추가할 때만 필요
        @field:NotNull
        val order: Int,
        @field:NotBlank
        val content: String,
        val photos: List<PhotoUpdateRequest> = emptyList(),
        val photoIdsToDelete: List<Long> = emptyList()
    )

    data class PhotoUpdateRequest(
        val id: Long?, // 기존 Photo의 ID (새로 추가 시 null)
        val imageUrl: String
    )

    // --- 응답(Response) DTOs ---

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

    data class DetailResponse(
        val postId: Long,
        val title: String,
        val content: String, // 게시글 전체 소개글
        val authorId: Long,
        val authorNickname: String,
        val country: String,
        val representativeImageUrl: String?,
        val createdAt: LocalDateTime,
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
                    createdAt = post.createdAt,
                    days = post.days.map { DayDetailResponse.from(it) }
                )
            }
        }
    }

    data class DayDetailResponse(
        val dayId: Long,
        val day: Int,
        val content: String, // Day 요약 설명
        val itineraries: List<ItineraryDetailResponse>
    ) {
        companion object {
            fun from(postDay: CommunityPostDay): DayDetailResponse {
                return DayDetailResponse(
                    dayId = postDay.id!!,
                    day = postDay.day,
                    content = postDay.content,
                    itineraries = postDay.itineraries.map { ItineraryDetailResponse.from(it) }
                )
            }
        }
    }

    data class ItineraryDetailResponse(
        val itineraryId: Long,
        val order: Int,
        val memo: String?,
        val content: String,
        val place: ItineraryResponse.ItineraryDetail.LocationInfo?, // 지도 마커 정보
        val photos: List<ItineraryPhotoDetailResponse>
    ) {
        companion object {
            fun from(postItinerary: com.tribe.tribe_api.community.entity.CommunityPostItinerary): ItineraryDetailResponse {
                return ItineraryDetailResponse(
                    itineraryId = postItinerary.id!!,
                    order = postItinerary.order,
                    memo = postItinerary.memo,
                    content = postItinerary.content,
                    place = postItinerary.place?.let { ItineraryResponse.ItineraryDetail.LocationInfo.from(it) },
                    photos = postItinerary.photos.map { ItineraryPhotoDetailResponse.from(it) }
                )
            }
        }
    }

    data class ItineraryPhotoDetailResponse(
        val photoId: Long,
        val imageUrl: String
    ) {
        companion object {
            fun from(photo: com.tribe.tribe_api.community.entity.CommunityPostItineraryPhoto): ItineraryPhotoDetailResponse {
                return ItineraryPhotoDetailResponse(
                    photoId = photo.id!!,
                    imageUrl = photo.imageUrl
                )
            }
        }
    }
}