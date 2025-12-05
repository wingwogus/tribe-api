package com.tribe.tribe_api.chat.socket

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.tribe.tribe_api.chat.dto.ChatMessageDto
import com.tribe.tribe_api.chat.repository.ChatMessageRepository
import com.tribe.tribe_api.chat.service.ChatService
import com.tribe.tribe_api.common.util.jwt.JwtTokenProvider
import com.tribe.tribe_api.common.util.security.CustomUserDetails
import com.tribe.tribe_api.common.util.socket.SocketDto
import com.tribe.tribe_api.itinerary.entity.Category
import com.tribe.tribe_api.member.entity.Member
import com.tribe.tribe_api.member.entity.Provider
import com.tribe.tribe_api.member.entity.Role
import com.tribe.tribe_api.member.repository.MemberRepository
import com.tribe.tribe_api.trip.entity.Country
import com.tribe.tribe_api.trip.entity.Trip
import com.tribe.tribe_api.trip.entity.TripMember
import com.tribe.tribe_api.trip.entity.TripRole
import com.tribe.tribe_api.trip.repository.TripMemberRepository
import com.tribe.tribe_api.trip.repository.TripRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.messaging.converter.MappingJackson2MessageConverter
import org.springframework.messaging.simp.stomp.*
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.messaging.WebSocketStompClient
import org.springframework.web.socket.sockjs.client.SockJsClient
import org.springframework.web.socket.sockjs.client.Transport
import org.springframework.web.socket.sockjs.client.WebSocketTransport
import java.lang.reflect.Type
import java.time.LocalDate
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatSocketIntegrationTest {
    @LocalServerPort
    var port: Int = 0

    @Autowired lateinit var jwtTokenProvider: JwtTokenProvider
    @Autowired lateinit var memberRepository: MemberRepository
    @Autowired lateinit var tripRepository: TripRepository
    @Autowired lateinit var chatService: ChatService
    @Autowired lateinit var tripMemberRepository: TripMemberRepository
    @Autowired lateinit var chatRepository: ChatMessageRepository
    @Autowired lateinit var objectMapper: ObjectMapper


    lateinit var stompClient: WebSocketStompClient
    lateinit var token: String
    lateinit var member: Member
    lateinit var tripMember: TripMember
    lateinit var trip: Trip
    lateinit var category: Category

    private lateinit var websocketUrl: String
    private lateinit var connectHeaders: StompHeaders

    @BeforeEach
    fun setup() {
        val transports: List<Transport> = listOf(WebSocketTransport(StandardWebSocketClient()))
        val sockJsClient = SockJsClient(transports)

        stompClient = WebSocketStompClient(sockJsClient)
        stompClient.messageConverter = MappingJackson2MessageConverter().apply {
            this.objectMapper.registerKotlinModule()
        }

        // 데이터 초기화
        member = memberRepository.save(Member("test@ws.com", "pw", "테스터", null, Role.USER, Provider.LOCAL, null, false))
        trip = tripRepository.save(Trip("소켓 테스트 여행", LocalDate.now(), LocalDate.now().plusDays(3), Country.JAPAN))
        tripMember = tripMemberRepository.save(trip.addMember(member, TripRole.OWNER))
        tripRepository.save(trip)

        // JWT 토큰 생성
        val userDetails = CustomUserDetails(member)
        val authentication = UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities)
        SecurityContextHolder.getContext().authentication = authentication
        token = jwtTokenProvider.generateToken(authentication).accessToken

        // Initialize common socket connection properties
        websocketUrl = "http://localhost:$port/ws-stomp"
        connectHeaders = StompHeaders().apply { add("Authorization", "Bearer $token") }
    }

    private fun createSessionHandler(future: CompletableFuture<*>): StompSessionHandlerAdapter {
        return object : StompSessionHandlerAdapter() {
            override fun handleException(
                session: StompSession,
                command: StompCommand?,
                headers: StompHeaders,
                payload: ByteArray,
                exception: Throwable
            ) {
                println("[STOMP] 예외 발생: ${exception.message}")
                exception.printStackTrace()
                future.completeExceptionally(exception)

            }

            override fun handleTransportError(session: StompSession, exception: Throwable) {
                println("전송 에러 발생: ${exception.message}")
                exception.printStackTrace()
                future.completeExceptionally(exception)
            }
        }
    }

    private fun createWebSocketSession(future: CompletableFuture<SocketDto.TripEvent>): StompSession {
        val sessionHandler = createSessionHandler(future)

        val session: StompSession = stompClient.connectAsync(
            websocketUrl,
            null,
            connectHeaders,
            sessionHandler
        ).get(5, TimeUnit.SECONDS)

        session.subscribe("/topic/trips/${trip.id}", object : StompFrameHandler {
            override fun getPayloadType(headers: StompHeaders): Type {
                return SocketDto.TripEvent::class.java
            }

            override fun handleFrame(headers: StompHeaders, payload: Any?) {
                future.complete(payload as SocketDto.TripEvent)
            }
        })
        Thread.sleep(500) // Small delay for subscription to take effect
        return session
    }

    @Test
    @DisplayName("채팅 전송 시 웹소켓 메시지 전송")
    fun sendChat_ShouldBroadcastMessage() {
        // given
        val future = CompletableFuture<SocketDto.TripEvent>()
        val session = createWebSocketSession(future)
        val content = "안녕하세요~"

        // when
        chatService.sendChat(trip.id!!, content)

        // then
        val receivedMessage = future.get(5, TimeUnit.SECONDS)

        assertThat(receivedMessage).isNotNull
        assertThat(receivedMessage.type).isEqualTo(SocketDto.EditType.CHAT)
        assertThat(receivedMessage.tripId).isEqualTo(trip.id)

        val responseDto = objectMapper.convertValue(receivedMessage.data, ChatMessageDto.Response::class.java)

        assertThat(responseDto.messageId).isNotNull
        assertThat(responseDto.sender.memberId).isEqualTo(member.id)
        assertThat(responseDto.sender.tripMemberId).isEqualTo(tripMember.id)
        assertThat(responseDto.sender.nickname).isEqualTo(tripMember.name)
        assertThat(responseDto.sender.avatar).isEqualTo(member.avatar)
        assertThat(responseDto.content).isEqualTo(content)

        println("[SockJS] 수신된 메시지: $receivedMessage")
        session.disconnect()
    }
}