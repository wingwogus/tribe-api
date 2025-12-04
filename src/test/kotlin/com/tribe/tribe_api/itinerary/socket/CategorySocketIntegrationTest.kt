package com.tribe.tribe_api.itinerary.socket

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.tribe.tribe_api.common.util.jwt.JwtTokenProvider
import com.tribe.tribe_api.common.util.security.CustomUserDetails
import com.tribe.tribe_api.common.util.socket.SocketDto
import com.tribe.tribe_api.itinerary.dto.CategoryDto
import com.tribe.tribe_api.itinerary.entity.Category
import com.tribe.tribe_api.itinerary.repository.CategoryRepository
import com.tribe.tribe_api.itinerary.service.CategoryService
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
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CategorySocketIntegrationTest {

    @LocalServerPort
    var port: Int = 0

    @Autowired lateinit var jwtTokenProvider: JwtTokenProvider
    @Autowired lateinit var categoryService: CategoryService
    @Autowired lateinit var memberRepository: MemberRepository
    @Autowired lateinit var tripRepository: TripRepository
    @Autowired lateinit var categoryRepository: CategoryRepository
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
    @DisplayName("카테고리 생성 시 웹소켓 메시지 전송")
    fun createCategory_ShouldBroadcastMessage() {
        // given
        val future = CompletableFuture<SocketDto.TripEvent>()
        val session = createWebSocketSession(future)
        val request = CategoryDto.CreateRequest(name = "새 카테고리", day = 1, order = 2)

        // when
        categoryService.createCategory(trip.id!!, request)

        // then
        val receivedMessage = future.get(5, TimeUnit.SECONDS)

        assertThat(receivedMessage).isNotNull
        assertThat(receivedMessage.type).isEqualTo(SocketDto.EditType.ADD_CATEGORY)
        assertThat(receivedMessage.tripId).isEqualTo(trip.id)

        val responseDto = objectMapper.convertValue(receivedMessage.data, CategoryDto.CategoryResponse::class.java)

        assertThat(responseDto.categoryId).isNotNull
        assertThat(responseDto.name).isEqualTo("새 카테고리")
        assertThat(responseDto.day).isEqualTo(1)
        assertThat(responseDto.order).isEqualTo(2)

        println("[SockJS] 수신된 메시지: $receivedMessage")
        session.disconnect()
    }

    @Test
    @DisplayName("카테고리 수정 시 웹소켓 메시지 전송")
    fun updateCategory_ShouldBroadcastMessage() {
        // given
        val future = CompletableFuture<SocketDto.TripEvent>()
        val session = createWebSocketSession(future)
        val request = CategoryDto.UpdateRequest(name = "수정된 카테고리 이름", day = 2, order = 3, memo = "메모 추가")

        // when
        categoryService.updateCategory(trip.id!!, category.id!!, request)

        // then
        val receivedMessage = future.get(5, TimeUnit.SECONDS)
        assertThat(receivedMessage).isNotNull
        assertThat(receivedMessage.type).isEqualTo(SocketDto.EditType.EDIT_CATEGORY)
        assertThat(receivedMessage.tripId).isEqualTo(trip.id)

        val responseDto = objectMapper.convertValue(receivedMessage.data, CategoryDto.CategoryResponse::class.java)

        assertThat(responseDto.categoryId).isEqualTo(category.id!!)
        assertThat(responseDto.name).isEqualTo("수정된 카테고리 이름")
        assertThat(responseDto.day).isEqualTo(2)
        assertThat(responseDto.order).isEqualTo(3)
        assertThat(responseDto.memo).isEqualTo("메모 추가")

        println("[SockJS] 수신된 메시지: $receivedMessage")
        session.disconnect()
    }

    @Test
    @DisplayName("카테고리 삭제 시 웹소켓 메시지 전송")
    fun deleteCategory_ShouldBroadcastMessage() {
        // given
        val future = CompletableFuture<SocketDto.TripEvent>()
        val session = createWebSocketSession(future)
        val categoryId = category.id!!

        // when
        categoryService.deleteCategory(trip.id!!, categoryId)

        // then
        val receivedMessage = future.get(5, TimeUnit.SECONDS)
        assertThat(receivedMessage).isNotNull
        assertThat(receivedMessage.type).isEqualTo(SocketDto.EditType.DELETE_CATEGORY)
        assertThat(receivedMessage.tripId).isEqualTo(trip.id)
        assertThat((receivedMessage.data as Number).toLong()).isEqualTo(categoryId)

        println("[SockJS] 수신된 메시지: $receivedMessage")
        session.disconnect()
    }

    @Test
    @DisplayName("카테고리 순서 변경 시 웹소켓 메시지 전송")
    fun orderUpdateCategory_ShouldBroadcastMessage() {
        // given
        val category2 = categoryRepository.save(Category(trip, 1, "Day 1 - Cat 2", 2))
        val future = CompletableFuture<SocketDto.TripEvent>()
        val session = createWebSocketSession(future)
        val request = CategoryDto.OrderUpdate(
            items = listOf(
                CategoryDto.OrderCategory(categoryId = category.id!!, order = 2),
                CategoryDto.OrderCategory(categoryId = category2.id!!, order = 1)
            )
        )

        // when
        categoryService.orderUpdateCategory(trip.id!!, request)

        // then
        val receivedMessage = future.get(5, TimeUnit.SECONDS)
        assertThat(receivedMessage).isNotNull
        assertThat(receivedMessage.type).isEqualTo(SocketDto.EditType.MOVE_CATEGORY)
        assertThat(receivedMessage.tripId).isEqualTo(trip.id)

        val dtoList = objectMapper.convertValue(
            receivedMessage.data,
            object : TypeReference<List<CategoryDto.CategoryResponse>>() {}
        )
        assertThat(dtoList).hasSize(2)

        val updatedCat1 = dtoList.find { it.categoryId == category.id!! }
        val updatedCat2 = dtoList.find { it.categoryId == category2.id!! }

        assertThat(updatedCat1!!.order).isEqualTo(2)
        assertThat(updatedCat2!!.order).isEqualTo(1)

        println("[SockJS] 수신된 메시지: $receivedMessage")
        session.disconnect()
    }
}