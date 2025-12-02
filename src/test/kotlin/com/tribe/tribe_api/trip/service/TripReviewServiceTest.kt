package com.tribe.tribe_api.trip.service

import com.ninjasquad.springmockk.MockkBean
import com.tribe.tribe_api.common.exception.BusinessException
import com.tribe.tribe_api.common.exception.ErrorCode
import com.tribe.tribe_api.common.util.security.CustomUserDetails
import com.tribe.tribe_api.common.util.service.GeminiApiClient
import com.tribe.tribe_api.common.util.service.GoogleMapService
import com.tribe.tribe_api.itinerary.dto.PlaceDto
import com.tribe.tribe_api.itinerary.entity.Category
import com.tribe.tribe_api.itinerary.entity.ItineraryItem
import com.tribe.tribe_api.itinerary.entity.Place
import com.tribe.tribe_api.itinerary.entity.RecommendedPlace
import com.tribe.tribe_api.itinerary.repository.CategoryRepository
import com.tribe.tribe_api.itinerary.repository.ItineraryItemRepository
import com.tribe.tribe_api.itinerary.repository.PlaceRepository
import com.tribe.tribe_api.itinerary.repository.RecommendedPlaceRepository
import com.tribe.tribe_api.member.entity.Member
import com.tribe.tribe_api.member.entity.Provider
import com.tribe.tribe_api.member.entity.Role
import com.tribe.tribe_api.member.repository.MemberRepository
import com.tribe.tribe_api.trip.dto.TripReviewRequest
import com.tribe.tribe_api.trip.entity.Country
import com.tribe.tribe_api.trip.entity.Trip
import com.tribe.tribe_api.trip.entity.TripReview
import com.tribe.tribe_api.trip.entity.TripRole
import com.tribe.tribe_api.trip.repository.TripRepository
import com.tribe.tribe_api.trip.repository.TripReviewRepository
import io.mockk.every
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@SpringBootTest
@Transactional
@ExtendWith(MockKExtension::class)
class TripReviewServiceTest @Autowired constructor(
    private var tripReviewService: TripReviewService,
    private val tripRepository: TripRepository,
    private val tripReviewRepository: TripReviewRepository,
    private val memberRepository: MemberRepository,
    private val passwordEncoder: PasswordEncoder,
    private val placeRepository: PlaceRepository,
    private val categoryRepository: CategoryRepository,
    private val itineraryItemRepository: ItineraryItemRepository,
    private val recommendedPlaceRepository: RecommendedPlaceRepository, // 추가
) {
    // Mock 객체 주입
    @MockkBean
    private lateinit var geminiApiClient: GeminiApiClient

    @MockkBean // 추가
    private lateinit var googleMapService: GoogleMapService

    private lateinit var owner: Member
    private lateinit var member: Member
    private lateinit var trip: Trip
    private lateinit var savedReview: TripReview
    private lateinit var place1: Place
    private lateinit var place2: Place

    @BeforeEach
    fun setUp() {
        // 1. 사용자 생성
        owner = memberRepository.save(
            Member(
                "owner@test.com",
                passwordEncoder.encode("pw"),
                "방장",
                null, Role.USER, Provider.LOCAL,
                null,
                false))
        member = memberRepository.save(
            Member(
                "member@test.com",
                passwordEncoder.encode("pw"),
                "멤버",
                null, Role.USER, Provider.LOCAL,
                null,
                false))

        // 2. 여행 데이터 생성
        trip = Trip(
            "테스트 여행",
            LocalDate.now(), LocalDate.now().plusDays(5),
            Country.JAPAN)
        trip.addMember(owner, TripRole.OWNER)
        trip.addMember(member, TripRole.MEMBER)
        tripRepository.save(trip)

        place1 = placeRepository.save(Place
            ("place1_id",
            "오사카 성",
            "일본 오사카",
            BigDecimal.valueOf(34.6873), BigDecimal.valueOf(135.5259)))
        place2 = placeRepository.save(Place
            ("place2_id",
            "도톤보리",
            "일본 오사카",
            BigDecimal.valueOf(34.6687), BigDecimal.valueOf(135.5013)))


        val category = categoryRepository.save(Category
            (trip, 1, "Day 1", 1))

        val itineraryItem = itineraryItemRepository.save(
            ItineraryItem(
                category = category,
                place = place1,
                title = null, // 장소 기반 일정이므로 title은 null
                time = LocalDateTime.now(),
                order = 1,
                memo = "오사카성 방문"
            )
        )
        category.itineraryItems.add(itineraryItem)
        trip.categories.add(category)
        tripRepository.save(trip)


        // 3. 기존 리뷰 데이터 생성
        savedReview = tripReviewRepository.save(TripReview
            (trip, "기존 컨셉", "## 기존 AI 피드백 내용\n ---추천 장소 목록---\n오사카 성\n도톤보리"))
    }

    private fun setAuthentication(member: Member) {
        val userDetails = CustomUserDetails(member)

        val authentication = UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities)
        SecurityContextHolder.getContext().authentication = authentication
    }

    @Nested
    @DisplayName("AI 여행 리뷰 생성 (createReview)")
    inner class CreateReviewTest {
        @Test
        @DisplayName("성공 - 추천 장소 포함")
        fun createReview_Success_WithRecommendedPlaces() {
            // given: 방장 로그인
            setAuthentication(owner)
            val request = TripReviewRequest.CreateReview("새로운 럭셔리 여행 컨셉")
            val fakeReviewContent = "## AI가 생성한 피드백입니다."
            val fakePlacePart = "오사카 성\n도톤보리"
            val fakeAiFeedback = "$fakeReviewContent\n---추천 장소 목록---\n$fakePlacePart"

            every { geminiApiClient.getFeedback(any()) } returns fakeAiFeedback

            val searchResult1 = PlaceDto.Simple.from(place1)
            val searchResult2 = PlaceDto.Simple.from(place2)

            every { googleMapService.searchPlaces("오사카 성", "ko", "JP") } returns listOf(searchResult1)
            every { googleMapService.searchPlaces("도톤보리", "ko", "JP") } returns listOf(searchResult2)

            // when: 리뷰 생성
            val reviewDetail = tripReviewService.createReview(trip.id!!, request)

            // then
            assertThat(reviewDetail.reviewId).isNotNull()
            assertThat(reviewDetail.concept).isEqualTo("새로운 럭셔리 여행 컨셉")
            assertThat(reviewDetail.content).isEqualTo(fakeReviewContent)

            val savedReview = tripReviewRepository.findTripReviewWithRecommendedPlacesById(reviewDetail.reviewId)
                ?: throw AssertionError("Review not found")
            assertThat(savedReview.trip.id).isEqualTo(trip.id)
            assertThat(savedReview.recommendedPlaces).hasSize(2)
            assertThat(savedReview.recommendedPlaces.map { it.place.name }).containsExactlyInAnyOrder("오사카 성", "도톤보리")
        }

        @Test
        @DisplayName("성공 - 추천 장소 없음")
        fun createReview_Success_WithoutRecommendedPlaces() {
            // given
            setAuthentication(owner)
            val request = TripReviewRequest.CreateReview("컨셉")
            val fakeAiFeedback = "## AI 피드백만 있습니다."
            every { geminiApiClient.getFeedback(any()) } returns fakeAiFeedback

            // when
            val reviewDetail = tripReviewService.createReview(trip.id!!, request)


            // then
            assertThat(reviewDetail.reviewId).isNotNull()
            assertThat(reviewDetail.content).isEqualTo(fakeAiFeedback)
            val savedReview = tripReviewRepository.findByIdOrNull(reviewDetail.reviewId)
            ?: throw AssertionError("Review not found")
            assertThat(savedReview.recommendedPlaces).isEmpty()
        }

        @Test
        @DisplayName("실패 - AI 피드백 생성 오류")
        fun createReview_Fail_AiFeedbackError() {
            // given
            setAuthentication(owner)
            val request = TripReviewRequest.CreateReview("컨셉")
            every { geminiApiClient.getFeedback(any()) } returns null

            // when & then
            val exception = assertThrows<BusinessException> {
                tripReviewService.createReview(trip.id!!, request)
            }
            assertThat(exception.errorCode).isEqualTo(ErrorCode.AI_FEEDBACK_ERROR)
        }
    }


    @Nested
    @DisplayName("리뷰 조회 (getReview / getAllReviews)")
    inner class GetReviewTest {
        @Test
        @DisplayName("특정 여행의 모든 리뷰 목록 조회 성공")
        fun getAllReviews_Success() {
            setAuthentication(owner)
            // given: 페이지 정보
            val pageable = PageRequest.of(0, 10)

            // when: 리뷰 목록 조회 서비스 호출
            val reviewsPage = tripReviewService.getAllReviews(trip.id!!, pageable)

            // then
            assertThat(reviewsPage.content).hasSize(1)
            assertThat(reviewsPage.content[0].title).isEqualTo("기존 AI 피드백 내용")
            assertThat(reviewsPage.content[0].reviewId).isEqualTo(savedReview.id)
            assertThat(reviewsPage.content[0].concept).isEqualTo("기존 컨셉")
        }

        @Test
        @DisplayName("리뷰 상세 조회 성공 - 추천 장소 없음")
        fun getReview_Success_NoRecommendedPlaces() {
            setAuthentication(member)

            // given
            val reviewId = savedReview.id!!

            // when
            val reviewDetail = tripReviewService.getReview(trip.id!!, reviewId )

            // then
            assertThat(reviewDetail.reviewId).isEqualTo(reviewId)
            assertThat(reviewDetail.concept).isEqualTo("기존 컨셉")
            assertThat(reviewDetail.content).isEqualTo("## 기존 AI 피드백 내용\n ---추천 장소 목록---\n오사카 성\n도톤보리")
            assertThat(reviewDetail.recommendedPlaces).isEmpty()
        }

        @Test
        @DisplayName("리뷰 상세 조회 성공 - 추천 장소 포함")
        fun getReview_Success_WithRecommendedPlaces() {
            setAuthentication(member)

            // given: 추천 장소를 포함하는 리뷰를 미리 저장
            val tripReview = tripReviewRepository.save(TripReview(trip, "장소 포함 컨셉", "내용"))
            recommendedPlaceRepository.save(RecommendedPlace.from(place1, tripReview ))
            recommendedPlaceRepository.save(RecommendedPlace.from(place2, tripReview ))

            val reviewWithPlaces = tripReview

            // when
            val reviewDetail = tripReviewService.getReview(trip.id!!, reviewWithPlaces.id!! )

            // then
            assertThat(reviewDetail.reviewId).isEqualTo(reviewWithPlaces.id)
            assertThat(reviewDetail.recommendedPlaces).hasSize(2)
            assertThat(reviewDetail.recommendedPlaces?.map { it.placeName }).containsExactlyInAnyOrder("오사카 성", "도톤보리")
        }


        @Test
        @DisplayName("리뷰 상세 조회 실패 - 존재하지 않는 리뷰 ID")
        fun getReview_Fail_When_ReviewNotFound() {
            setAuthentication(member)

            // given
            val invalidReviewId = 999L

            // when & then: TRIP_REVIEW_NOT_FOUND 에러 발생 검증
            val exception = assertThrows<BusinessException> {
                tripReviewService.getReview(trip.id!!, invalidReviewId)
            }
            assertThat(exception.errorCode).isEqualTo(ErrorCode.TRIP_REVIEW_NOT_FOUND)
        }

        @Test
        @DisplayName("리뷰 상세 조회 실패 - 다른 여행의 리뷰 ID")
        fun getReview_Fail_When_TripIdMismatch() {
            setAuthentication(member)

            // given: 다른 여행 생성
            val otherTrip = tripRepository.save(Trip("다른 여행", LocalDate.now(), LocalDate.now(), Country.USA))
            val reviewInOtherTrip = tripReviewRepository.save(TripReview(otherTrip, "컨셉", "내용"))

            // when & then
            val exception = assertThrows<BusinessException> {
                tripReviewService.getReview(trip.id!!, reviewInOtherTrip.id!!)
            }
            assertThat(exception.errorCode).isEqualTo(ErrorCode.TRIP_NOT_FOUND)
        }
    }
}
