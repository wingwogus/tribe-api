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
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
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
@ExtendWith(MockKExtension::class)
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
        // 1. 테스트용 사용자 생성
        owner = memberRepository.save(Member("owner@test.com", passwordEncoder.encode("pw"), "방장", provider = Provider.LOCAL, role = Role.USER, isFirstLogin = false))
        member1 = memberRepository.save(Member("member1@test.com", passwordEncoder.encode("pw"), "멤버1", provider = Provider.LOCAL, role = Role.USER, isFirstLogin = false))
        member2 = memberRepository.save(Member("member2@test.com", passwordEncoder.encode("pw"), "멤버2", provider = Provider.LOCAL, role = Role.USER, isFirstLogin = false))

        // 2. 테스트용 여행 생성
        trip = tripRepository.save(Trip("테스트 여행", java.time.LocalDate.now(), java.time.LocalDate.now().plusDays(5), Country.JAPAN))
        trip.addMember(owner, TripRole.OWNER)
        trip.addMember(member1, TripRole.MEMBER)
        trip.addMember(member2, TripRole.MEMBER)

        // TripMember 엔티티를 직접 조회하여 변수에 할당
        ownerTripMember = tripMemberRepository.findByTripAndMember(trip, owner)!!
        member1TripMember = tripMemberRepository.findByTripAndMember(trip, member1)!!
        member2TripMember = tripMemberRepository.findByTripAndMember(trip, member2)!!

        // 3. 테스트용 여정 생성 (이제 이렇게 생성하면 됩니다)
        val category = categoryRepository.save(Category(trip, 1, "1일차", 1))
        val place = placeRepository.save(Place("place_id_settlement", "테스트 장소", "주소", BigDecimal.ZERO, BigDecimal.ZERO))
        itineraryItem = itineraryItemRepository.save(ItineraryItem(category, place, "저녁 식사", null, 1, null))

        // 4. Mock 객체들의 동작 정의
        // Cloudinary는 어떤 파일이든 "mock-url"을 반환하도록 설정
        every { cloudinaryUploadService.upload(any()) } returns "https://mock.cloudinary.com/image.jpg"
    }

    private fun setAuthentication(member: Member) {
        val userDetails = CustomUserDetails(member)
        val authentication = UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities)
        SecurityContextHolder.getContext().authentication = authentication
    }

    @Test
    @DisplayName("지출 생성 성공 - 수기 입력(HANDWRITE)")
    fun createExpense_Success_Handwrite() {
        // given: '방장'이 로그인하고 지출 생성을 요청
        setAuthentication(owner)
        val request = ExpenseDto.CreateRequest(
            tripId = trip.id!!,
            expenseTitle = "저녁 식사",
            totalAmount = BigDecimal("50000"),
            itineraryItemId = itineraryItem.id!!,
            payerId = ownerTripMember.id!!,
            inputMethod = "HANDWRITE",
            items = listOf(
                ExpenseDto.ItemCreate("라멘", BigDecimal("20000")),
                ExpenseDto.ItemCreate("맥주", BigDecimal("30000"))
            )
        )

        // when: 지출 생성
        val response = expenseService.createExpense(trip.id!!, itineraryItem.id!!, request, null)

        // then: 생성된 지출 검증
        assertThat(response.expenseTitle).isEqualTo("저녁 식사")
        assertThat(response.totalAmount).isEqualByComparingTo("50000")
        assertThat(response.payer.name).isEqualTo("방장")
        assertThat(response.items).hasSize(2)
    }

    @Test
    @DisplayName("지출 생성 성공 - 영수증 스캔(SCAN)")
    fun createExpense_Success_Scan() {
        // given: '멤버1'이 로그인하고 영수증 이미지 파일과 함께 요청
        setAuthentication(member1)
        val imageFile = MockMultipartFile("image", "receipt.jpg", "image/jpeg", "test image data".toByteArray())
        val request = ExpenseDto.CreateRequest(
            tripId = trip.id!!,
            expenseTitle = "편의점 간식",
            totalAmount = null, // SCAN 시에는 총액을 보내지 않음
            itineraryItemId = itineraryItem.id!!,
            payerId = member1TripMember.id!!,
            inputMethod = "SCAN",
            items = emptyList() // SCAN 시에는 아이템 목록을 보내지 않음
        )

        // Gemini API가 반환할 가짜 JSON 데이터 정의
        val fakeOcrResponse = ExpenseDto.OcrResponse(
            totalAmount = BigDecimal("12000"),
            items = listOf(
                ExpenseDto.OcrItem("콜라", BigDecimal("2000")),
                ExpenseDto.OcrItem("과자", BigDecimal("10000"))
            )
        )
        // ObjectMapper를 사용하여 객체를 JSON 문자열로 변환
        val fakeGeminiJson = objectMapper.writeValueAsString(fakeOcrResponse)
        every { geminiApiClient.generateContentFromImage(any(), any(), any()) } returns fakeGeminiJson

        // when: 지출 생성
        val response = expenseService.createExpense(trip.id!!, itineraryItem.id!!, request, imageFile)

        // then: OCR 결과대로 지출이 생성되었는지 검증
        val savedExpense = expenseRepository.findById(response.expenseId).get()
        assertThat(savedExpense.title).isEqualTo("편의점 간식")
        assertThat(savedExpense.totalAmount).isEqualByComparingTo("12000")
        assertThat(savedExpense.payer.id).isEqualTo(member1TripMember.id)
        assertThat(savedExpense.receiptImageUrl).isEqualTo("https://mock.cloudinary.com/image.jpg")
        assertThat(savedExpense.expenseItems).hasSize(2)
        assertThat(savedExpense.expenseItems.any { it.name == "콜라" }).isTrue()
    }

    @Test
    @DisplayName("지출 수정 시 가격 변경되면 참여자 N빵 금액 자동 재계산 성공")
    fun updateExpense_RecalculateAmount_Success() {
        // given: '방장'이 로그인. 30000원짜리 지출을 생성하고 2명(방장, 멤버1)에게 15000원씩 배정.
        setAuthentication(owner)
        val expenseResponse = createTestExpense(BigDecimal("30000"))
        assignTestParticipants(expenseResponse.expenseId, listOf(ownerTripMember.id!!, member1TripMember.id!!))

        // when: 지출 총액을 30000원에서 40000원으로 수정
        val updateRequest = ExpenseDto.UpdateRequest(
            tripId = trip.id!!,
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
        expenseService.updateExpense(trip.id!!, expenseResponse.expenseId, updateRequest)

        // then: 배정된 금액이 20000원씩으로 자동 변경되었는지 확인
        val updatedExpense = expenseRepository.findById(expenseResponse.expenseId).get()
        val assignments = updatedExpense.expenseItems[0].assignments
        assertThat(assignments).hasSize(2)
        assertThat(assignments.first { it.tripMember.id == ownerTripMember.id }.amount).isEqualByComparingTo("20000")
        assertThat(assignments.first { it.tripMember.id == member1TripMember.id }.amount).isEqualByComparingTo("20000")
    }

    @Test
    @DisplayName("지출 생성 실패 - 총액과 아이템 합계 불일치")
    fun createExpense_Fail_AmountMismatch() {
        // given: 총액은 50000원인데, 아이템 합계는 40000원인 잘못된 요청
        setAuthentication(owner)
        val request = ExpenseDto.CreateRequest(
            tripId = trip.id!!,
            expenseTitle = "금액 안맞는 지출",
            totalAmount = BigDecimal("50000"),
            itineraryItemId = itineraryItem.id!!,
            payerId = ownerTripMember.id!!,
            inputMethod = "HANDWRITE",
            items = listOf(ExpenseDto.ItemCreate("아이템", BigDecimal("40000")))
        )

        // when & then: EXPENSE_TOTAL_AMOUNT_MISMATCH 예외가 발생하는지 검증
        val exception = assertThrows<BusinessException> {
            expenseService.createExpense(trip.id!!, itineraryItem.id!!, request, null)
        }
        assertThat(exception.errorCode).isEqualTo(ErrorCode.EXPENSE_TOTAL_AMOUNT_MISMATCH)
    }

    @Test
    @DisplayName("멤버별 배분 정보 등록 성공 (N빵 및 나머지 처리)")
    fun assignParticipants_Success_WithRemainder() {
        // given: '방장'이 로그인. 10000원짜리 지출을 생성.
        setAuthentication(owner)
        val expenseResponse = createTestExpense(BigDecimal("10000"))
        val expenseItemId = expenseResponse.items[0].itemId

        // 3명(방장, 멤버1, 멤버2)을 배정하도록 요청 준비
        val request = ExpenseDto.ParticipantAssignRequest(
            tripId = trip.id!!,
            items = listOf(
                ExpenseDto.ItemAssignment(
                    itemId = expenseItemId,
                    participantIds = listOf(ownerTripMember.id!!, member1TripMember.id!!, member2TripMember.id!!)
                )
            )
        )

        // when: 참여자 배정 서비스 호출
        expenseService.assignParticipants(trip.id!!, expenseResponse.expenseId, request)

        // then: 3명에게 3334원, 3333원, 3333원으로 올바르게 배정되었는지 확인
        val updatedExpense = expenseRepository.findById(expenseResponse.expenseId).get()
        val assignments = updatedExpense.expenseItems.first { it.id == expenseItemId }.assignments

        assertThat(assignments).hasSize(3)

        val ownerAssignment = assignments.first { it.tripMember.id == ownerTripMember.id!! }
        val member1Assignment = assignments.first { it.tripMember.id == member1TripMember.id!! }
        val member2Assignment = assignments.first { it.tripMember.id == member2TripMember.id!! }

        // 첫 번째 참여자(방장)에게 나머지 1원이 더해져 3334원이 되어야 함
        assertThat(ownerAssignment.amount).isEqualByComparingTo("3334")
        assertThat(member1Assignment.amount).isEqualByComparingTo("3333")
        assertThat(member2Assignment.amount).isEqualByComparingTo("3333")
    }

    // 테스트용 지출 생성 헬퍼
    private fun createTestExpense(totalAmount: BigDecimal = BigDecimal("15000")): ExpenseDto.CreateResponse {
        val request = ExpenseDto.CreateRequest(
            tripId = trip.id!!,
            expenseTitle = "테스트 지출",
            totalAmount = totalAmount,
            itineraryItemId = itineraryItem.id!!,
            payerId = ownerTripMember.id!!,
            inputMethod = "HANDWRITE",
            items = listOf(ExpenseDto.ItemCreate("테스트 아이템", totalAmount))
        )
        return expenseService.createExpense(trip.id!!, itineraryItem.id!!, request, null)
    }

    // 테스트용 참여자 배정 헬퍼
    private fun assignTestParticipants(expenseId: Long, participantIds: List<Long>) {
        val expense = expenseRepository.findById(expenseId).get()
        val request = ExpenseDto.ParticipantAssignRequest(
            tripId = trip.id!!,
            items = listOf(
                ExpenseDto.ItemAssignment(
                    itemId = expense.expenseItems[0].id!!,
                    participantIds = participantIds
                )
            )
        )
        expenseService.assignParticipants(trip.id!!, expenseId, request)
    }
}