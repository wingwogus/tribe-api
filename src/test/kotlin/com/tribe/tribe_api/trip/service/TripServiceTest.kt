package com.tribe.tribe_api.trip.service

import com.tribe.tribe_api.common.exception.BusinessException
import com.tribe.tribe_api.common.exception.ErrorCode
import com.tribe.tribe_api.common.util.security.CustomUserDetails
import com.tribe.tribe_api.common.util.service.RedisService
import com.tribe.tribe_api.member.entity.Member
import com.tribe.tribe_api.member.entity.Provider
import com.tribe.tribe_api.member.entity.Role
import com.tribe.tribe_api.member.repository.MemberRepository
import com.tribe.tribe_api.trip.dto.TripRequest
import com.tribe.tribe_api.trip.entity.Country
import com.tribe.tribe_api.trip.entity.Trip
import com.tribe.tribe_api.trip.entity.TripRole
import com.tribe.tribe_api.trip.repository.TripRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDate

@SpringBootTest
@Transactional
class TripServiceIntegrationTest @Autowired constructor(
    private val tripService: TripService,
    private val tripRepository: TripRepository,
    private val memberRepository: MemberRepository,
    private val passwordEncoder: PasswordEncoder,
    private val redisService: RedisService,
) {
    // 테스트에 사용할 사용자 및 여행 객체
    private lateinit var owner: Member
    private lateinit var member: Member
    private lateinit var nonMember: Member
    private lateinit var trip: Trip

    @BeforeEach
    fun setUp() {
        owner = memberRepository.save(Member(
            email = "owner@test.com",
            password = passwordEncoder.encode("pw"),
            nickname = "방장",
            provider = Provider.LOCAL,
            role = Role.USER,
            isFirstLogin = false))
        member = memberRepository.save(Member(
            email = "member@test.com",
            password = passwordEncoder.encode("pw"),
            nickname = "멤버",
            provider = Provider.LOCAL,
            role = Role.USER,
            isFirstLogin = false))
        nonMember = memberRepository.save(Member(
            email = "nonmember@test.com",
            password = passwordEncoder.encode("pw"),
            nickname = "외부인",
            provider = Provider.LOCAL,
            role = Role.USER,
            isFirstLogin = false))

        trip = tripRepository.save(Trip(
            title = "테스트 여행",
            startDate = LocalDate.now(),
            endDate = LocalDate.now().plusDays(5),
            country = Country.JAPAN))

        trip.addMember(owner, TripRole.OWNER)
        trip.addMember(member, TripRole.MEMBER)

    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    private fun setAuthentication(member: Member) {
        val userDetails = CustomUserDetails(member)
        val authentication = UsernamePasswordAuthenticationToken(
            userDetails, null, userDetails.authorities
        )
        SecurityContextHolder.getContext().authentication = authentication
    }

    @Test
    @DisplayName("여행 생성 성공")
    fun createTrip_Success() {
        // given: '외부인'이 로그인을 했다고 가정
        setAuthentication(nonMember)

        val request = TripRequest.Create(
            "새로운 여행",
            LocalDate.now(),
            LocalDate.now().plusDays(2),
            Country.SOUTH_KOREA)

        // when: 여행 생성 서비스 호출
        val createdTripDetail = tripService.createTrip(request)

        // then: 결과 검증
        assertThat(createdTripDetail.title).isEqualTo("새로운 여행")
        assertThat(createdTripDetail.members).hasSize(1)
        assertThat(createdTripDetail.members[0].nickname).isEqualTo("외부인")
        assertThat(createdTripDetail.members[0].role).isEqualTo(TripRole.OWNER)
    }

    @Test
    @DisplayName("여행 상세 조회 성공 - 멤버인 경우")
    fun getTripDetails_Success_When_UserIsMember() {
        // given: '멤버'가 로그인을 했다고 가정
        setAuthentication(member)
        // when: 여행 상세 정보 조회
        val tripDetail = tripService.getTripDetails(trip.id!!)

        // then: 결과 검증
        assertThat(tripDetail.tripId).isEqualTo(trip.id!!)
        assertThat(tripDetail.members.any { it.nickname == "방장" }).isTrue
        assertThat(tripDetail.members.any { it.nickname == "멤버" }).isTrue
    }

//    @Test
//    @DisplayName("여행 상세 조회 실패 - 멤버가 아닌 경우")
//    fun `getTripDetails_Fail_When_UserIsNotMember`() {
//        // given: '외부인'이 로그인을 했다고 가정
//        setAuthentication(nonMember)
//
//        // when & then: NO_AUTHORITY_TRIP 에러가 발생하는지 검증
//        assertThrows<BusinessException> {
//            tripService.getTripDetails(trip.id!!)
//        }
//    }

    @Test
    @DisplayName("여행 수정 성공")
    fun updateTrip_Success() {
        // given: 주인이 로그인
        setAuthentication(owner)

        // when: 여행 수정
        val request = TripRequest.Update(
            "수정된 여행",
            LocalDate.now(),
            LocalDate.now().plusDays(2),
            Country.SOUTH_KOREA
        )
        val updateTrip = tripService.updateTrip(trip.id!!, request)

        // then: 검증
        assertThat(updateTrip.title).isEqualTo("수정된 여행")
        assertThat(updateTrip.startDate).isEqualTo(LocalDate.now())
        assertThat(updateTrip.endDate).isEqualTo(LocalDate.now().plusDays(2))
        assertThat(updateTrip.country).isEqualTo(Country.SOUTH_KOREA.koreanName)
    }

    @Test
    @DisplayName("여행 수정 실패 - 주인이 아닌 경우")
    fun updateTrip_Failed_When_UserIsMember() {
        // given
        setAuthentication(member)

        val request = TripRequest.Update(
            "수정된 여행",
            LocalDate.now(),
            LocalDate.now().plusDays(2),
            Country.SOUTH_KOREA
        )

        // when & then: NO_AUTHORITY_TRIP 에러가 발생하는지 검증
        val exception = assertThrows<BusinessException> {
            tripService.updateTrip(trip.id!!, request)
        }

        assertThat(exception.errorCode).isEqualTo(ErrorCode.NO_AUTHORITY_TRIP)
    }

    @Test
    @DisplayName("내 여행 목록 조회")
    fun getAllTrips_Success() {
        // given: '방장'이 로그인을 했다고 가정
        setAuthentication(owner)
        val pageable = PageRequest.of(0, 10)

        // when: 내 여행 목록 조회
        val myTrips = tripService.getAllTrips(pageable)

        // then: 결과 검증
        assertThat(myTrips.content).hasSize(1)
        assertThat(myTrips.content[0].title).isEqualTo("테스트 여행")
    }

    @Test
    @DisplayName("초대 수락 성공")
    fun joinTrip_Success() {
        // given: '외부인'이 로그인을 했다고 가정
        setAuthentication(nonMember)

        // 초대 토큰을 Redis에 미리 저장
        val token = "valid-token-123"
        redisService.setValues("INVITE:$token", trip.id!!.toString(), Duration.ofMinutes(10))
        val request = TripRequest.Join(token)

        // when: 초대 수락 서비스 호출
        tripService.joinTrip(request)

        // then: '외부인'이 멤버로 추가되었는지 확인
        val updatedTrip = tripRepository.findTripWithMembersById(trip.id!!)
        assertThat(updatedTrip?.members).hasSize(3)
        assertThat(updatedTrip?.members?.any { it.member?.nickname == "외부인" }).isTrue
    }

    @Test
    @DisplayName("초대 수락 실패 - 이미 참여한 멤버인 경우")
    fun joinTrip_Fail_When_AlreadyMember() {
        // given: 이미 멤버인 '멤버'가 로그인을 했다고 가정
        setAuthentication(member)

        val token = "valid-token-123"
        redisService.setValues("INVITE:$token", trip.id!!.toString(), Duration.ofMinutes(10))
        val request = TripRequest.Join(token)

        // when & then: ALREADY_JOINED_TRIP 에러가 발생하는지 검증
        val exception = assertThrows<BusinessException> {
            tripService.joinTrip(request)
        }

        assertThat(exception.errorCode).isEqualTo(ErrorCode.ALREADY_JOINED_TRIP)
    }
}