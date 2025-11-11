package com.tribe.tribe_api.community.controller

import com.tribe.tribe_api.common.util.ApiResponse
import com.tribe.tribe_api.community.dto.CommunityPostDto
import com.tribe.tribe_api.community.service.CommunityPostService
import com.tribe.tribe_api.trip.entity.Country
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/v1/community/posts") // Base URL
class CommunityPostController(
    private val communityPostService: CommunityPostService
) {

    /**
     * 1. 게시글 생성 (여행 공유)
     * JSON(request)과 Image(imageFile)를 함께 받기 위해 multipart/form-data 사용
     */
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun createPost(
        @Valid @RequestPart("request") request: CommunityPostDto.CreateRequest,
        @RequestPart(value = "image", required = false) imageFile: MultipartFile?
    ): ResponseEntity<ApiResponse<CommunityPostDto.DetailResponse>> {
        val response = communityPostService.createPost(request.tripId, request, imageFile)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success("게시글이 성공적으로 공유되었습니다.", response))
    }

    /**
     * 2. 게시글 목록 조회 (국가별 필터링)
     */
    @GetMapping
    fun getPosts(
        @RequestParam(required = false) country: Country?,
        @PageableDefault(size = 10, sort = ["createdAt"], direction = Sort.Direction.DESC) pageable: Pageable
    ): ResponseEntity<ApiResponse<Page<CommunityPostDto.SimpleResponse>>> {
        val response = communityPostService.getPosts(country, pageable)
        return ResponseEntity.ok(ApiResponse.success("게시글 목록 조회 성공", response))
    }

    /**
     * 3. 게시글 상세 조회
     */
    @GetMapping("/{postId}")
    fun getPostDetail(
        @PathVariable postId: Long
    ): ResponseEntity<ApiResponse<CommunityPostDto.DetailResponse>> {
        val response = communityPostService.getPostDetail(postId)
        return ResponseEntity.ok(ApiResponse.success("게시글 상세 조회 성공", response))
    }

    /**
     * 4. 게시글 수정
     */
    @PatchMapping("/{postId}", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun updatePost(
        @PathVariable postId: Long,
        @Valid @RequestPart("request") request: CommunityPostDto.UpdateRequest,
        @RequestPart(value = "image", required = false) imageFile: MultipartFile?
    ): ResponseEntity<ApiResponse<CommunityPostDto.DetailResponse>> {
        val response = communityPostService.updatePost(postId, request, imageFile)
        return ResponseEntity.ok(ApiResponse.success("게시글 수정 성공", response))
    }

    /**
     * 5. 게시글 삭제
     */
    @DeleteMapping("/{postId}")
    fun deletePost(
        @PathVariable postId: Long
    ): ResponseEntity<ApiResponse<Unit>> {
        communityPostService.deletePost(postId)
        return ResponseEntity.ok(ApiResponse.success("게시글 삭제 성공", null))
    }

    /**
     * 6. 특정 MemberId가 작성한 모든 게시글 목록 조회
     */
    @GetMapping("/by-author/{memberId}")
    fun getPostsByAuthorId(
        @PathVariable memberId: Long,
        @PageableDefault(size = 10, sort = ["createdAt"], direction = Sort.Direction.DESC) pageable: Pageable
    ): ResponseEntity<ApiResponse<Page<CommunityPostDto.SimpleResponse>>> {
        val postsPage = communityPostService.getPostsByMemberId(memberId, pageable)
        return ResponseEntity.ok(ApiResponse.success("회원 작성 게시글 목록 조회 성공", postsPage))
    }
}

