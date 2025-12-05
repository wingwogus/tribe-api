package com.tribe.tribe_api.itinerary.socket

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.tribe.tribe_api.common.util.jwt.JwtTokenProvider
import com.tribe.tribe_api.common.util.security.CustomUserDetails
import com.tribe.tribe_api.common.util.socket.SocketDto
import com.tribe.tribe_api.itinerary.dto.ItineraryRequest
import com.tribe.tribe_api.itinerary.dto.ItineraryResponse
import com.tribe.tribe_api.itinerary.entity.Category
import com.tribe.tribe_api.itinerary.entity.ItineraryItem
import com.tribe.tribe_api.itinerary.repository.CategoryRepository
import com.tribe.tribe_api.itinerary.repository.ItineraryItemRepository
import com.tribe.tribe_api.itinerary.service.ItineraryService
import com.tribe.tribe_api.member.entity.Member
import com.tribe.tribe_api.member.entity.Provider
import com.tribe.tribe_api.member.entity.Role
import com.tribe.tribe_api.member.repository.MemberRepository
import com.tribe.tribe_api.trip.entity.Country
import com.tribe.tribe_api.trip.entity.Trip
import com.tribe.tribe_api.trip.entity.TripRole
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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ItinerarySocketIntegrationTest {

    @LocalServerPort
    var port: Int = 0

    @Autowired lateinit var jwtTokenProvider: JwtTokenProvider
    @Autowired lateinit var itineraryService: ItineraryService
    @Autowired lateinit var memberRepository: MemberRepository
    @Autowired lateinit var tripRepository: TripRepository
    @Autowired lateinit var categoryRepository: CategoryRepository
    @Autowired lateinit var itineraryItemRepository: ItineraryItemRepository
    @Autowired lateinit var objectMapper: ObjectMapper


    lateinit var stompClient: WebSocketStompClient
    lateinit var token: String
    lateinit var member: Member
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
        trip.addMember(member, TripRole.OWNER)
        tripRepository.save(trip)
        category = categoryRepository.save(Category(trip, 1, "Day 1", 1))

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
        itineraryItemRepository.deleteAll()
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
    @DisplayName("일정 생성 시 SockJS로 웹소켓 메시지 전송")
    fun createItinerary_ShouldBroadcastMessage() {
        // given
        val future = CompletableFuture<SocketDto.TripEvent>()
        val session = createWebSocketSession(future)

        // when
        val request = ItineraryRequest.Create(
            placeId = null,
            title = "SockJS 테스트 일정",
            time = LocalDateTime.now(),
            memo = "테스트 메모"
        )
        itineraryService.createItinerary(category.id!!, request)

        // then
        val receivedMessage = future.get(5, TimeUnit.SECONDS)

        assertThat(receivedMessage).isNotNull
        assertThat(receivedMessage.type).isEqualTo(SocketDto.EditType.ADD_ITINERARY)
        assertThat(receivedMessage.tripId).isEqualTo(trip.id)

        val detailDto = objectMapper.convertValue(receivedMessage.data, ItineraryResponse.ItineraryDetail::class.java)

        assertThat(detailDto.itineraryId).isNotNull
        assertThat(detailDto.name).isEqualTo("SockJS 테스트 일정")
        assertThat(detailDto.memo).isEqualTo("테스트 메모")
        assertThat(detailDto.categoryId).isEqualTo(category.id!!)
        assertThat(detailDto.order).isEqualTo(1)

        println("[SockJS] 수신된 메시지: $receivedMessage")
        session.disconnect()
    }

    @Test
    @DisplayName("일정 수정 시 웹소켓 메시지 브로드캐스트")
    fun updateItinerary_ShouldBroadcastMessage() {
        // given
        val item = itineraryItemRepository.save(
            ItineraryItem(
                category = category,
                place = null,
                title = "수정 전 일정",
                time = LocalDateTime.of(LocalDate.now(), LocalTime.of(10, 0)),
                order = 1,
                memo = "수정 전 메모"
            )
        )

        val future = CompletableFuture<SocketDto.TripEvent>()
        val session = createWebSocketSession(future)

        // when
        val updateRequest = ItineraryRequest.Update(
            time = LocalDateTime.of(LocalDate.now(), LocalTime.of(12, 0)),
            memo = "수정 후 메모"
        )
        itineraryService.updateItinerary(item.id!!, updateRequest)

        // then
        val receivedMessage = future.get(5, TimeUnit.SECONDS)
        assertThat(receivedMessage).isNotNull
        assertThat(receivedMessage.type).isEqualTo(SocketDto.EditType.EDIT_ITINERARY)
        assertThat(receivedMessage.tripId).isEqualTo(trip.id)

        val detailDto = objectMapper.convertValue(receivedMessage.data, ItineraryResponse.ItineraryDetail::class.java)
        assertThat(detailDto.itineraryId).isEqualTo(item.id!!)
        assertThat(detailDto.memo).isEqualTo("수정 후 메모")
        assertThat(detailDto.name).isEqualTo("수정 전 일정")

        println("[SockJS] 수신된 메시지: $receivedMessage")
        session.disconnect()
    }

    @Test
    @DisplayName("일정 삭제 시 웹소켓 메시지 브로드캐스트")
    fun deleteItinerary_ShouldBroadcastMessage() {
        // given
        val item = itineraryItemRepository.save(
            ItineraryItem(
                category = category,
                place = null,
                title = "삭제될 일정",
                time = LocalDateTime.of(LocalDate.now(), LocalTime.of(10, 0)),
                order = 1,
                memo = "삭제될 메모"
            )
        )
        val itemId = item.id!!

        val future = CompletableFuture<SocketDto.TripEvent>()
        val session = createWebSocketSession(future)

        // when
        itineraryService.deleteItinerary(itemId)

        // then
        val receivedMessage = future.get(5, TimeUnit.SECONDS)
        assertThat(receivedMessage).isNotNull
        assertThat(receivedMessage.type).isEqualTo(SocketDto.EditType.DELETE_ITINERARY)
        assertThat(receivedMessage.tripId).isEqualTo(trip.id)
        assertThat((receivedMessage.data as Number).toLong()).isEqualTo(itemId)

        println("[SockJS] 수신된 메시지: $receivedMessage")
        session.disconnect()
    }

    @Test
    @DisplayName("일정 순서 변경 시 웹소켓 메시지 브로드캐스트")
    fun updateItineraryOrder_ShouldBroadcastMessage() {
        // given
        val item1 = itineraryItemRepository.save(ItineraryItem(category, null, "일정1", null, 1, null))
        val item2 = itineraryItemRepository.save(ItineraryItem(category, null, "일정2", null, 2, null))

        val future = CompletableFuture<SocketDto.TripEvent>()
        val session = createWebSocketSession(future)

        // when
        val orderUpdateRequest = ItineraryRequest.OrderUpdate(
            items = listOf(
                ItineraryRequest.OrderItem(item1.id!!, category.id!!, 2),
                ItineraryRequest.OrderItem(item2.id!!, category.id!!, 1)
            )
        )
        itineraryService.updateItineraryOrder(trip.id!!, orderUpdateRequest)

        // then
        val receivedMessage = future.get(5, TimeUnit.SECONDS)
        assertThat(receivedMessage).isNotNull
        assertThat(receivedMessage.type).isEqualTo(SocketDto.EditType.MOVE_ITINERARY)
        assertThat(receivedMessage.tripId).isEqualTo(trip.id)

        val detailDtoList = objectMapper.convertValue(
            receivedMessage.data,
            object : TypeReference<List<ItineraryResponse.ItineraryDetail>>() {}
        )
        assertThat(detailDtoList).hasSize(2)

        val updatedItem1 = detailDtoList.find { it.itineraryId == item1.id!! }
        val updatedItem2 = detailDtoList.find { it.itineraryId == item2.id!! }

        assertThat(updatedItem1!!.name).isEqualTo("일정1")
        assertThat(updatedItem1.order).isEqualTo(2)
        assertThat(updatedItem2!!.name).isEqualTo("일정2")
        assertThat(updatedItem2.order).isEqualTo(1)

        println("[SockJS] 수신된 메시지: $receivedMessage")
        session.disconnect()
    }
}
