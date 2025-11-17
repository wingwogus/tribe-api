package com.tribe.tribe_api.community.service

import com.tribe.tribe_api.common.exception.BusinessException
import com.tribe.tribe_api.common.exception.ErrorCode
import com.tribe.tribe_api.common.util.security.SecurityUtil
import com.tribe.tribe_api.common.util.service.CloudinaryUploadService
import com.tribe.tribe_api.community.dto.CommunityPostDto
import com.tribe.tribe_api.community.dto.PostSearchCondition
import com.tribe.tribe_api.community.entity.CommunityPost
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
import org.springframework.web.multipart.MultipartFile

@Service
@Transactional
class CommunityPostService(
    private val communityPostRepository: CommunityPostRepository,
    private val memberRepository: MemberRepository,
    private val tripRepository: TripRepository,
    private val tripMemberRepository: TripMemberRepository,
    private val communityPostRepositoryCustom: CommunityPostRepositoryCustom,
    private val cloudinaryUploadService: CloudinaryUploadService // 이미지 업로드 서비스
) {

    // 1. 게시글 생성
    @PreAuthorize("@tripSecurityService.isTripOwner(#tripId)")
    fun createPost(tripId: Long, request: CommunityPostDto.CreateRequest, imageFile: MultipartFile?,): CommunityPostDto.DetailResponse {
        val memberId = SecurityUtil.getCurrentMemberId()
        val author = memberRepository.findById(memberId)
            .orElseThrow { BusinessException(ErrorCode.MEMBER_NOT_FOUND) }

        val trip = tripRepository.findById(request.tripId)
            .orElseThrow { BusinessException(ErrorCode.TRIP_NOT_FOUND) }


        // 이미지 처리
        var imageUrl: String? = null
        if (imageFile != null && !imageFile.isEmpty) {
            imageUrl = cloudinaryUploadService.upload(imageFile, "community")
        }

        val newPost = CommunityPost(
            author = author,
            trip = trip,
            title = request.title,
            content = request.content,
            representativeImageUrl = imageUrl
        )

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
    fun updatePost(postId: Long, request: CommunityPostDto.UpdateRequest, imageFile: MultipartFile?): CommunityPostDto.DetailResponse {
        val memberId = SecurityUtil.getCurrentMemberId()
        val post = communityPostRepository.findByIdWithDetails(postId)
            ?: throw BusinessException(ErrorCode.POST_NOT_FOUND)

        // 내용 업데이트 (Dirty Checking)
        post.title = request.title
        post.content = request.content

        // 이미지 업데이트 (새 파일이 들어온 경우에만)
        if (imageFile != null && !imageFile.isEmpty) {
            // [수정!] 기존 이미지 삭제 로직 구현
            val oldImageUrl = post.representativeImageUrl

            // 1. 새 이미지 업로드 및 URL 덮어쓰기
            post.representativeImageUrl = cloudinaryUploadService.upload(imageFile, "community")

            // 2. 기존 이미지가 있었다면 삭제 (null이 아닌지 확인)
            if (oldImageUrl != null) {
                cloudinaryUploadService.delete(oldImageUrl)
            }
        }

        return CommunityPostDto.DetailResponse.from(post) // 더티 체킹으로 자동 업데이트
    }

    // 5. 게시글 삭제
    @PreAuthorize("@tripSecurityService.isTripOwnerByPostId(#postId)")
    fun deletePost(postId: Long) {
        val memberId = SecurityUtil.getCurrentMemberId()

        val post = communityPostRepository.findByIdWithDetails(postId)
            ?: throw BusinessException(ErrorCode.POST_NOT_FOUND)

        if (post.author.id != memberId) {
            throw BusinessException(ErrorCode.NO_AUTHORITY_TRIP)
        }

        // DB에서 삭제하기 전에 Cloudinary 이미지부터 삭제
        val imageUrlToDelete = post.representativeImageUrl

        // 1. DB에서 게시글 삭제
        communityPostRepository.delete(post)

        // 2. Cloudinary에서 이미지 삭제 (DB 트랜잭션과 분리)
        if (imageUrlToDelete != null) {
            cloudinaryUploadService.delete(imageUrlToDelete)
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

