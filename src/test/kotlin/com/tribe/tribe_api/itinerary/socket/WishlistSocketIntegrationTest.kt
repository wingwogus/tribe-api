package com.tribe.tribe_api.itinerary.socket

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.tribe.tribe_api.common.util.jwt.JwtTokenProvider
import com.tribe.tribe_api.common.util.security.CustomUserDetails
import com.tribe.tribe_api.common.util.socket.SocketDto
import com.tribe.tribe_api.itinerary.dto.WishlistDto
import com.tribe.tribe_api.itinerary.entity.Category
import com.tribe.tribe_api.itinerary.entity.Place
import com.tribe.tribe_api.itinerary.entity.WishlistItem
import com.tribe.tribe_api.itinerary.repository.CategoryRepository
import com.tribe.tribe_api.itinerary.repository.PlaceRepository
import com.tribe.tribe_api.itinerary.repository.WishlistItemRepository
import com.tribe.tribe_api.itinerary.service.WishlistService
import com.tribe.tribe_api.member.entity.Member
import com.tribe.tribe_api.member.entity.Provider
import com.tribe.tribe_api.member.entity.Role
import com.tribe.tribe_api.member.repository.MemberRepository
import com.tribe.tribe_api.trip.entity.Country
import com.tribe.tribe_api.trip.entity.Trip
import com.tribe.tribe_api.trip.entity.TripRole
import com.tribe.tribe_api.trip.repository.TripMemberRepository
import com.tribe.tribe_api.trip.repository.TripRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
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
import java.math.BigDecimal
import java.time.LocalDate
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WishlistSocketIntegrationTest {

    @LocalServerPort
    var port: Int = 0

    @Autowired lateinit var jwtTokenProvider: JwtTokenProvider
    @Autowired lateinit var wishlistService: WishlistService
    @Autowired lateinit var memberRepository: MemberRepository
    @Autowired lateinit var tripRepository: TripRepository
    @Autowired lateinit var tripMemberRepository: TripMemberRepository
    @Autowired lateinit var categoryRepository: CategoryRepository
    @Autowired lateinit var wishlistItemRepository: WishlistItemRepository
    @Autowired lateinit var placeRepository: PlaceRepository
    @Autowired lateinit var objectMapper: ObjectMapper


    lateinit var stompClient: WebSocketStompClient
    lateinit var token: String
    lateinit var member: Member
    lateinit var trip: Trip

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
        trip.addMember(member, TripRole.OWNER)
        tripRepository.save(trip)
        categoryRepository.save(Category(trip, 1, "Day 1", 1))

        // JWT 토큰 생성
        val userDetails = CustomUserDetails(member)
        val authentication = UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities)
        SecurityContextHolder.getContext().authentication = authentication
        token = jwtTokenProvider.generateToken(authentication).accessToken

        // Initialize common socket connection properties
        websocketUrl = "http://localhost:$port/ws-stomp"
        connectHeaders = StompHeaders().apply { add("Authorization", "Bearer $token") }
    }

    @AfterEach
    fun tearDown() {
        // Delete in reverse order of creation to respect foreign key constraints
        wishlistItemRepository.deleteAll()
        placeRepository.deleteAll()
        categoryRepository.deleteAll()
        tripRepository.deleteAll()
        memberRepository.deleteAll()
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
                if (future is CompletableFuture<*>) {
                    (future as CompletableFuture<Any?>).completeExceptionally(exception)
                }
            }

            override fun handleTransportError(session: StompSession, exception: Throwable) {
                println("전송 에러 발생: ${exception.message}")
                exception.printStackTrace()
                if (future is CompletableFuture<*>) {
                    (future as CompletableFuture<Any?>).completeExceptionally(exception)
                }
            }
        }
    }

    private fun createWebSocketSession(future: CompletableFuture<SocketDto.TripEditMessage>): StompSession {
        val sessionHandler = createSessionHandler(future)

        val session: StompSession = stompClient.connectAsync(
            websocketUrl,
            null,
            connectHeaders,
            sessionHandler
        ).get(5, TimeUnit.SECONDS)

        session.subscribe("/topic/trips/${trip.id}", object : StompFrameHandler {
            override fun getPayloadType(headers: StompHeaders): Type {
                return SocketDto.TripEditMessage::class.java
            }

            override fun handleFrame(headers: StompHeaders, payload: Any?) {
                future.complete(payload as SocketDto.TripEditMessage)
            }
        })
        Thread.sleep(500) // Small delay for subscription to take effect
        return session
    }

    @Test
    @DisplayName("위시리스트 추가 시 웹소켓 메시지 전송")
    fun addWishList_ShouldBroadcastMessage() {
        // given
        val future = CompletableFuture<SocketDto.TripEditMessage>()
        val session = createWebSocketSession(future)
        val request = WishlistDto.WishListAddRequest(
            externalPlaceId = "place-123",
            placeName = "테스트 장소",
            address = "테스트 주소",
            latitude = BigDecimal.valueOf(37.5),
            longitude = BigDecimal.valueOf(127.0)
        )

        // when
        wishlistService.addWishList(trip.id!!, request)

        // then
        val receivedMessage = future.get(5, TimeUnit.SECONDS)
        assertThat(receivedMessage.type).isEqualTo(SocketDto.EditType.ADD_WISHLIST)
        assertThat(receivedMessage.tripId).isEqualTo(trip.id!!)

        val responseDto = objectMapper.convertValue(receivedMessage.data, WishlistDto.WishlistItemDto::class.java)
        assertThat(responseDto.wishlistItemId).isNotNull()
        assertThat(responseDto.name).isEqualTo("테스트 장소")

        println("[SockJS] 수신된 메시지: $receivedMessage")
        session.disconnect()
    }

    @Test
    @DisplayName("위시리스트 아이템 삭제 시 웹소켓 메시지 전송")
    fun deleteWishlistItems_ShouldBroadcastMessage() {
        // given
        val tripMember = tripMemberRepository.findByTripAndMember(trip, member)!!
        val place = placeRepository.save(Place("place-to-delete", "삭제될 장소", "주소", BigDecimal.valueOf(1.0), BigDecimal.valueOf(1.0)))
        val itemToDelete = wishlistItemRepository.save(WishlistItem(trip, place, tripMember))

        val future = CompletableFuture<SocketDto.TripEditMessage>()
        val session = createWebSocketSession(future)

        // when
        wishlistService.deleteWishlistItems(trip.id!!, listOf(itemToDelete.id!!))

        // then
        val receivedMessage = future.get(5, TimeUnit.SECONDS)
        assertThat(receivedMessage.type).isEqualTo(SocketDto.EditType.DELETE_WISHLIST)
        assertThat(receivedMessage.tripId).isEqualTo(trip.id!!)

        val deletedIds = objectMapper.convertValue(receivedMessage.data, object : TypeReference<List<Long>>() {})
        assertThat(deletedIds).containsExactly(itemToDelete.id!!)

        println("[SockJS] 수신된 메시지: $receivedMessage")
        session.disconnect()
    }
}