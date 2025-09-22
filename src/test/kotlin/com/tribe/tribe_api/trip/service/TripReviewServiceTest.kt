package com.tribe.tribe_api.trip.service

import com.tribe.tribe_api.common.exception.BusinessException
import com.tribe.tribe_api.common.exception.ErrorCode
import com.tribe.tribe_api.common.util.security.CustomUserDetails
import com.tribe.tribe_api.common.util.service.GeminiApiClient
import com.tribe.tribe_api.itinerary.entity.Category
import com.tribe.tribe_api.itinerary.entity.ItineraryItem
import com.tribe.tribe_api.itinerary.entity.Place
import com.tribe.tribe_api.itinerary.repository.PlaceRepository
import com.tribe.tribe_api.member.entity.Member
import com.tribe.tribe_api.member.entity.Provider
import com.tribe.tribe_api.member.entity.Role
import com.tribe.tribe_api.member.repository.MemberRepository
import com.tribe.tribe_api.trip.dto.TripReviewRequest
import com.tribe.tribe_api.trip.entity.*
import com.tribe.tribe_api.trip.repository.TripRepository
import com.tribe.tribe_api.trip.repository.TripReviewRepository
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate

@SpringBootTest
@Transactional
@ExtendWith(MockKExtension::class)
class TripReviewServiceIntegrationTest @Autowired constructor(
    private val tripRepository: TripRepository,
    private val tripReviewRepository: TripReviewRepository,
    private val memberRepository: MemberRepository,
    private val passwordEncoder: PasswordEncoder,
) {
    @Autowired
    private lateinit var placeRepository: PlaceRepository

    // Mock 객체 주입
    @MockK
    private lateinit var geminiApiClient: GeminiApiClient
    private lateinit var tripReviewService: TripReviewService


    private lateinit var owner: Member
    private lateinit var member: Member
    private lateinit var trip: Trip
    private lateinit var savedReview: TripReview

    @BeforeEach
    fun setUp() {
        tripReviewService = TripReviewService(
            tripRepository,
            tripReviewRepository,
            geminiApiClient
        )
        // 1. 사용자 생성
        owner = memberRepository.save(
            Member(
                "owner@test.com",
                passwordEncoder.encode("pw"),
                "방장",
                null,
                Role.USER,
                Provider.LOCAL,
                null,
                false))
        member = memberRepository.save(
            Member(
                "member@test.com",
                passwordEncoder.encode("pw"),
                "멤버",
                null,
                Role.USER,
                Provider.LOCAL,
                null,
                false))

        // 2. 여행 데이터 생성
        trip = Trip(
            "테스트 여행",
            LocalDate.now(),
            LocalDate.now().plusDays(5),
            Country.JAPAN)

        trip.addMember(owner, TripRole.OWNER)
        trip.addMember(member, TripRole.MEMBER)

        val category = Category(trip, 1, "Day 1",1)
        val place = Place(
            "id",
            "오사카성",
            "일본 오사카",
            BigDecimal.valueOf(34.6873),
            BigDecimal.valueOf(34.6873))

        placeRepository.save(place)

        val itineraryItem = ItineraryItem(category, place, 1, "오사카성 방문")
        category.itineraryItems.add(itineraryItem)
        trip.categories.add(category)

        tripRepository.save(trip)

        // 3. 기존 리뷰 데이터 생성
        savedReview = tripReviewRepository.save(
            TripReview(trip, "기존 컨셉", "## 기존 AI 피드백 내용\n"))
    }

    private fun setAuthentication(member: Member) {
        val userDetails = CustomUserDetails(member)
        val authentication = UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities)
        SecurityContextHolder.getContext().authentication = authentication
    }

    @Test
    @DisplayName("AI 여행 리뷰 생성 성공")
    fun createReview_Success() {
        // given: 방장 로그인
        setAuthentication(owner)
        val request = TripReviewRequest.CreateReview("새로운 럭셔리 여행 컨셉")
        val fakeAiFeedback = "## AI가 생성한 피드백입니다.\n"

        every {geminiApiClient.getFeedback(any())}.returns(fakeAiFeedback)

        // when: 리뷰 생성
        val reviewDetail = tripReviewService.createReview(trip.id!!, request)

        // then
        assertThat(reviewDetail.reviewId).isNotNull()
        assertThat(reviewDetail.concept).isEqualTo("새로운 럭셔리 여행 컨셉")
        assertThat(reviewDetail.content).isEqualTo(fakeAiFeedback)

        val foundReview = tripReviewRepository.findById(reviewDetail.reviewId).get()
        assertThat(foundReview.trip.id).isEqualTo(trip.id)
    }

    @Test
    @DisplayName("AI 여행 리뷰 생성 실패 - 방장이 아닌 경우")
    fun createReview_Fail_When_UserIsNotOwner() {
        // given: '멤버'가 로그인하고, 리뷰 생성을 요청
        setAuthentication(member)
        val request = TripReviewRequest.CreateReview("새로운 럭셔리 여행 컨셉")

        // when & then: NO_AUTHORITY_TRIP 에러가 발생하는지 검증
        val exception = assertThrows<BusinessException> {
            tripReviewService.createReview(trip.id!!, request)
        }
        assertThat(exception.errorCode).isEqualTo(ErrorCode.NO_AUTHORITY_TRIP)
    }

    @Test
    @DisplayName("특정 여행의 모든 리뷰 목록 조회 성공")
    fun getAllReviews_Success() {
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
    @DisplayName("리뷰 상세 조회 성공")
    fun getReview_Success() {
        // given
        val reviewId = savedReview.id!!

        // when
        val reviewDetail = tripReviewService.getReview(trip.id!!, reviewId )

        // then
        assertThat(reviewDetail.reviewId).isEqualTo(reviewId)
        assertThat(reviewDetail.concept).isEqualTo("기존 컨셉")
        assertThat(reviewDetail.content).isEqualTo("## 기존 AI 피드백 내용\n")
    }

    @Test
    @DisplayName("리뷰 상세 조회 실패 - 존재하지 않는 리뷰 ID")
    fun getReview_Fail_When_ReviewNotFound() {
        // given
        val invalidReviewId = 999L

        // when & then: TRIP_REVIEW_NOT_FOUND 에러 발생 검증
        val exception = assertThrows<BusinessException> {
            tripReviewService.getReview(trip.id!!, invalidReviewId)
        }
        assertThat(exception.errorCode).isEqualTo(ErrorCode.TRIP_REVIEW_NOT_FOUND)
    }
}