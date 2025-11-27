package com.tribe.tribe_api.community.service

import com.tribe.tribe_api.common.exception.BusinessException
import com.tribe.tribe_api.common.exception.ErrorCode
import com.tribe.tribe_api.common.util.security.SecurityUtil
import com.tribe.tribe_api.common.util.service.CloudinaryUploadService
import com.tribe.tribe_api.community.dto.CommunityPostDto
import com.tribe.tribe_api.community.dto.PostSearchCondition
import com.tribe.tribe_api.community.entity.CommunityPost
import com.tribe.tribe_api.community.entity.CommunityPostDay
import com.tribe.tribe_api.community.entity.CommunityPostItinerary
import com.tribe.tribe_api.community.entity.CommunityPostItineraryPhoto
import com.tribe.tribe_api.community.repository.CommunityPostRepository
import com.tribe.tribe_api.community.repository.CommunityPostRepositoryCustom
import com.tribe.tribe_api.itinerary.repository.PlaceRepository
import com.tribe.tribe_api.member.repository.MemberRepository
import com.tribe.tribe_api.trip.entity.Country
import com.tribe.tribe_api.trip.repository.TripRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class CommunityPostService(
    private val communityPostRepository: CommunityPostRepository,
    private val memberRepository: MemberRepository,
    private val tripRepository: TripRepository,
    private val communityPostRepositoryCustom: CommunityPostRepositoryCustom,
    private val cloudinaryUploadService: CloudinaryUploadService,
    private val placeRepository: PlaceRepository
) {

    @PreAuthorize("@tripSecurityService.isTripAdmin(#request.tripId)")
    fun createPost(request: CommunityPostDto.CreateRequest): CommunityPostDto.DetailResponse {
        val memberId = SecurityUtil.getCurrentMemberId()
        val author = memberRepository.findById(memberId)
            .orElseThrow { BusinessException(ErrorCode.MEMBER_NOT_FOUND) }

        val trip = tripRepository.findTripWithFullItineraryById(request.tripId)
            ?: throw BusinessException(ErrorCode.TRIP_NOT_FOUND)

        val newPost = CommunityPost(
            author = author,
            trip = trip,
            title = request.title,
            content = request.content,
            representativeImageUrl = request.representativeImageUrl
        )

        trip.categories.sortedBy { it.day }.forEach { category ->
            val postDay = CommunityPostDay(newPost, category.day, "")
            newPost.days.add(postDay)

            category.itineraryItems.sortedBy { it.order }.forEach { itineraryItem ->
                val postItinerary = CommunityPostItinerary(
                    postDay,
                    itineraryItem.place,
                    itineraryItem.order,
                    itineraryItem.memo ?: ""
                )
                postDay.itineraries.add(postItinerary)
            }
        }

        val savedPost = communityPostRepository.save(newPost)
        return CommunityPostDto.DetailResponse.from(savedPost)
    }

    @Transactional(readOnly = true)
    fun getPosts(country: Country?, pageable: Pageable): Page<CommunityPostDto.SimpleResponse> {
        val posts = if (country != null) {
            communityPostRepository.findByTripCountry(country, pageable)
        } else {
            communityPostRepository.findAll(pageable)
        }
        return posts.map { CommunityPostDto.SimpleResponse.from(it) }
    }

    @Transactional(readOnly = true)
    fun getPostDetail(postId: Long): CommunityPostDto.DetailResponse {
        val post = communityPostRepository.findByIdWithDetails(postId)
            ?: throw BusinessException(ErrorCode.POST_NOT_FOUND)
        return CommunityPostDto.DetailResponse.from(post)
    }

    @PreAuthorize("@tripSecurityService.isTripAdminByPostId(#postId)")
    fun updatePost(postId: Long, request: CommunityPostDto.UpdateRequest): CommunityPostDto.DetailResponse {
        val post = communityPostRepository.findByIdWithDetails(postId)
            ?: throw BusinessException(ErrorCode.POST_NOT_FOUND)

        // 1. 대표 이미지 변경 감지 및 기존 파일 삭제 처리
        if (post.representativeImageUrl != request.representativeImageUrl) {
            post.representativeImageUrl?.let { cloudinaryUploadService.delete(it) }
        }

        // 2. 게시글 기본 정보 업데이트
        post.title = request.title
        post.content = request.content
        post.representativeImageUrl = request.representativeImageUrl

        // 삭제할 이미지 URL들을 담을 그릇 (나중에 한 번에 지움)
        val imageUrlsToDelete = mutableSetOf<String>()

        // 3. Day 순회 (구조 변경 없이 내용만 수정)
        // DB에 있는 Day들을 ID로 빠르게 찾기 위해 Map으로 변환
        val existingDays = post.days.associateBy { it.id }

        request.days.forEach { dayUpdate ->
            // Request로 들어온 ID에 해당하는 Day가 없으면 건너뜀 (혹은 에러 처리)
            val dayEntity = existingDays[dayUpdate.id] ?: return@forEach

            // Day 내용(Content) 수정
            dayEntity.content = dayUpdate.content ?: ""

            // 4. Itinerary 순회 (구조 변경 없이 내용만 수정)
            val existingItineraries = dayEntity.itineraries.associateBy { it.id }

            dayUpdate.itineraries.forEach { itineraryUpdate ->
                val itineraryEntity = existingItineraries[itineraryUpdate.id] ?: return@forEach

                // Itinerary 내용(Content) 수정
                itineraryEntity.content = itineraryUpdate.content

                // 5. 사진(Photo) 처리 - 사진은 '컨텐츠'이므로 추가/삭제 가능해야 함
                val existingPhotos = itineraryEntity.photos.associateBy { it.id }.toMutableMap()

                // 5-1. 사진 삭제
                itineraryUpdate.photoIdsToDelete.forEach { photoId ->
                    existingPhotos.remove(photoId)?.also { photoToDelete ->
                        imageUrlsToDelete.add(photoToDelete.imageUrl) // Cloudinary 삭제 대기열 추가
                        itineraryEntity.photos.remove(photoToDelete)  // DB 관계 끊기 (orphanRemoval 동작)
                    }
                }

                // 5-2. 사진 추가 (새로 업로드된 사진)
                itineraryUpdate.photos.forEach { photoUpdate ->
                    if (photoUpdate.id == null) {
                        itineraryEntity.photos.add(
                            CommunityPostItineraryPhoto(itineraryEntity, photoUpdate.imageUrl)
                        )
                    }
                }
            }
        }

        // 6. 변경된 포스트 저장 및 실제 이미지 파일 삭제
        val savedPost = communityPostRepository.save(post)

        // 트랜잭션이 성공적으로 거의 끝났을 때 외부 스토리지 파일 삭제
        imageUrlsToDelete.forEach { cloudinaryUploadService.delete(it) }

        return CommunityPostDto.DetailResponse.from(savedPost)
    }

    @PreAuthorize("@tripSecurityService.isTripAdminByPostId(#postId)")
    fun deletePost(postId: Long) {
        val post = communityPostRepository.findByIdWithDetails(postId)
            ?: throw BusinessException(ErrorCode.POST_NOT_FOUND)

        val imageUrlsToDelete = mutableListOf<String>()
        post.representativeImageUrl?.let { imageUrlsToDelete.add(it) }
        post.days.forEach { day ->
            day.itineraries.forEach { itinerary ->
                itinerary.photos.forEach { photo -> imageUrlsToDelete.add(photo.imageUrl) }
            }
        }

        communityPostRepository.delete(post)
        imageUrlsToDelete.forEach { cloudinaryUploadService.delete(it) }
    }

    @Transactional(readOnly = true)
    fun getPostsByMemberId(memberId: Long, pageable: Pageable): Page<CommunityPostDto.SimpleResponse> {
        return communityPostRepository.findByAuthorMemberIdWithDetails(memberId, pageable)
            .map { CommunityPostDto.SimpleResponse.from(it) }
    }

    @Transactional(readOnly = true)
    fun searchPosts(condition: PostSearchCondition, pageable: Pageable): Page<CommunityPostDto.SimpleResponse> {
        return communityPostRepositoryCustom.searchPost(condition, pageable)
            .map { CommunityPostDto.SimpleResponse.from(it) }
    }
}


