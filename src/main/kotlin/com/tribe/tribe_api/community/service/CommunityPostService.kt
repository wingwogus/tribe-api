package com.tribe.tribe_api.community.service

import com.tribe.tribe_api.common.exception.BusinessException
import com.tribe.tribe_api.common.exception.ErrorCode
import com.tribe.tribe_api.common.util.security.SecurityUtil
import com.tribe.tribe_api.common.util.service.CloudinaryUploadService
import com.tribe.tribe_api.community.dto.CommunityPostDto
import com.tribe.tribe_api.community.dto.PostSearchCondition
import com.tribe.tribe_api.community.entity.CommunityPost
import com.tribe.tribe_api.community.entity.CommunityPostDay
import com.tribe.tribe_api.community.entity.CommunityPostDayPhoto
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
    private val cloudinaryUploadService: CloudinaryUploadService, // 이미지 업로드 서비스
) {

    /**
     * Day별 컨텐츠(Days and Photos)를 생성하고 부모 Post에 연결하는 공통 로직
     */
    private fun mapDaysAndPhotos(post: CommunityPost, daysRequest: List<CommunityPostDto.DayCreateRequest>) {
        daysRequest.forEach { dayRequest ->
            val newDay = CommunityPostDay(
                communityPost = post,
                day = dayRequest.day,
                content = dayRequest.content
            )

            // 3. Day별 사진 (손자) 엔티티 생성 및 연결
            dayRequest.photoUrls.forEach { imageUrl ->
                val newPhoto = CommunityPostDayPhoto(
                    communityPostDay = newDay,
                    imageUrl = imageUrl
                )
                newDay.photos.add(newPhoto) // Day에 사진 연결
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
            ?: throw BusinessException(ErrorCode.NOT_A_TRIP_MEMBER) //member가 해당 Trip에 존재하는지만 확인


        val newPost = CommunityPost(
            author = author,
            trip = trip,
            title = request.title,
            content = request.content,
            representativeImageUrl = request.representativeImageUrl,
        )

        mapDaysAndPhotos(newPost, request.days)

        val savedPost = communityPostRepository.save(newPost)
        // 상세 조회 DTO를 반환하여 생성된 내용을 바로 확인
        return CommunityPostDto.DetailResponse.from(savedPost)
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
        // N+1 방지를 위해 Fetch Join 쿼리 사용
        val post = communityPostRepository.findByIdWithDetails(postId)
            ?: throw BusinessException(ErrorCode.POST_NOT_FOUND)

        // 보여줄 정보만 가져온 표시되는 dto로 반환
        return CommunityPostDto.DetailResponse.from(post)
    }

    // 게시글 수정
    @PreAuthorize("@tripSecurityService.isTripOwnerByPostId(#postId)")
    fun updatePost(postId: Long, request: CommunityPostDto.UpdateRequest): CommunityPostDto.DetailResponse {
        val post = communityPostRepository.findByIdWithDetails(postId)
            ?: throw BusinessException(ErrorCode.POST_NOT_FOUND)


        // 기존 이미지 url 저장 및 메타데이터 업데이트
        val oldImageUrl = post.representativeImageUrl
        post.title = request.title
        post.content = request.content
        post.representativeImageUrl = request.representativeImageUrl


        // 기존 day별 컨텐츠및 사진 모두 삭제
        post.days.forEach { postDay->
            postDay.photos.forEach { photo ->
                cloudinaryUploadService.delete(photo.imageUrl)
            }
        }
        post.days.clear() // 고아제거로 인해 기존 day, photo 모두 제거

        mapDaysAndPhotos(post, request.days)

        //대표 이미지 변경시 cloudinary 이미지 삭제
        if(oldImageUrl != null && oldImageUrl != post.representativeImageUrl){
            cloudinaryUploadService.delete(oldImageUrl)
        }

        val savedPost = communityPostRepository.save(post)
        return CommunityPostDto.DetailResponse.from(savedPost)
    }

    // 5. 게시글 삭제
    @PreAuthorize("@tripSecurityService.isTripOwnerByPostId(#postId)")
    fun deletePost(postId: Long) {

        val post = communityPostRepository.findByIdWithDetails(postId)
            ?: throw BusinessException(ErrorCode.POST_NOT_FOUND)

        // 모든 day별 사진과 대표 이미지 삭제
        val imageUrlsToDelete = mutableListOf<String>()
        if (post.representativeImageUrl != null) {
            imageUrlsToDelete.add(post.representativeImageUrl!!)
        }

        // Day별 첨부된 모든 사진 URL 수집
        post.days.forEach { postDay ->
            postDay.photos.forEach { photo ->
                imageUrlsToDelete.add(photo.imageUrl)
            }
        }

        // 1. DB에서 게시글 삭제 (Days와 Photos는 Cascade와 orphanRemoval로 자동 삭제됨)
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

