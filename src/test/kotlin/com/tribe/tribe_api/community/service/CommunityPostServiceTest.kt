package com.tribe.tribe_api.community.service

import com.ninjasquad.springmockk.MockkBean
import com.tribe.tribe_api.common.exception.BusinessException
import com.tribe.tribe_api.common.exception.ErrorCode
import com.tribe.tribe_api.common.util.security.CustomUserDetails
import com.tribe.tribe_api.common.util.service.CloudinaryUploadService
import com.tribe.tribe_api.community.dto.CommunityPostDto
import com.tribe.tribe_api.community.entity.CommunityPost
import com.tribe.tribe_api.community.repository.CommunityPostRepository
import com.tribe.tribe_api.member.entity.Member
import com.tribe.tribe_api.member.entity.Provider
import com.tribe.tribe_api.member.entity.Role
import com.tribe.tribe_api.member.repository.MemberRepository
import com.tribe.tribe_api.trip.entity.Country
import com.tribe.tribe_api.trip.entity.Trip
import com.tribe.tribe_api.trip.entity.TripRole
import com.tribe.tribe_api.trip.repository.TripMemberRepository
import com.tribe.tribe_api.trip.repository.TripRepository
import io.mockk.every
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.*

@SpringBootTest
@Transactional
class CommunityPostServiceIntegrationTest @Autowired constructor(
    private val communityPostService: CommunityPostService,
    private val memberRepository: MemberRepository,
    private val tripRepository: TripRepository,
    private val tripMemberRepository: TripMemberRepository,
    private val communityPostRepository: CommunityPostRepository,
    private val passwordEncoder: PasswordEncoder
) {

    // CloudinaryUploadService는 외부 API와 통신하므로 MockBean으로 처리합니다.
    @MockkBean
    private lateinit var cloudinaryUploadService: CloudinaryUploadService

    private lateinit var owner: Member
    private lateinit var member: Member
    private lateinit var nonMember: Member
    private lateinit var trip: Trip
    private lateinit var tripOfOtherCountry: Trip
    private lateinit var savedPost: CommunityPost

    @BeforeEach
    fun setUp() {
        // 1. 사용자 생성 (소유자, 일반 멤버, 비멤버)
        owner = memberRepository.save(Member("owner@test.com", passwordEncoder.encode("pw"), "여행소유자", null, Role.USER, Provider.LOCAL, null, false))
        member = memberRepository.save(Member("member@test.com", passwordEncoder.encode("pw"), "일반멤버", null, Role.USER, Provider.LOCAL, null, false))
        nonMember = memberRepository.save(Member("nonmember@test.com", passwordEncoder.encode("pw"), "비멤버", null, Role.USER, Provider.LOCAL, null, false))

        // 2. 여행 데이터 생성 (일본)
        trip = Trip("일본 여행", LocalDate.now(), LocalDate.now().plusDays(5), Country.JAPAN)
        trip.addMember(owner, TripRole.OWNER) // owner를 소유자로 추가
        trip.addMember(member, TripRole.MEMBER) // member를 일반 멤버로 추가
        tripRepository.save(trip)

        // 2-2. 다른 국가(미국) 여행 데이터 생성 (필터링 테스트용)
        tripOfOtherCountry = Trip("미국 여행", LocalDate.now(), LocalDate.now().plusDays(5), Country.USA)
        tripOfOtherCountry.addMember(owner, TripRole.OWNER)
        tripRepository.save(tripOfOtherCountry)

        // 3. 테스트용 게시글 데이터 미리 생성 (작성자: owner)
        savedPost = communityPostRepository.save(
            CommunityPost(
                author = owner,
                trip = trip,
                title = "기존 게시글",
                content = "기존 내용",
                representativeImageUrl = "http://example.com/old_image.jpg"
            )
        )

        // 4. 미국 여행 게시글 생성 (필터링 테스트용)
        communityPostRepository.save(
            CommunityPost(
                author = owner,
                trip = tripOfOtherCountry,
                title = "미국 여행 공유",
                content = "미국 여행 내용",
                representativeImageUrl = null
            )
        )
    }

    private fun setAuthentication(member: Member) {
        val userDetails = CustomUserDetails(member)
        val authentication = UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities)
        SecurityContextHolder.getContext().authentication = authentication
    }

    @Test
    @DisplayName("게시글 생성 성공 - 소유자(OWNER)가 이미지와 함께 생성")
    fun createPost_Success_WithImage() {
        // given: 소유자로 로그인
        setAuthentication(owner)
        val request = CommunityPostDto.CreateRequest(tripId = trip.id!!, title = "새 게시글", content = "새 내용")
        val imageFile = MockMultipartFile("image", "hello.jpg", "image/jpeg", "test image bytes".toByteArray())

        // Mocking: Cloudinary 업로드 시 "new_image_url" 반환하도록 설정
        every { cloudinaryUploadService.upload(imageFile, "community") } returns "http://example.com/new_image.jpg"

        // when
        val response = communityPostService.createPost(request, imageFile)

        // then
        assertThat(response.postId).isNotNull()
        assertThat(response.title).isEqualTo("새 게시글")
        assertThat(response.authorNickname).isEqualTo(owner.nickname)
        assertThat(response.representativeImageUrl).isEqualTo("http://example.com/new_image.jpg")
        assertThat(response.trip.tripId).isEqualTo(trip.id)
    }

    @Test
    @DisplayName("게시글 생성 실패 - 일반 멤버(MEMBER)가 시도")
    fun createPost_Fail_When_UserIsMember() {
        // given: 일반 멤버로 로그인
        setAuthentication(member)
        val request = CommunityPostDto.CreateRequest(tripId = trip.id!!, title = "글쓰기 시도", content = "내용")

        // when & then: NO_AUTHORITY_POST 에러가 발생하는지 검증
        val exception = assertThrows<BusinessException> {
            communityPostService.createPost(request, null)
        }
        assertThat(exception.errorCode).isEqualTo(ErrorCode.NO_AUTHORITY_POST)
    }

    @Test
    @DisplayName("게시글 생성 실패 - 비멤버(nonMember)가 시도")
    fun createPost_Fail_When_UserIsNotMember() {
        // given: 여행에 참여하지 않은 사용자로 로그인
        setAuthentication(nonMember)
        val request = CommunityPostDto.CreateRequest(tripId = trip.id!!, title = "글쓰기 시도", content = "내용")

        // when & then: NOT_A_TRIP_MEMBER 에러가 발생하는지 검증
        val exception = assertThrows<BusinessException> {
            communityPostService.createPost(request, null)
        }
        assertThat(exception.errorCode).isEqualTo(ErrorCode.NOT_A_TRIP_MEMBER)
    }

    @Test
    @DisplayName("게시글 목록 조회 성공 - 국가 필터링 (JAPAN)")
    fun getPosts_Success_WithCountryFilter() {
        // given
        val pageable: Pageable = PageRequest.of(0, 10)

        // when
        // @Query가 적용된 실제 Repository 메서드를 호출합니다.
        val response = communityPostService.getPosts(Country.JAPAN, pageable)

        // then
        assertThat(response.content).hasSize(1)
        assertThat(response.content[0].postId).isEqualTo(savedPost.id)
        assertThat(response.content[0].country).isEqualTo(Country.JAPAN.koreanName)
    }

    @Test
    @DisplayName("게시글 목록 조회 성공 - 필터링 없음")
    fun getPosts_Success_WithoutFilter() {
        // given
        val pageable: Pageable = PageRequest.of(0, 10)

        // when
        val response = communityPostService.getPosts(null, pageable)

        // then
        // setUp에서 2개의 게시글(일본, 미국)을 생성했으므로 2개가 조회되어야 함
        assertThat(response.content).hasSize(2)
    }

    @Test
    @DisplayName("게시글 상세 조회 성공")
    fun getPostDetail_Success() {
        // given
        val postId = savedPost.id!!

        // when
        val response = communityPostService.getPostDetail(postId)

        // then
        assertThat(response.postId).isEqualTo(postId)
        assertThat(response.title).isEqualTo("기존 게시글")
        assertThat(response.authorNickname).isEqualTo(owner.nickname)
        // 공유용 DTO에 민감 정보가 없는지 간접 확인 (categories는 있지만 members, expenses 등은 없음)
        assertThat(response.trip).isNotNull()
        assertThat(response.trip.categories).isNotNull()
    }

    @Test
    @DisplayName("게시글 상세 조회 실패 - 존재하지 않는 Post ID")
    fun getPostDetail_Fail_When_PostNotFound() {
        // given
        val invalidPostId = 999L

        // when & then
        val exception = assertThrows<BusinessException> {
            communityPostService.getPostDetail(invalidPostId)
        }
        assertThat(exception.errorCode).isEqualTo(ErrorCode.POST_NOT_FOUND)
    }

    @Test
    @DisplayName("게시글 수정 성공 - 작성자가 이미지 교체")
    fun updatePost_Success_WithImageChange() {
        // given: 작성자(owner)로 로그인
        setAuthentication(owner)
        val postId = savedPost.id!!
        val request = CommunityPostDto.UpdateRequest(title = "제목 수정됨", content = "내용 수정됨")
        val newImageFile = MockMultipartFile("image", "new.jpg", "image/jpeg", "new image bytes".toByteArray())
        val oldImageUrl = savedPost.representativeImageUrl!! // "http://example.com/old_image.jpg"

        // Mocking
        every { cloudinaryUploadService.upload(newImageFile, "community") } returns "http://example.com/new_image.jpg"
        every { cloudinaryUploadService.delete(oldImageUrl) } returns Unit // delete가 호출되는지 검증하기 위함

        // when
        val response = communityPostService.updatePost(postId, request, newImageFile)

        // then
        assertThat(response.title).isEqualTo("제목 수정됨")
        assertThat(response.content).isEqualTo("내용 수정됨")
        assertThat(response.representativeImageUrl).isEqualTo("http://example.com/new_image.jpg")

        // Cloudinary의 delete가 1번 호출되었는지 검증
        verify(exactly = 1) { cloudinaryUploadService.delete(oldImageUrl) }
    }

    @Test
    @DisplayName("게시글 수정 실패 - 작성자가 아닌 멤버가 시도")
    fun updatePost_Fail_When_UserIsNotAuthor() {
        // given: 작성자가 아닌, 같은 여행의 '일반 멤버'로 로그인
        setAuthentication(member)
        val postId = savedPost.id!!
        val request = CommunityPostDto.UpdateRequest(title = "해킹 시도", content = "해킹 내용")

        // when & then
        val exception = assertThrows<BusinessException> {
            communityPostService.updatePost(postId, request, null)
        }
        assertThat(exception.errorCode).isEqualTo(ErrorCode.NO_AUTHORITY_POST)
    }

    @Test
    @DisplayName("게시글 삭제 성공 - 작성자")
    fun deletePost_Success_AsAuthor() {
        // given: 작성자(owner)로 로그인
        setAuthentication(owner)
        val postId = savedPost.id!!
        val oldImageUrl = savedPost.representativeImageUrl!!

        // Mocking: delete 호출을 가로챕니다.
        every { cloudinaryUploadService.delete(oldImageUrl) } returns Unit

        // when
        communityPostService.deletePost(postId)

        // then
        // Cloudinary의 delete가 1번 호출되었는지 검증
        verify(exactly = 1) { cloudinaryUploadService.delete(oldImageUrl) }

        // DB에서도 정말 삭제되었는지 확인
        val foundPost = communityPostRepository.findById(postId)
        assertThat(foundPost.isPresent).isFalse()
    }

    @Test
    @DisplayName("게시글 삭제 실패 - 작성자가 아닌 멤버가 시도")
    fun deletePost_Fail_When_UserIsNotAuthor() {
        // given: 작성자가 아닌, 같은 여행의 '일반 멤버'로 로그인
        setAuthentication(member)
        val postId = savedPost.id!!

        // when & then
        val exception = assertThrows<BusinessException> {
            communityPostService.deletePost(postId)
        }
        assertThat(exception.errorCode).isEqualTo(ErrorCode.NO_AUTHORITY_POST)

        // DB에 데이터가 삭제되지 않고 남아있는지 확인
        val foundPost = communityPostRepository.findById(postId)
        assertThat(foundPost.isPresent).isTrue()
    }
}

