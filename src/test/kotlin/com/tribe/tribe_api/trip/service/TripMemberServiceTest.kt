package com.tribe.tribe_api.trip.service

import com.tribe.tribe_api.common.exception.BusinessException
import com.tribe.tribe_api.common.exception.ErrorCode
import com.tribe.tribe_api.common.util.security.CustomUserDetails
import com.tribe.tribe_api.common.util.service.CloudinaryUploadService
import com.tribe.tribe_api.community.dto.CommunityPostDto
import com.tribe.tribe_api.community.repository.CommunityPostRepository
import com.tribe.tribe_api.community.service.CommunityPostService
import com.tribe.tribe_api.member.entity.Member
import com.tribe.tribe_api.member.entity.Provider
import com.tribe.tribe_api.member.entity.Role
import com.tribe.tribe_api.member.repository.MemberRepository
import com.tribe.tribe_api.trip.dto.TripMemberDto
import com.tribe.tribe_api.trip.entity.Country
import com.tribe.tribe_api.trip.entity.Trip
import com.tribe.tribe_api.trip.entity.TripRole
import com.tribe.tribe_api.trip.repository.TripMemberRepository
import com.tribe.tribe_api.trip.repository.TripRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate


@SpringBootTest
@Transactional
class TripMemberServiceTest @Autowired constructor(
    private val tripMemberService: TripMemberService,
    private val tripService: TripService,
    private val communityPostService: CommunityPostService,
    private val communityPostRepository: CommunityPostRepository,
    private val cloudinaryUploadService: CloudinaryUploadService,
    private val memberRepository: MemberRepository,
    private val tripRepository: TripRepository,
    private val tripMemberRepository: TripMemberRepository,
    private val passwordEncoder: PasswordEncoder
){
    private lateinit var owner: Member
    private lateinit var member1: Member
    private lateinit var member2 : Member
    private lateinit var adminMember : Member
    private lateinit var nonMember: Member
    private lateinit var trip: Trip

    @BeforeEach
    fun setUp() {
        // 1. 사용자 생성 (소유자, 일반 멤버, 비멤버)
        owner = memberRepository.save(Member("owner@test.com", passwordEncoder.encode("pw"), "여행소유자", null, Role.USER, Provider.LOCAL, null, false))
        member1 = memberRepository.save(Member("member@test.com", passwordEncoder.encode("pw"), "일반멤버", null, Role.USER, Provider.LOCAL, null, false))
        member2 = memberRepository.save(Member("member2@test.com", passwordEncoder.encode("pw"), "일반멤버2", null, Role.USER, Provider.LOCAL, null, false))
        adminMember = memberRepository.save(Member("admin@test.com", passwordEncoder.encode("pw"), "어드민", null, Role.USER, Provider.LOCAL, null, false))
        nonMember = memberRepository.save(Member("nonmember@test.com", passwordEncoder.encode("pw"), "비멤버", null, Role.USER, Provider.LOCAL, null, false))


        // 2. 여행 데이터 생성 (일본)
        trip = Trip("일본 여행", LocalDate.now(), LocalDate.now().plusDays(5), Country.JAPAN)
        trip.addMember(owner, TripRole.OWNER)
        trip.addMember(member1, TripRole.MEMBER)
        trip.addMember(member2, TripRole.MEMBER)
        trip.addMember(adminMember, TripRole.ADMIN)
        trip.addMember(nonMember, TripRole.GUEST)
        tripRepository.save(trip)
    }

    private fun setAuthentication(member: Member) {
        val userDetails = CustomUserDetails(member)
        val authentication = UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities)
        SecurityContextHolder.getContext().authentication = authentication
    }

    @Nested
    @DisplayName("멤버 권한 변경")
    inner class UpdateMemberRoleTest {
        @Test
        @DisplayName("Owner가 멤버의 권한 변경 - 성공 ( 일반 멤버를 어드민으로 )")
        fun 멤버권한_변경_오너_성공() {
            //given
            setAuthentication(owner)
            val tripId = trip.id
            val memberId = member1.id
            val requestRole = TripRole.ADMIN
            val requestDto = TripMemberDto.AssignRoleRequest(requestRole)
            val tripMemberId = tripMemberRepository.findByTripIdAndMemberId(tripId!!, memberId!!)?.id!!

            //when
            tripMemberService.assignRole(tripId, tripMemberId, requestDto)
            //then
            val updatedMember = tripMemberRepository.findByTripIdAndMemberId(tripId, memberId)
            assertThat(updatedMember!!.role).isEqualTo(requestRole)
        }

        @Test
        @DisplayName("Owner가 멤버의 권한 변경 - 성공 ( 어드민을 일반 멤버로 )")
        fun 멤버권한_변경_오너_성공2() {
            //given
            setAuthentication(owner)
            val tripId = trip.id
            val memberId = adminMember.id
            val requestRole = TripRole.MEMBER
            val requestDto = TripMemberDto.AssignRoleRequest(requestRole)
            val tripMemberId = tripMemberRepository.findByTripIdAndMemberId(tripId!!, memberId!!)?.id!!

            //when
            tripMemberService.assignRole(tripId, tripMemberId, requestDto)
            //then
            val updatedMember = tripMemberRepository.findByTripIdAndMemberId(tripId, memberId)
            assertThat(updatedMember!!.role).isEqualTo(requestRole)
        }

        @Test
        @DisplayName("Owner가 멤버의 권한 변경 - 실패 ( owner가 owner의 권한 변경 )")
        fun 멤버권한_변경_오너_실패() {
            //given
            setAuthentication(owner)
            val tripId = trip.id
            val memberId = owner.id
            val requestRole = TripRole.ADMIN
            val requestDto = TripMemberDto.AssignRoleRequest(requestRole)
            val tripMemberId = tripMemberRepository.findByTripIdAndMemberId(tripId!!, memberId!!)?.id!!


            //when & then
            val exception = assertThrows<BusinessException> {
                tripMemberService.assignRole(tripId, tripMemberId, requestDto)
            }
            assertThat(exception.errorCode).isEqualTo(ErrorCode.CANNOT_CHANGE_OWN_ROLE)
        }

        @Test
        @DisplayName("Member가 멤버의 권한 변경")
        fun 멤버권한_변경_멤버() {
            //given
            setAuthentication(member2)
            val tripId = trip.id
            val memberId = member1.id
            val requestRole = TripRole.ADMIN
            val requestDto = TripMemberDto.AssignRoleRequest(requestRole)
            val tripMemberId = tripMemberRepository.findByTripIdAndMemberId(tripId!!, memberId!!)?.id!!

            //when & then

            val exception = assertThrows<BusinessException> {
                tripMemberService.assignRole(tripId, tripMemberId, requestDto)
            }

            assertThat(exception.errorCode).isEqualTo(ErrorCode.NO_AUTHORITY_TRIP)

            val updatedMember = tripMemberRepository.findByTripIdAndMemberId(tripId, memberId)
            assertThat(updatedMember!!.role).isNotEqualTo(requestRole)
            assertThat(updatedMember.role).isEqualTo(TripRole.MEMBER)
        }

        @Test
        @DisplayName("Guest가 멤버의 권한 변경")
        fun 멤버권한_변경_게스트() {
            //given
            setAuthentication(nonMember)
            val tripId = trip.id
            val memberId = member1.id
            val requestRole = TripRole.ADMIN
            val requestDto = TripMemberDto.AssignRoleRequest(requestRole)
            val tripMemberId = tripMemberRepository.findByTripIdAndMemberId(tripId!!, memberId!!)?.id!!

            //when & then
            val exception = assertThrows<BusinessException> {
                tripMemberService.assignRole(tripId, tripMemberId,requestDto)
            }

            assertThat(exception.errorCode).isEqualTo(ErrorCode.NO_AUTHORITY_TRIP)
        }

        @Test
        @DisplayName("Admin이 멤버의 권한 변경")
        fun 멤버권한_변경_어드민() {
            //given
            setAuthentication(adminMember)
            val tripId = trip.id
            val memberId = member1.id
            val requestRole = TripRole.ADMIN
            val requestDto = TripMemberDto.AssignRoleRequest(requestRole)
            val tripMemberId = tripMemberRepository.findByTripIdAndMemberId(tripId!!, memberId!!)?.id!!

            //when & then
            val exception = assertThrows<BusinessException> {
                tripMemberService.assignRole(tripId, tripMemberId, requestDto)
            }

            assertThat(exception.errorCode).isEqualTo(ErrorCode.NO_AUTHORITY_TRIP)
        }
    }

    @Nested
    @DisplayName("멤버 초대")
    inner class InviteMember{
        @Test
        @DisplayName("Owner가 다른 멤버를 초대")
        fun 멤버_초대_오너() {
            //given
            setAuthentication(owner)
            val tripId = trip.id!!
            //when
            val invitation = tripService.createInvitation(tripId)
            //then
            assertThat(invitation).isNotNull()
        }
        @Test
        @DisplayName("Admin이 다른 멤버를 초대")
        fun 멤버_초대_어드민() {
            //given
            setAuthentication(adminMember)
            val tripId = trip.id!!
            //when
            val invitation = tripService.createInvitation(tripId)
            //then
            assertThat(invitation).isNotNull()
        }
        @Test
        @DisplayName("Member가 다른 멤버를 초대")
        fun 멤버_초대_멤버() {
            //given
            setAuthentication(member1)
            val tripId = trip.id!!
            // when & then
            val exception = assertThrows<BusinessException> {
                tripService.createInvitation(tripId)
            }
            assertThat(exception.errorCode).isEqualTo(ErrorCode.NO_AUTHORITY_TRIP)
        }
        @Test
        @DisplayName("Guest가 다른 멤버를 초대")
        fun 멤버_초대_게스트() {
            //given
            setAuthentication(nonMember)
            val tripId = trip.id!!
            // when & then
            val exception = assertThrows<BusinessException> {
                tripService.createInvitation(tripId)
            }
            assertThat(exception.errorCode).isEqualTo(ErrorCode.NO_AUTHORITY_TRIP)
        }
    }

    @Nested
    @DisplayName("여행 공유 생성")
    inner class CreatePost{
        @Test
        @DisplayName("OWNER가 여행 공유 생성")
        fun 여행공유_생성_오너() {
            //given
            setAuthentication(owner)
            val tripId = trip.id!!
            val title = "레전드 일본 여행"
            val content = "시부야 사변 한번 훑어주고 신주쿠가서 장어먹고 산책 좀 하다가 레전드 마트가서 와규 산다음에 숙소가서 양주까고 먹었습니다."
            val imageFile = null
            val createPost = CommunityPostDto.CreateRequest(tripId, title, content)
            //when
            val response = communityPostService.createPost(tripId, createPost,imageFile)
            //then
            assertThat(response.trip.tripId).isEqualTo(tripId)
            assertThat(response.title).isEqualTo(title)
            assertThat(response.content).isEqualTo(content)
            assertThat(response.authorNickname).isEqualTo(owner.nickname)
            assertThat(response.representativeImageUrl).isNull()
            assertThat(response.postId).isNotNull()
            assertThat(response.country).isEqualTo("일본")
        }
        @Test
        @DisplayName("ADMIN이 여행 공유 생성")
        fun 여행공유_생성_어드민() {
            //given
            setAuthentication(adminMember)
            val tripId = trip.id!!
            val title = "레전드 일본 여행"
            val content = "시부야 사변 한번 훑어주고 신주쿠가서 장어먹고 산책 좀 하다가 레전드 마트가서 와규 산다음에 숙소가서 양주까고 먹었습니다."
            val imageFile = null
            val createPost = CommunityPostDto.CreateRequest(tripId, title, content)
            //when
            val response = communityPostService.createPost(tripId, createPost,imageFile)
            //then
            assertThat(response.trip.tripId).isEqualTo(tripId)
            assertThat(response.title).isEqualTo(title)
            assertThat(response.content).isEqualTo(content)
            assertThat(response.authorNickname).isEqualTo(adminMember.nickname)
            assertThat(response.representativeImageUrl).isNull()
            assertThat(response.postId).isNotNull()
            assertThat(response.country).isEqualTo("일본")
        }
        @Test
        @DisplayName("MEMBER가 여행 공유 생성")
        fun 여행공유_생성_멤버() {
            //given
            setAuthentication(member1)
            val tripId = trip.id!!
            // when & then
            val exception = assertThrows<BusinessException> {
                communityPostService.createPost(tripId, CommunityPostDto.CreateRequest(tripId, "title", "content"), null)
            }
            assertThat(exception.errorCode).isEqualTo(ErrorCode.NO_AUTHORITY_TRIP)
        }
        @Test
        @DisplayName("GUEST가 여행 공유 생성")
        fun 여행공유_생성_게스트() {
            //given
            setAuthentication(nonMember)
            val tripId = trip.id!!
            // when & then
            val exception = assertThrows<BusinessException> {
                communityPostService.createPost(tripId, CommunityPostDto.CreateRequest(tripId, "title", "content"), null)
            }
            assertThat(exception.errorCode).isEqualTo(ErrorCode.NO_AUTHORITY_TRIP)
        }
    }
}