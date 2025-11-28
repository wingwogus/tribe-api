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
import com.tribe.tribe_api.itinerary.entity.Category
import com.tribe.tribe_api.itinerary.entity.ItineraryItem
import com.tribe.tribe_api.itinerary.entity.Place
import com.tribe.tribe_api.itinerary.repository.CategoryRepository
import com.tribe.tribe_api.itinerary.repository.ItineraryItemRepository
import com.tribe.tribe_api.itinerary.repository.PlaceRepository
import com.tribe.tribe_api.member.entity.Member
import com.tribe.tribe_api.member.entity.Provider
import com.tribe.tribe_api.member.entity.Role
import com.tribe.tribe_api.member.repository.MemberRepository
import com.tribe.tribe_api.trip.entity.Country
import com.tribe.tribe_api.trip.entity.Trip
import com.tribe.tribe_api.trip.entity.TripRole
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
import java.time.LocalDateTime

@ActiveProfiles("test")
@SpringBootTest
@Transactional
class CommunityPostServiceIntegrationTest @Autowired constructor(
    private val communityPostService: CommunityPostService,
    private val memberRepository: MemberRepository,
    private val tripRepository: TripRepository,
    private val communityPostRepository: CommunityPostRepository,
    private val passwordEncoder: PasswordEncoder,
    private val placeRepository: PlaceRepository,
    private val categoryRepository: CategoryRepository,
    private val itineraryItemRepository: ItineraryItemRepository
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
        owner = memberRepository.save(Member
            ("owner@test.com",
            passwordEncoder.encode("pw"),
            "여행소유자",
            null, Role.USER, Provider.LOCAL,
            null,
            false))

        member = memberRepository.save(Member
            ("member@test.com",
            passwordEncoder.encode("pw"),
            "일반멤버",
            null, Role.USER, Provider.LOCAL,
            null,
            false))

        nonMember = memberRepository.save(Member
            ("nonmember@test.com",
            passwordEncoder.encode("pw"),
            "비멤버",
            null, Role.USER, Provider.LOCAL,
            null,
            false))

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

        // trip에 category와 itineraryitem 추가
        val category1 = categoryRepository.save(Category(trip,1, "카테고리 1" , 1))

        val item = ItineraryItem(
            category1,
            mockPlace,
            "원본 일정 제목",
            LocalDateTime.now(),
            1,
            "원본 일정 메모"
        )

        itineraryItemRepository.save(item)

        category1.itineraryItems.add(item)

        trip.categories.add(category1) // trip 엔티티에 category추가

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
            assertThat(postsPage.content.map { it.title }).containsExactlyInAnyOrder("기존 게시글", "레전드 미국 여행 공유")
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

    @Test @DisplayName("게시글 생성 성공 - 소유자(OWNER)가 Trip의 일정 복사")
    fun createPost_Success_WithDaysAndPhotos() {
        // given: 소유자로 로그인
        setAuthentication(owner)

        val request = CommunityPostDto.CreateRequest(
            tripId = trip.id!!,
            title = "새로운 자동 생성 게시글",
            content = "소개글",
            representativeImageUrl = "http://mock.cdn/new_rep.jpg",
            )

        // when
        val response = communityPostService.createPost(request)

        // then
        assertThat(response.postId).isNotNull()
        assertThat(response.title).isEqualTo("새로운 자동 생성 게시글")
        assertThat(response.representativeImageUrl).isEqualTo("http://mock.cdn/new_rep.jpg")

        // 계층 구조 검증 - setUp에서 만든 trip의 구조가 복사되었는지 확인
        assertThat(response.days).hasSize(1)
        val dayResponse = response.days[0]
        assertThat(dayResponse.day).isEqualTo(1)
        assertThat(dayResponse.content).isEqualTo("") //복사 시 day의 content는 비어있음

        assertThat(dayResponse.itineraries).hasSize(1)
        val itineraryResponse = dayResponse.itineraries[0]
        assertThat(itineraryResponse.order).isEqualTo(1)
        assertThat(itineraryResponse.content).isEqualTo("원본 일정 메모")
        assertThat(itineraryResponse.place?.externalPlaceId).isEqualTo(mockPlace.externalPlaceId)
        assertThat(itineraryResponse.photos).isEmpty()

    }

    @Test @DisplayName("게시글 생성 실패 - 일반 멤버(MEMBER)가 시도")
    fun createPost_Fail_When_UserIsMember() {
        // given: 일반 멤버로 로그인
        setAuthentication(member)
        val request = CommunityPostDto.CreateRequest(tripId = trip.id!!, title = "글쓰기 시도", content = "내용",  representativeImageUrl = null)

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
        val request = CommunityPostDto.CreateRequest(tripId = trip.id!!, title = "글쓰기 시도", content = "내용",  representativeImageUrl = null)

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

        // 1. DB에서 최신 상태 조회 (ID 확보)
        val postToUpdate = communityPostRepository.findById(postId).get()
        val dayToUpdate = postToUpdate.days.first()
        val itineraryToUpdate = dayToUpdate.itineraries.first()
        val photoToDelete = itineraryToUpdate.photos.first()

        assertThat(photoToDelete.id).isNotNull()

        // 2. 삭제되어야 할 URL들 미리 변수에 저장 (검증용)
        val oldRepUrl = postToUpdate.representativeImageUrl!!
        val oldPhotoUrl = photoToDelete.imageUrl

        // 3. Mocking
        every { cloudinaryUploadService.delete(any()) } returns Unit

        // 4. Request 생성
        val request = CommunityPostDto.UpdateRequest(
            title = "수정된 제목",
            content = "수정된 내용",
            representativeImageUrl = "http://cdn/new_rep.jpg", // 새 이미지
            days = listOf(
                CommunityPostDto.DayUpdateRequest(
                    id = dayToUpdate.id!!,
                    day = 1,
                    content = "수정된 day 요약",
                    itineraries = listOf(
                        CommunityPostDto.ItineraryUpdateRequest(
                            id = itineraryToUpdate.id!!,
                            order = 1,
                            content = "수정된 일정 내용",
                            photos = listOf(
                                CommunityPostDto.PhotoUpdateRequest(
                                    id = null,
                                    imageUrl = "http://cdn/new_photo.jpg"
                                )
                            ),
                            photoIdsToDelete = listOf(photoToDelete.id!!), // 삭제 요청
                            placeId = null
                        )
                    )
                )
            )
        )

        // when
        val response = communityPostService.updatePost(postId, request)

        // then
        assertThat(response.title).isEqualTo("수정된 제목")
        assertThat(response.representativeImageUrl).isEqualTo("http://cdn/new_rep.jpg")
        assertThat(response.days[0].content).isEqualTo("수정된 day 요약")
        assertThat(response.days[0].itineraries[0].content).isEqualTo("수정된 일정 내용")

        // 기존 코드(verify exactly = 1)는 순서나 중복 호출에 민감해서 실패할 수 있습니다.
        // 아래처럼 capture를 사용해야 100% 통과합니다.

        val deletedUrls = mutableListOf<String>()

        // delete 함수가 호출될 때마다 인자를 리스트에 담습니다.
        verify { cloudinaryUploadService.delete(capture(deletedUrls)) }

        // 두 URL이 모두 삭제되었는지 확인 (순서 상관 없음)
        assertThat(deletedUrls).contains(oldRepUrl, oldPhotoUrl)
        assertThat(deletedUrls).hasSize(2)
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
