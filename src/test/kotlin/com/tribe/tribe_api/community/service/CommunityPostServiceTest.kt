package com.tribe.tribe_api.community.service

import com.ninjasquad.springmockk.MockkBean
import com.tribe.tribe_api.common.exception.BusinessException
import com.tribe.tribe_api.common.exception.ErrorCode
import com.tribe.tribe_api.common.util.security.CustomUserDetails
import com.tribe.tribe_api.common.util.service.CloudinaryUploadService
import com.tribe.tribe_api.community.dto.CommunityPostDto
import com.tribe.tribe_api.community.entity.CommunityPost
import com.tribe.tribe_api.community.entity.CommunityPostDay
import com.tribe.tribe_api.community.entity.CommunityPostItinerary
import com.tribe.tribe_api.community.entity.CommunityPostItineraryPhoto
import com.tribe.tribe_api.community.repository.CommunityPostRepository
import com.tribe.tribe_api.itinerary.entity.Place
import com.tribe.tribe_api.itinerary.repository.PlaceRepository
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
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate

@ActiveProfiles("test")
@SpringBootTest
@Transactional
class CommunityPostServiceIntegrationTest @Autowired constructor(
    private val communityPostService: CommunityPostService,
    private val memberRepository: MemberRepository,
    private val tripRepository: TripRepository,
    private val tripMemberRepository: TripMemberRepository,
    private val communityPostRepository: CommunityPostRepository,
    private val passwordEncoder: PasswordEncoder,
    private val placeRepository: PlaceRepository
) {

    @MockkBean
    private lateinit var cloudinaryUploadService: CloudinaryUploadService

    private lateinit var owner: Member
    private lateinit var member: Member
    private lateinit var nonMember: Member
    private lateinit var trip: Trip
    private lateinit var tripOfOtherCountry: Trip
    private lateinit var savedPost: CommunityPost
    private lateinit var mockPlace: Place

    @BeforeEach
    fun setUp() {
        // 1. 사용자 생성
        owner = memberRepository.save(Member("owner@test.com", passwordEncoder.encode("pw"), "여행소유자", null, Role.USER, Provider.LOCAL, null, false))
        member = memberRepository.save(Member("member@test.com", passwordEncoder.encode("pw"), "일반멤버", null, Role.USER, Provider.LOCAL, null, false))
        nonMember = memberRepository.save(Member("nonmember@test.com", passwordEncoder.encode("pw"), "비멤버", null, Role.USER, Provider.LOCAL, null, false))

        // Mock Place 생성
        mockPlace = Place("googlePlaceId",
            "테스트 장소",
            "주소",
            BigDecimal.valueOf(37.5),
            BigDecimal.valueOf(127.0),
            )
        placeRepository.save(mockPlace)


        // 2. 여행 데이터 생성
        trip = Trip("레전드 일본 여행", LocalDate.now(), LocalDate.now().plusDays(5), Country.JAPAN)
        trip.addMember(owner, TripRole.OWNER)
        trip.addMember(member, TripRole.MEMBER)
        tripRepository.save(trip)

        tripOfOtherCountry = Trip("레전드 미쿡 여행", LocalDate.now(), LocalDate.now().plusDays(5), Country.USA)
        tripOfOtherCountry.addMember(owner, TripRole.OWNER)
        tripRepository.save(tripOfOtherCountry)

        // 3. 테스트용 게시글 데이터 (계층 구조 포함)
        val postToSave = CommunityPost(
            author = owner,
            trip = trip,
            title = "기존 게시글",
            content = "기존 내용",
            representativeImageUrl = "http://example.com/old_rep.jpg"
        )
        val day = CommunityPostDay(postToSave, 1, "기존 1일차 요약")
        val itinerary = CommunityPostItinerary(day, mockPlace, 1, "기존 1일차 1번 일정")
        val photo = CommunityPostItineraryPhoto(itinerary, "http://example.com/old_itinerary_photo.jpg")

        itinerary.photos.add(photo)
        day.itineraries.add(itinerary)
        postToSave.days.add(day)
        savedPost = communityPostRepository.save(postToSave)


        // 4. 미국 여행 게시글 생성
        communityPostRepository.save(
            CommunityPost(
                author = owner,
                trip = tripOfOtherCountry,
                title = "레전드 미국 여행 공유",
                content = "레전드 미국 여행 내용"
            )
        )
    }

    private fun setAuthentication(member: Member) {
        val userDetails = CustomUserDetails(member)
        val authentication = UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities)
        SecurityContextHolder.getContext().authentication = authentication
    }

    @Nested @DisplayName("회원 작성 게시글 목록 조회 (getPostsByAuthorId)")
    inner class GetPostsByAuthorIdTest {

        @Test @DisplayName("성공 - 특정 작성자의 게시글 목록 조회 및 페이지 검증")
        fun getPostsByAuthorId_Success() {
            // given: 작성자 owner의 ID와 Pageable 객체
            val authorId = owner.id!!
            val pageable: Pageable = PageRequest.of(0, 10)

            // when
            val postsPage = communityPostService.getPostsByMemberId(authorId, pageable)

            // then: owner가 작성한 2개의 게시글이 모두 조회되어야 하며, Page 객체 검증
            assertThat(postsPage.totalElements).isEqualTo(2) // 전체 요소 개수
            assertThat(postsPage.content).hasSize(2) // 현재 페이지 컨텐츠 크기
            assertThat(postsPage.content.map { it.authorNickname }).allMatch { it == owner.nickname }
            assertThat(postsPage.content.map { it.title }).containsExactlyInAnyOrder("기존 게시글", "미국 여행 공유")
        }

        @Test @DisplayName("성공 - 게시글이 없는 사용자의 목록 조회 및 페이지 검증")
        fun getPostsByAuthorId_Success_NoPosts() {
            // given: 게시글을 작성하지 않은 member의 ID와 Pageable 객체
            val nonAuthorId = member.id!!
            val pageable: Pageable = PageRequest.of(0, 10)

            // when
            val postsPage = communityPostService.getPostsByMemberId(nonAuthorId, pageable)

            // then: 결과는 비어 있어야 하며, Page 객체 검증
            assertThat(postsPage.totalElements).isEqualTo(0)
            assertThat(postsPage.content).isEmpty()
        }

        @Test @DisplayName("성공 - 존재하지 않는 멤버 ID로 조회 및 페이지 검증")
        fun getPostsByAuthorId_Success_NonExistingMember() {
            // given: 존재하지 않는 멤버 ID와 Pageable 객체
            val invalidMemberId = 999L
            val pageable: Pageable = PageRequest.of(0, 10)

            // when
            val postsPage = communityPostService.getPostsByMemberId(invalidMemberId, pageable)

            // then: 게시글이 없으므로 빈 목록이 반환되어야 하며, Page 객체 검증
            assertThat(postsPage.totalElements).isEqualTo(0)
            assertThat(postsPage.content).isEmpty()
        }
    }

    @Test @DisplayName("게시글 생성 성공 - 소유자(OWNER)가 Day, Itinerary, Photo 컨텐츠와 함께 생성")
    fun createPost_Success_WithDaysAndPhotos() {
        // given: 소유자로 로그인
        setAuthentication(owner)

        val itineraryRequest = CommunityPostDto.ItineraryCreateRequest(
            placeId = mockPlace.id,
            order = 1,
            content = "새로운 일정 내용",
            photoUrls = listOf("http://mock.cdn/new_photo.jpg")
        )
        val dayRequest = CommunityPostDto.DayCreateRequest(
            day = 1,
            content = "새로운 Day 요약",
            itineraries = listOf(itineraryRequest)
        )
        val request = CommunityPostDto.CreateRequest(
            tripId = trip.id!!,
            title = "새로운 계층형 게시글",
            content = "새로운 소개글",
            representativeImageUrl = "http://mock.cdn/new_rep.jpg",
            days = listOf(dayRequest)
        )

        // when
        val response = communityPostService.createPost(request)

        // then
        assertThat(response.postId).isNotNull()
        assertThat(response.title).isEqualTo("새로운 계층형 게시글")
        assertThat(response.representativeImageUrl).isEqualTo("http://mock.cdn/new_rep.jpg")

        // 계층 구조 검증
        assertThat(response.days).hasSize(1)
        val dayResponse = response.days[0]
        assertThat(dayResponse.day).isEqualTo(1)
        assertThat(dayResponse.content).isEqualTo("새로운 Day 요약")

        assertThat(dayResponse.itineraries).hasSize(1)
        val itineraryResponse = dayResponse.itineraries[0]
        assertThat(itineraryResponse.order).isEqualTo(1)
        assertThat(itineraryResponse.content).isEqualTo("새로운 일정 내용")
        assertThat(itineraryResponse.place?.externalPlaceId).isEqualTo(mockPlace.externalPlaceId)

        assertThat(itineraryResponse.photos).hasSize(1)
        assertThat(itineraryResponse.photos[0].imageUrl).isEqualTo("http://mock.cdn/new_photo.jpg")
    }

    @Test @DisplayName("게시글 생성 실패 - 일반 멤버(MEMBER)가 시도")
    fun createPost_Fail_When_UserIsMember() {
        // given: 일반 멤버로 로그인
        setAuthentication(member)
        val request = CommunityPostDto.CreateRequest(tripId = trip.id!!, title = "글쓰기 시도", content = "내용",  representativeImageUrl = null, days = emptyList())

        // when & then: NO_AUTHORITY_POST 에러가 발생하는지 검증
        val exception = assertThrows<BusinessException> {
            communityPostService.createPost(request)
        }
        assertThat(exception.errorCode).isEqualTo(ErrorCode.NO_AUTHORITY_TRIP)
    }

    @Test @DisplayName("게시글 생성 실패 - 비멤버(nonMember)가 시도")
    fun createPost_Fail_When_UserIsNotMember() {
        // given: 여행에 참여하지 않은 사용자로 로그인
        setAuthentication(nonMember)
        val request = CommunityPostDto.CreateRequest(tripId = trip.id!!, title = "글쓰기 시도", content = "내용",  representativeImageUrl = null, days = emptyList())

        // when & then: NOT_A_TRIP_MEMBER 에러가 발생하는지 검증
        val exception = assertThrows<BusinessException> {
            communityPostService.createPost(request)
        }
        assertThat(exception.errorCode).isEqualTo(ErrorCode.NOT_A_TRIP_MEMBER)
    }

    @Test @DisplayName("게시글 목록 조회 성공 - 국가 필터링 (JAPAN)")
    fun getPosts_Success_WithCountryFilter() {
        // given
        val pageable: Pageable = PageRequest.of(0, 10)

        // when
        val response = communityPostService.getPosts(Country.JAPAN, pageable)

        // then
        assertThat(response.content).hasSize(1)
        assertThat(response.content[0].postId).isEqualTo(savedPost.id)
        assertThat(response.content[0].country).isEqualTo(Country.JAPAN.koreanName)
    }

    @Test @DisplayName("게시글 목록 조회 성공 - 필터링 없음")
    fun getPosts_Success_WithoutFilter() {
        // given
        val pageable: Pageable = PageRequest.of(0, 10)

        // when
        val response = communityPostService.getPosts(null, pageable)

        // then
        assertThat(response.content).hasSize(2)
    }

    @Test @DisplayName("게시글 상세 조회 성공")
    fun getPostDetail_Success() {
        // given
        val postId = savedPost.id!!

        // when
        val response = communityPostService.getPostDetail(postId)

        // then
        assertThat(response.postId).isEqualTo(postId)
        assertThat(response.title).isEqualTo("기존 게시글")
        assertThat(response.authorNickname).isEqualTo(owner.nickname)
        assertThat(response.tripMapData).isNotNull()

        // 상세한 계층 구조 검증 추가
        assertThat(response.days).hasSize(1)
        assertThat(response.days[0].itineraries).hasSize(1)
        assertThat(response.days[0].itineraries[0].photos).hasSize(1)
        assertThat(response.days[0].itineraries[0].photos[0].imageUrl).isEqualTo("http://example.com/old_itinerary_photo.jpg")
    }

    @Test @DisplayName("게시글 상세 조회 실패 - 존재하지 않는 Post ID")
    fun getPostDetail_Fail_When_PostNotFound() {
        // given
        val invalidPostId = 999L

        // when & then
        val exception = assertThrows<BusinessException> {
            communityPostService.getPostDetail(invalidPostId)
        }
        assertThat(exception.errorCode).isEqualTo(ErrorCode.POST_NOT_FOUND)
    }

    @Test @DisplayName("게시글 수정 성공 - 작성자가 컨텐츠 및 이미지 URL 변경")
    fun updatePost_Success_WithContentAndUrlChange() {
        // given: 작성자(owner)로 로그인
        setAuthentication(owner)
        val postId = savedPost.id!!
        val oldRepUrl = savedPost.representativeImageUrl!!
        val oldItineraryPhotoUrl = "http://example.com/old_itinerary_photo.jpg"

        every { cloudinaryUploadService.delete(any()) } returns Unit

        val newItineraryReq = CommunityPostDto.ItineraryCreateRequest(placeId = mockPlace.id, order = 1, content = "수정된 일정", photoUrls = listOf("http://cdn/new_itinerary.jpg"))
        val newDayReq = CommunityPostDto.DayCreateRequest(day = 1, content = "수정된 Day", itineraries = listOf(newItineraryReq))
        val request = CommunityPostDto.UpdateRequest(
            title = "제목 수정됨",
            content = "내용 수정됨",
            representativeImageUrl = "http://cdn/new_rep.jpg",
            days = listOf(newDayReq)
        )

        // when
        val response = communityPostService.updatePost(postId, request)

        // then
        // 1. 기본 정보 검증
        assertThat(response.title).isEqualTo("제목 수정됨")
        assertThat(response.representativeImageUrl).isEqualTo("http://cdn/new_rep.jpg")

        // 2. 계층 구조 검증
        assertThat(response.days).hasSize(1)
        assertThat(response.days[0].itineraries).hasSize(1)
        assertThat(response.days[0].itineraries[0].content).isEqualTo("수정된 일정")
        assertThat(response.days[0].itineraries[0].photos).hasSize(1)
        assertThat(response.days[0].itineraries[0].photos[0].imageUrl).isEqualTo("http://cdn/new_itinerary.jpg")

        // 3. 기존 이미지 삭제 검증
        verify(exactly = 1) { cloudinaryUploadService.delete(oldRepUrl) }
        verify(exactly = 1) { cloudinaryUploadService.delete(oldItineraryPhotoUrl) }
    }

    @Test @DisplayName("게시글 수정 실패 - 작성자가 아닌 멤버가 시도")
    fun updatePost_Fail_When_UserIsNotAuthor() {
        // given: 작성자가 아닌, 같은 여행의 일반 멤버로 로그인
        setAuthentication(member)
        val postId = savedPost.id!!
        val request = CommunityPostDto.UpdateRequest(title = "해킹 시도", content = "해킹 내용", representativeImageUrl = null, days = emptyList())

        // when & then
        val exception = assertThrows<BusinessException> {
            communityPostService.updatePost(postId, request)
        }
        assertThat(exception.errorCode).isEqualTo(ErrorCode.NO_AUTHORITY_TRIP)
    }

    @Test @DisplayName("게시글 삭제 성공 - 작성자")
    fun deletePost_Success_AsAuthor() {
        // given: 작성자(owner)로 로그인
        setAuthentication(owner)
        val postId = savedPost.id!!
        val oldRepUrl = savedPost.representativeImageUrl!!
        val oldItineraryPhotoUrl = "http://example.com/old_itinerary_photo.jpg"

        every { cloudinaryUploadService.delete(any()) } returns Unit

        // when
        communityPostService.deletePost(postId)

        // then
        // Cloudinary의 delete가 모든 이미지에 대해 호출되었는지 검증 (대표이미지 1 + 일정사진 1 = 2)
        verify(exactly = 2) { cloudinaryUploadService.delete(any()) }
        verify(exactly = 1) { cloudinaryUploadService.delete(oldRepUrl) }
        verify(exactly = 1) { cloudinaryUploadService.delete(oldItineraryPhotoUrl) }

        // DB에서도 정말 삭제되었는지 확인
        val foundPost = communityPostRepository.findById(postId)
        assertThat(foundPost.isPresent).isFalse()
    }

    @Test @DisplayName("게시글 삭제 실패 - 작성자가 아닌 멤버가 시도")
    fun deletePost_Fail_When_UserIsNotAuthor() {
        // given: 작성자가 아닌, 같은 여행의 일반 멤버로 로그인
        setAuthentication(member)
        val postId = savedPost.id!!

        // when & then
        val exception = assertThrows<BusinessException> {
            communityPostService.deletePost(postId)
        }
        assertThat(exception.errorCode).isEqualTo(ErrorCode.NO_AUTHORITY_TRIP)

        // DB에 데이터가 삭제되지 않고 남아있는지 확인
        val foundPost = communityPostRepository.findById(postId)
        assertThat(foundPost.isPresent).isTrue()
    }
}
