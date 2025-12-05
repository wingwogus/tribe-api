package com.tribe.tribe_api.chat.service

import com.tribe.tribe_api.chat.repository.ChatMessageRepository
import com.tribe.tribe_api.common.exception.BusinessException
import com.tribe.tribe_api.common.exception.ErrorCode
import com.tribe.tribe_api.common.util.security.CustomUserDetails
import com.tribe.tribe_api.member.entity.Member
import com.tribe.tribe_api.member.entity.Provider
import com.tribe.tribe_api.member.entity.Role
import com.tribe.tribe_api.member.repository.MemberRepository
import com.tribe.tribe_api.trip.entity.Country
import com.tribe.tribe_api.trip.entity.Trip
import com.tribe.tribe_api.trip.entity.TripRole
import com.tribe.tribe_api.trip.repository.TripRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@SpringBootTest
@Transactional
class ChatServiceTest @Autowired constructor(
    private val chatService: ChatService,
    private val tripRepository: TripRepository,
    private val memberRepository: MemberRepository,
    private val passwordEncoder: PasswordEncoder,
    private val chatMessageRepository: ChatMessageRepository,
) {
    private lateinit var owner: Member
    private lateinit var member: Member
    private lateinit var nonMember: Member
    private lateinit var trip: Trip

    @BeforeEach
    fun setUp() {
        owner = memberRepository.save(
            Member(
                email = "owner@test.com",
                password = passwordEncoder.encode("pw"),
                nickname = "방장",
                provider = Provider.LOCAL,
                role = Role.USER,
                isFirstLogin = false
            )
        )
        member = memberRepository.save(
            Member(
                email = "member@test.com",
                password = passwordEncoder.encode("pw"),
                nickname = "멤버",
                provider = Provider.LOCAL,
                role = Role.USER,
                isFirstLogin = false
            )
        )
        nonMember = memberRepository.save(
            Member(
                email = "nonmember@test.com",
                password = passwordEncoder.encode("pw"),
                nickname = "외부인",
                provider = Provider.LOCAL,
                role = Role.USER,
                isFirstLogin = false
            )
        )

        trip = tripRepository.save(
            Trip(
                title = "테스트 여행",
                startDate = LocalDate.now(),
                endDate = LocalDate.now().plusDays(5),
                country = Country.JAPAN
            )
        )

        trip.addMember(owner, TripRole.OWNER)
        trip.addMember(member, TripRole.MEMBER)
    }

    private fun setAuthentication(member: Member) {
        val userDetails = CustomUserDetails(member)
        val authentication = UsernamePasswordAuthenticationToken(
            userDetails, null, userDetails.authorities
        )
        SecurityContextHolder.getContext().authentication = authentication
    }

    @Test
    @DisplayName("채팅 전송 성공")
    fun sendChat_Success() {
        // given
        setAuthentication(member)
        val content = "안녕하세요!"

        // when
        val chatResponse = chatService.sendChat(trip.id!!, content)

        // then
        assertThat(chatResponse.content).isEqualTo(content)
        assertThat(chatResponse.sender.nickname).isEqualTo(member.nickname)

        val messages = chatMessageRepository.findAll()
        assertThat(messages).hasSize(1)
        assertThat(messages[0].content).isEqualTo(content)
    }

    @Test
    @DisplayName("채팅 전송 실패 - 멤버가 아닌 경우")
    fun sendChat_Fail_When_NotAMember() {
        // given
        setAuthentication(nonMember)
        val content = "안녕하세요!"

        // when & then
        val exception = assertThrows<BusinessException> {
            chatService.sendChat(trip.id!!, content)
        }

        assertThat(exception.errorCode).isEqualTo(ErrorCode.NOT_A_TRIP_MEMBER)
    }

    @Test
    @DisplayName("채팅 기록 조회 성공")
    fun getChatHistory_Success() {
        // given
        setAuthentication(member)
        chatService.sendChat(trip.id!!, "첫번째 메시지")
        chatService.sendChat(trip.id!!, "두번째 메시지")


        // when
        val chatHistory = chatService.getChatHistory(trip.id!!, null, 10)

        // then
        assertThat(chatHistory.content).hasSize(2)
        assertThat(chatHistory.content[0].content).isEqualTo("두번째 메시지")
        assertThat(chatHistory.content[1].content).isEqualTo("첫번째 메시지")
    }

    @Test
    @DisplayName("채팅 기록 조회 실패 - 멤버가 아닌 경우")
    fun getChatHistory_Fail_When_NotAMember() {
        // given
        setAuthentication(nonMember)

        // when & then
        val exception = assertThrows<BusinessException> {
            chatService.getChatHistory(trip.id!!, null, 10)
        }

        assertThat(exception.errorCode).isEqualTo(ErrorCode.NOT_A_TRIP_MEMBER)
    }
}