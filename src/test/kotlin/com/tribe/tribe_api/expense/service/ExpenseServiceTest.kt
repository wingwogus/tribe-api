package com.tribe.tribe_api.expense.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.tribe.tribe_api.common.exception.BusinessException
import com.tribe.tribe_api.common.exception.ErrorCode
import com.tribe.tribe_api.common.util.security.CustomUserDetails
import com.tribe.tribe_api.common.util.service.CloudinaryUploadService
import com.tribe.tribe_api.common.util.service.GeminiApiClient
import com.tribe.tribe_api.expense.dto.ExpenseDto
import com.tribe.tribe_api.expense.repository.ExpenseRepository
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
import com.tribe.tribe_api.trip.entity.TripMember
import com.tribe.tribe_api.trip.entity.TripRole
import com.tribe.tribe_api.trip.repository.TripMemberRepository
import com.tribe.tribe_api.trip.repository.TripRepository
import io.mockk.every
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@SpringBootTest
@Transactional
class ExpenseServiceTest @Autowired constructor(
    private val expenseService: ExpenseService,
    private val expenseRepository: ExpenseRepository,
    private val memberRepository: MemberRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tripRepository: TripRepository,
    private val tripMemberRepository: TripMemberRepository,
    private val placeRepository: PlaceRepository,
    private val categoryRepository: CategoryRepository,
    private val itineraryItemRepository: ItineraryItemRepository,
    private val objectMapper: ObjectMapper,
){
    @MockkBean
    private lateinit var geminiApiClient: GeminiApiClient
    @MockkBean
    private lateinit var cloudinaryUploadService: CloudinaryUploadService
    private lateinit var owner: Member
    private lateinit var member1: Member
    private lateinit var trip: Trip
    private lateinit var ownerTripMember: TripMember
    private lateinit var member1TripMember: TripMember
    private lateinit var member2: Member
    private lateinit var member2TripMember: TripMember
    private lateinit var itineraryItem: ItineraryItem

    @BeforeEach
    fun setUp() {
        owner = memberRepository.save(Member("owner@test.com", passwordEncoder.encode("pw"), "방장", provider = Provider.LOCAL, role = Role.USER, isFirstLogin = false))
        member1 = memberRepository.save(Member("member1@test.com", passwordEncoder.encode("pw"), "멤버1", provider = Provider.LOCAL, role = Role.USER, isFirstLogin = false))
        member2 = memberRepository.save(Member("member2@test.com", passwordEncoder.encode("pw"), "멤버2", provider = Provider.LOCAL, role = Role.USER, isFirstLogin = false))

        trip = tripRepository.save(Trip("테스트 여행", java.time.LocalDate.now(), java.time.LocalDate.now().plusDays(5), Country.JAPAN))
        trip.addMember(owner, TripRole.OWNER)
        trip.addMember(member1, TripRole.MEMBER)
        trip.addMember(member2, TripRole.MEMBER)

        ownerTripMember = tripMemberRepository.findByTripAndMember(trip, owner)!!
        member1TripMember = tripMemberRepository.findByTripAndMember(trip, member1)!!
        member2TripMember = tripMemberRepository.findByTripAndMember(trip, member2)!!

        val category = categoryRepository.save(Category(trip, 1, "1일차", 1))
        val place = placeRepository.save(Place("place_id_settlement", "테스트 장소", "주소", BigDecimal.ZERO, BigDecimal.ZERO))
        itineraryItem = itineraryItemRepository.save(ItineraryItem(category, place, "저녁 식사", null, 1, null))

        // Mocking Cloudinary
        // 💡 수정: any()를 명시적으로 any<MultipartFile>()로 지정하여 MockK 타입 추론 강화
        every { cloudinaryUploadService.upload(any() , any()) } returns "https://mock.cloudinary.com/image.jpg"
    }

    private fun setAuthentication(member: Member) {
        val userDetails = CustomUserDetails(member)
        val authentication = UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities)
        SecurityContextHolder.getContext().authentication = authentication
    }

    @Test
    @DisplayName("지출 생성 성공 - 수기 입력(HANDWRITE)")
    fun createExpense_Success_Handwrite() {
        // given: 여행 멤버인 '방장'으로 로그인
        setAuthentication(owner)
        val request = ExpenseDto.CreateRequest(
            tripId = trip.id!!,
            expenseTitle = "저녁 식사",
            totalAmount = BigDecimal("50000"),
            itineraryItemId = itineraryItem.id!!,
            payerId = ownerTripMember.id!!,
            inputMethod = "HANDWRITE",
            currency = "KRW",
            items = listOf(
                ExpenseDto.ItemCreate("라멘", BigDecimal("20000")),
                ExpenseDto.ItemCreate("맥주", BigDecimal("30000"))
            )
        )

        // when: 실제 보안 검증 로직을 거쳐 서비스 호출
        val response = expenseService.createExpense(trip.id!!, itineraryItem.id!!, request, null)

        // then: 성공적으로 생성됨
        assertThat(response.expenseTitle).isEqualTo("저녁 식사")
        assertThat(response.totalAmount).isEqualByComparingTo("50000")
        assertThat(response.items).hasSize(2)
    }

    @Test
    @DisplayName("지출 생성 성공 - 스캔(SCAN) - 팁/세금 포함 (차액 발생)")
    fun createExpense_Success_Scan_With_TaxAndTip() {
        // given: '멤버1'이 로그인
        setAuthentication(member1)
        val imageFile = MockMultipartFile("image", "receipt.jpg", "image/jpeg", "test image data".toByteArray())
        val request = ExpenseDto.CreateRequest(
            tripId = trip.id!!,
            expenseTitle = "레스토랑 저녁",
            totalAmount = null, // 스캔 시에는 totalAmount를 보내지 않음
            itineraryItemId = itineraryItem.id!!,
            payerId = member1TripMember.id!!,
            inputMethod = "SCAN",
            currency = "USD",
            items = emptyList()
        )

        // Gemini API가 팁/세금이 포함된 총액을 반환하는 상황 모의
        // (항목 합계 = 1000, 총액 = 1200 -> 차액 200 발생)
        val fakeOcrResponse = ExpenseDto.OcrResponse(
            totalAmount = BigDecimal("1200.00"),
            items = listOf(
                ExpenseDto.OcrItem("파스타", BigDecimal("700.00")),
                ExpenseDto.OcrItem("와인", BigDecimal("300.00"))
            ),
            subtotal = BigDecimal("1000.00"),
            tax = BigDecimal("100.00"),
            tip = BigDecimal("100.00"),
            discount = BigDecimal.ZERO
        )
        val fakeGeminiJson = objectMapper.writeValueAsString(fakeOcrResponse)
        every { geminiApiClient.generateContentFromImage(any(), any(), any()) } returns fakeGeminiJson

        // when: 지출 생성
        val response = expenseService.createExpense(trip.id!!, itineraryItem.id!!, request, imageFile)

        // then: 차액(200)이 "세금 / 팁 / 기타" 항목으로 자동 추가되어야 함
        val savedExpense = expenseRepository.findById(response.expenseId).get()
        assertThat(savedExpense.title).isEqualTo("레스토랑 저녁")
        assertThat(savedExpense.totalAmount).isEqualByComparingTo("1200.00") // AI가 준 총액
        assertThat(savedExpense.expenseItems).hasSize(3) // 기존 2개 + 차액 1개
        assertThat(savedExpense.expenseItems.first { it.name == "파스타" }.price).isEqualByComparingTo("700.00")
        assertThat(savedExpense.expenseItems.first { it.name == "와인" }.price).isEqualByComparingTo("300.00")
        assertThat(savedExpense.expenseItems.first { it.name == "세금 / 팁 / 기타" }.price).isEqualByComparingTo("200.00") // 차액(1200 - 1000)
        assertThat(savedExpense.receiptImageUrl).isEqualTo("https://mock.cloudinary.com/image.jpg")
    }

    @Test
    @DisplayName("지출 생성 성공 - 스캔(SCAN) - 할인 포함 (음수 차액 발생)")
    fun createExpense_Success_Scan_With_Discount() {
        // given: '멤버1'이 로그인
        setAuthentication(member1)
        val imageFile = MockMultipartFile("image", "receipt.jpg", "image/jpeg", "test image data".toByteArray())
        val request = ExpenseDto.CreateRequest(
            tripId = trip.id!!,
            expenseTitle = "마트 장보기",
            totalAmount = null,
            itineraryItemId = itineraryItem.id!!,
            payerId = member1TripMember.id!!,
            inputMethod = "SCAN",
            currency = "USD",
            items = emptyList()
        )

        // Gemini API가 할인이 적용된 총액을 반환하는 상황 모의
        // (항목 합계 = 1000, 총액 = 800 -> 차액 -200 발생)
        val fakeOcrResponse = ExpenseDto.OcrResponse(
            totalAmount = BigDecimal("800.00"),
            items = listOf(
                ExpenseDto.OcrItem("우유", BigDecimal("300.00")),
                ExpenseDto.OcrItem("빵", BigDecimal("700.00"))
            ),
            subtotal = BigDecimal("1000.00"),
            tax = BigDecimal.ZERO,
            tip = BigDecimal.ZERO,
            discount = BigDecimal("200.00")
        )
        val fakeGeminiJson = objectMapper.writeValueAsString(fakeOcrResponse)
        every { geminiApiClient.generateContentFromImage(any(), any(), any()) } returns fakeGeminiJson

        // when: 지출 생성
        val response = expenseService.createExpense(trip.id!!, itineraryItem.id!!, request, imageFile)

        // then: 차액(-200)이 "할인" 항목으로 자동 추가되어야 함
        val savedExpense = expenseRepository.findById(response.expenseId).get()
        assertThat(savedExpense.totalAmount).isEqualByComparingTo("800.00") // AI가 준 총액
        assertThat(savedExpense.expenseItems).hasSize(3) // 기존 2개 + 할인 1개
        assertThat(savedExpense.expenseItems.first { it.name == "우유" }.price).isEqualByComparingTo("300.00")
        assertThat(savedExpense.expenseItems.first { it.name == "빵" }.price).isEqualByComparingTo("700.00")
        assertThat(savedExpense.expenseItems.first { it.name == "할인" }.price).isEqualByComparingTo("-200.00") // 차액 (800 - 1000)
    }

    @Test
    @DisplayName("지출 생성 성공 - 스캔(SCAN) - 항목명 번역 검증")
    fun createExpense_Success_Scan_With_Translation() {
        // given: '멤버1'이 로그인
        setAuthentication(member1)
        val imageFile = MockMultipartFile("image", "receipt.jpg", "image/jpeg", "test image data".toByteArray())
        val request = ExpenseDto.CreateRequest(
            tripId = trip.id!!,
            expenseTitle = "해외 영수증",
            totalAmount = null,
            itineraryItemId = itineraryItem.id!!,
            payerId = member1TripMember.id!!,
            inputMethod = "SCAN",
            currency = "USD",
            items = emptyList()
        )

        // Gemini API가 영어 항목명을 "한국어"로 번역해서 반환하는 상황 모의
        val fakeOcrResponse = ExpenseDto.OcrResponse(
            totalAmount = BigDecimal("15.00"),
            items = listOf(
                ExpenseDto.OcrItem("커피", BigDecimal("15.00")) // "Coffee"가 "커피"로 번역됨
            ),
            subtotal = BigDecimal("15.00"),
            tax = BigDecimal.ZERO,
            tip = BigDecimal.ZERO,
            discount = BigDecimal.ZERO
        )
        val fakeGeminiJson = objectMapper.writeValueAsString(fakeOcrResponse)
        every { geminiApiClient.generateContentFromImage(any(), any(), any()) } returns fakeGeminiJson

        // when: 지출 생성
        val response = expenseService.createExpense(trip.id!!, itineraryItem.id!!, request, imageFile)

        // then: "커피"라는 번역된 이름으로 저장되었는지 검증
        val savedExpense = expenseRepository.findById(response.expenseId).get()
        assertThat(savedExpense.totalAmount).isEqualByComparingTo("15.00")
        assertThat(savedExpense.expenseItems).hasSize(1)
        assertThat(savedExpense.expenseItems.first().name).isEqualTo("커피") // 번역된 이름 확인
    }

    @Test
    @DisplayName("지출 수정 성공")
    fun updateExpense_Success() {
        // given: 여행 멤버인 '방장'으로 로그인
        setAuthentication(owner)
        val expenseResponse = createTestExpense(BigDecimal("30000"))
        val updateRequest = ExpenseDto.UpdateRequest(
            expenseTitle = "수정된 지출",
            totalAmount = BigDecimal("40000"),
            payerId = ownerTripMember.id!!,
            items = listOf(
                ExpenseDto.ItemUpdate(
                    itemId = expenseResponse.items[0].itemId,
                    itemName = "수정된 아이템",
                    price = BigDecimal("40000")
                )
            )
        )

        // when: 실제 보안 검증 로직을 거쳐 서비스 호출
        val response = expenseService.updateExpense(expenseResponse.expenseId, updateRequest)

        // then: 성공적으로 수정됨
        assertThat(response.totalAmount).isEqualByComparingTo("40000")
        assertThat(response.items[0].itemName).isEqualTo("수정된 아이템")
    }

    @Test
    @DisplayName("지출 삭제 성공")
    fun deleteExpense_Success() {
        // given: 여행 멤버인 '방장'으로 로그인
        setAuthentication(owner)
        val expenseResponse = createTestExpense()

        // when: 실제 보안 검증 로직을 거쳐 서비스 호출
        expenseService.deleteExpense(expenseResponse.expenseId)

        // then: 성공적으로 삭제됨
        val findExpense = expenseRepository.findById(expenseResponse.expenseId)
        assertThat(findExpense.isPresent).isFalse()
    }

    // --- 실패 케이스 테스트 ---
    private fun setupNonMember(): Member {
        val nonMember = memberRepository.save(Member(
            "nonmember@test.com", passwordEncoder.encode("pw"),
            "외부인", provider = Provider.LOCAL, role = Role.USER, isFirstLogin = false))
        setAuthentication(nonMember)
        return nonMember
    }

    @Test
    @DisplayName("지출 수정 실패 - 여행 멤버가 아닌 경우")
    fun updateExpense_Fail_NotATripMember() {
        // given: 지출을 생성하고, 여행 멤버가 아닌 '외부인'으로 로그인
        setAuthentication(owner)
        val expenseResponse = createTestExpense()
        setupNonMember()
        val updateRequest = ExpenseDto.UpdateRequest("타이틀", BigDecimal.ONE, ownerTripMember.id!!, emptyList())

        // when & then: 실제 보안 검증 로직이 BusinessException을 던지는지 확인
        val exception = assertThrows<BusinessException> {
            expenseService.updateExpense(expenseResponse.expenseId, updateRequest)
        }
        assertThat(exception.errorCode).isEqualTo(ErrorCode.NOT_A_TRIP_MEMBER)
    }

    @Test
    @DisplayName("지출 삭제 실패 - 여행 멤버가 아닌 경우")
    fun deleteExpense_Fail_NotATripMember() {
        // given: 지출을 생성하고, 여행 멤버가 아닌 '외부인'으로 로그인
        setAuthentication(owner)
        val expenseResponse = createTestExpense()
        setupNonMember()

        // when & then: 실제 보안 검증 로직이 BusinessException을 던지는지 확인
        val exception = assertThrows<BusinessException> {
            expenseService.deleteExpense(expenseResponse.expenseId)
        }
        assertThat(exception.errorCode).isEqualTo(ErrorCode.NOT_A_TRIP_MEMBER)
    }

    @Test
    @DisplayName("지출 생성 실패 - 여정 아이템이 해당 여행에 속하지 않는 경우")
    fun createExpense_Fail_ItineraryItemNotInTrip() {
        // given: '방장'으로 로그인
        setAuthentication(owner)

        // 다른 여행 데이터 생성
        val anotherTrip = tripRepository.save(Trip("다른 여행", java.time.LocalDate.now(), java.time.LocalDate.now().plusDays(1), Country.USA))
        val anotherCategory = categoryRepository.save(Category(anotherTrip, 1, "다른 여행 카테고리", 1))
        val anotherItineraryItem = itineraryItemRepository.save(ItineraryItem(anotherCategory, null, "다른 일정", null, 1, null))

        val request = ExpenseDto.CreateRequest(
            tripId = trip.id!!, // 원래 여행 ID
            expenseTitle = "저녁 식사",
            totalAmount = BigDecimal("10000"),
            itineraryItemId = anotherItineraryItem.id!!, // 👈 다른 여행의 아이템 ID 사용
            payerId = ownerTripMember.id!!,
            inputMethod = "HANDWRITE",
            currency = "KRW",
            items = listOf(ExpenseDto.ItemCreate("테스트 아이템", BigDecimal("10000")))
        )

        // when & then
        val exception = assertThrows<BusinessException> {
            expenseService.createExpense(trip.id!!, anotherItineraryItem.id!!, request, null)
        }
        assertThat(exception.errorCode).isEqualTo(ErrorCode.NO_AUTHORITY_TRIP)
    }


    // 테스트용 헬퍼 메서드
    private fun createTestExpense(totalAmount: BigDecimal = BigDecimal("15000")): ExpenseDto.CreateResponse {
        // 헬퍼 메서수는 테스트의 일부이므로, 여기서도 실제 보안 검증을 통과해야 함
        // createTestExpense를 호출하기 전에 setAuthentication이 먼저 호출되므로,
        // 이 메서수는 항상 권한이 있는 상태에서 실행됨.
        val request = ExpenseDto.CreateRequest(
            tripId = trip.id!!,
            expenseTitle = "테스트 지출",
            totalAmount = totalAmount,
            itineraryItemId = itineraryItem.id!!,
            payerId = ownerTripMember.id!!,
            inputMethod = "HANDWRITE",
            currency = "JPY",
            items = listOf(ExpenseDto.ItemCreate("테스트 아이템", totalAmount))
        )
        return expenseService.createExpense(trip.id!!, itineraryItem.id!!, request, null)
    }
}