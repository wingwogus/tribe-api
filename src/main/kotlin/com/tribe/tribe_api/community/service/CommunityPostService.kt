package com.tribe.tribe_api.community.service

import com.tribe.tribe_api.common.exception.BusinessException
import com.tribe.tribe_api.common.exception.ErrorCode
import com.tribe.tribe_api.common.util.security.SecurityUtil
import com.tribe.tribe_api.common.util.service.CloudinaryUploadService
import com.tribe.tribe_api.community.dto.CommunityPostDto
import com.tribe.tribe_api.community.dto.PostSearchCondition
import com.tribe.tribe_api.community.entity.*
import com.tribe.tribe_api.community.repository.CommunityPostRepository
import com.tribe.tribe_api.community.repository.CommunityPostRepositoryCustom
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


        val tripCategoriesByDay = trip.categories.groupBy { it.day }
        val categoryContentMap = request.days.associateBy { it.categoryId }
        val itineraryContentMap = request.days.flatMap { it.itineraries }.associateBy { it.itineraryItemId }

        // For each -> Day
        tripCategoriesByDay.keys.sorted().forEach { dayNum ->

            val postDay = CommunityPostDay(newPost, dayNum)
            newPost.days.add(postDay)

            // For each -> day 안에있는 category
            val categoriesForDay = tripCategoriesByDay[dayNum] ?: emptyList()
            categoriesForDay.sortedBy { it.order }.forEach { tripCategory ->

                // request 요청으로 들어온 cateory의 content찾기
                val categoryRequest = categoryContentMap[tripCategory.id]

                // 새로운 CommunityPostCategory 엔티티 생성
                val postCategory = CommunityPostCategory(
                    communityPostDay = postDay,
                    name = tripCategory.name
                )
                postDay.categories.add(postCategory)


                // For each -> category 안에 있는 itinerary
                tripCategory.itineraryItems.sortedBy { it.order }.forEach { itineraryItem ->

                    val itineraryRequest = itineraryContentMap[itineraryItem.id]
                    val newContent = itineraryRequest?.content ?: ""
                    val originalMemo = itineraryItem.memo

                    val postItinerary = CommunityPostItinerary(
                        communityPostCategory = postCategory, // 새로운 category 엔티티와 연결
                        place = itineraryItem.place,
                        order = itineraryItem.order,
                        memo = originalMemo, // 원본 메모
                        content = newContent
                    )

                    // 일정에 사진 추가
                    itineraryRequest?.imageUrls?.forEach { imageUrl ->
                        val photo = CommunityPostItineraryPhoto(postItinerary, imageUrl)
                        postItinerary.photos.add(photo)
                    }

                    postCategory.itineraries.add(postItinerary)
                }
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

        // 3. 전체 엔티티를 Map으로 변환하여 빠른 조회를 위함
        val existingDays = post.days.associateBy { it.id }
        val existingCategories = post.days.flatMap { it.categories }.associateBy { it.id }
        val existingItineraries = post.days.flatMap { it.categories }.flatMap { it.itineraries }.associateBy { it.id }

        // 4. Day 순회
        request.days.forEach { dayUpdate ->
            val dayEntity = existingDays[dayUpdate.id] ?: return@forEach

            // 5. Category 순회
            dayUpdate.categories.forEach { categoryUpdate ->
                val categoryEntity = existingCategories[categoryUpdate.id] ?: return@forEach

                // 6. Itinerary 순회
                categoryUpdate.itineraries.forEach { itineraryUpdate ->
                    val itineraryEntity = existingItineraries[itineraryUpdate.id] ?: return@forEach

                    // Itinerary 내용(Content) 수정
                    itineraryEntity.content = itineraryUpdate.content

                    // 7. 사진(Photo) 처리
                    val existingPhotos = itineraryEntity.photos.associateBy { it.id }.toMutableMap()

                    // 7-1. 사진 삭제
                    itineraryUpdate.photoIdsToDelete.forEach { photoId ->
                        existingPhotos.remove(photoId)?.also { photoToDelete ->
                            imageUrlsToDelete.add(photoToDelete.imageUrl) // Cloudinary 삭제 대기열 추가
                            itineraryEntity.photos.remove(photoToDelete)  // DB 관계 끊기 (orphanRemoval 동작)
                        }
                    }

                    // 7-2. 사진 추가 (새로 업로드된 사진)
                    itineraryUpdate.photos.forEach { photoUpdate ->
                        if (photoUpdate.id == null) {
                            itineraryEntity.photos.add(
                                CommunityPostItineraryPhoto(itineraryEntity, photoUpdate.imageUrl)
                            )
                        }
                    }
                }
            }
        }

        // 8. 변경된 포스트 저장 및 실제 이미지 파일 삭제
        val savedPost = communityPostRepository.save(post)

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
            day.categories.forEach { category ->
                category.itineraries.forEach { itinerary ->
                    itinerary.photos.forEach { photo -> imageUrlsToDelete.add(photo.imageUrl) }
                }
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


