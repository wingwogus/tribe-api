package com.tribe.tribe_api.community.service

import com.tribe.tribe_api.common.exception.BusinessException
import com.tribe.tribe_api.common.exception.ErrorCode
import com.tribe.tribe_api.common.util.security.SecurityUtil
import com.tribe.tribe_api.common.util.service.CloudinaryUploadService
import com.tribe.tribe_api.community.dto.CommunityPostDto
import com.tribe.tribe_api.community.dto.PostSearchCondition
import com.tribe.tribe_api.community.entity.CommunityPost
import com.tribe.tribe_api.community.entity.CommunityPostDay
import com.tribe.tribe_api.community.entity.CommunityPostItineraryPhoto
import com.tribe.tribe_api.community.repository.CommunityPostRepository
import com.tribe.tribe_api.community.repository.CommunityPostRepositoryCustom
import com.tribe.tribe_api.member.repository.MemberRepository
import com.tribe.tribe_api.trip.entity.Country
import com.tribe.tribe_api.trip.repository.TripMemberRepository
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
    private val tripMemberRepository: TripMemberRepository,
    private val communityPostRepositoryCustom: CommunityPostRepositoryCustom,
    private val cloudinaryUploadService: CloudinaryUploadService,
    private val placeRepository: com.tribe.tribe_api.itinerary.repository.PlaceRepository, // Place 조회를 위해 추가
) {

    /**
     * Day, Itinerary, Photo 등 상세 컨텐츠를 생성하고 부모 Post에 연결하는 공통 로직
     */
    private fun mapCommunityPostDetails(post: CommunityPost, daysRequest: List<CommunityPostDto.DayCreateRequest>) {
        daysRequest.forEach { dayRequest ->
            // 1. Day 엔티티 생성
            val newDay = CommunityPostDay(
                communityPost = post,
                day = dayRequest.day,
                content = dayRequest.content
            )

            // 2. Day별 Itinerary 엔티티 생성
            dayRequest.itineraries.forEach { itineraryRequest ->
                val place = itineraryRequest.placeId?.let {
                    placeRepository.findById(it).orElse(null)
                }
                val newItinerary = com.tribe.tribe_api.community.entity.CommunityPostItinerary(
                    communityPostDay = newDay,
                    place = place,
                    order = itineraryRequest.order,
                    content = itineraryRequest.content
                )

                // 3. Itinerary별 Photo 엔티티 생성
                itineraryRequest.photoUrls.forEach { imageUrl ->
                    val newPhoto = CommunityPostItineraryPhoto(
                        communityPostItinerary = newItinerary,
                        imageUrl = imageUrl
                    )
                    newItinerary.photos.add(newPhoto) // Itinerary에 사진 연결
                }
                newDay.itineraries.add(newItinerary) // Day에 Itinerary 연결
            }
            post.days.add(newDay) // Post에 Day 연결
        }
    }

    // 1. 게시글 생성
    @PreAuthorize("@tripSecurityService.isTripAdmin(#request.tripId)")
    fun createPost(request: CommunityPostDto.CreateRequest): CommunityPostDto.DetailResponse {
        val memberId = SecurityUtil.getCurrentMemberId()
        val author = memberRepository.findById(memberId)
            .orElseThrow { BusinessException(ErrorCode.MEMBER_NOT_FOUND) }

        val trip = tripRepository.findById(request.tripId)
            .orElseThrow { BusinessException(ErrorCode.TRIP_NOT_FOUND) }

        tripMemberRepository.findByTripAndMember(trip, author)
            ?: throw BusinessException(ErrorCode.NOT_A_TRIP_MEMBER)


        val newPost = CommunityPost(
            author = author,
            trip = trip,
            title = request.title,
            content = request.content,
            representativeImageUrl = request.representativeImageUrl,
        )

        mapCommunityPostDetails(newPost, request.days) // 새로운 매핑 함수 사용

        val savedPost = communityPostRepository.save(newPost)
        return communityPostRepository.findByIdWithDetails(savedPost.id!!)
            ?.let { CommunityPostDto.DetailResponse.from(it) }
            ?: throw BusinessException(ErrorCode.POST_NOT_FOUND)
    }

    // 2. 게시글 조회 (국가별 필터링)
    @Transactional(readOnly = true)
    fun getPosts(country: Country?, pageable: Pageable): Page<CommunityPostDto.SimpleResponse> {
        val postPage = if (country != null) {
            communityPostRepository.findByTripCountry(country, pageable)
        } else {
            communityPostRepository.findAll(pageable)
        }
        return postPage.map { CommunityPostDto.SimpleResponse.from(it) }
    }

    // 게시글 상세 조회
    @Transactional(readOnly = true)
    fun getPostDetail(postId: Long): CommunityPostDto.DetailResponse {
        val post = communityPostRepository.findByIdWithDetails(postId)
            ?: throw BusinessException(ErrorCode.POST_NOT_FOUND)

        return CommunityPostDto.DetailResponse.from(post)
    }

    // 게시글 수정
    @PreAuthorize("@tripSecurityService.isTripOwnerByPostId(#postId)")
    fun updatePost(postId: Long, request: CommunityPostDto.UpdateRequest): CommunityPostDto.DetailResponse {
        val post = communityPostRepository.findByIdWithDetails(postId)
            ?: throw BusinessException(ErrorCode.POST_NOT_FOUND)

        // Cloudinary에서 삭제할 이미지 URL 수집 (기존 로직)
        val imageUrlsToDelete = mutableListOf<String>()
        post.days.forEach { day ->
            day.itineraries.forEach { itinerary ->
                itinerary.photos.forEach { photo ->
                    imageUrlsToDelete.add(photo.imageUrl)
                }
            }
        }
        if (post.representativeImageUrl != null && post.representativeImageUrl != request.representativeImageUrl) {
            imageUrlsToDelete.add(post.representativeImageUrl!!)
        }

        // 메타데이터 업데이트
        post.title = request.title
        post.content = request.content
        post.representativeImageUrl = request.representativeImageUrl

        // 기존 Day, Itinerary, Photo 모두 삭제 (orphanRemoval=true 옵션으로 DB에서 자동 삭제됨)
        post.days.clear()

        // 새로운 내용으로 다시 채우기
        mapCommunityPostDetails(post, request.days)

        val savedPost = communityPostRepository.save(post)

        // Cloudinary 이미지 삭제 실행 (DB 트랜잭션과 분리)
        imageUrlsToDelete.forEach { cloudinaryUploadService.delete(it) }

        return communityPostRepository.findByIdWithDetails(savedPost.id!!)
            ?.let { CommunityPostDto.DetailResponse.from(it) }
            ?: throw BusinessException(ErrorCode.POST_NOT_FOUND)
    }


    // 5. 게시글 삭제
    @PreAuthorize("@tripSecurityService.isTripOwnerByPostId(#postId)")
    fun deletePost(postId: Long) {
        val post = communityPostRepository.findByIdWithDetails(postId)
            ?: throw BusinessException(ErrorCode.POST_NOT_FOUND)

        // 모든 Day, Itinerary, Photo 및 대표 이미지 URL 수집 (리팩토링됨)
        val imageUrlsToDelete = mutableListOf<String>()
        post.representativeImageUrl?.let { imageUrlsToDelete.add(it) }

        post.days.forEach { day ->
            day.itineraries.forEach { itinerary ->
                itinerary.photos.forEach { photo ->
                    imageUrlsToDelete.add(photo.imageUrl)
                }
            }
        }

        // 1. DB에서 게시글 삭제 (Days, Itineraries, Photos는 Cascade와 orphanRemoval로 자동 삭제됨)
        communityPostRepository.delete(post)

        // 2. Cloudinary에서 이미지 삭제 (DB 트랜잭션과 분리)
        imageUrlsToDelete.forEach { url ->
            cloudinaryUploadService.delete(url)
        }
    }

    // 6. 특정 MemberId가 작성한 모든 게시글 목록 조회
    @Transactional(readOnly = true)
    fun getPostsByMemberId(memberId: Long, pageable: Pageable): Page<CommunityPostDto.SimpleResponse> {
        val postPage: Page<CommunityPost> = communityPostRepository.findByAuthorMemberIdWithDetails(memberId, pageable)

        return postPage.map { CommunityPostDto.SimpleResponse.from(it) }
    }

    @Transactional(readOnly = true)
    fun searchPosts(
        condition: PostSearchCondition,
        pageable: Pageable
    ): Page<CommunityPostDto.SimpleResponse> {
        val searchPost = communityPostRepositoryCustom.searchPost(condition, pageable)

        return searchPost.map { CommunityPostDto.SimpleResponse.from(it) }
    }
}


