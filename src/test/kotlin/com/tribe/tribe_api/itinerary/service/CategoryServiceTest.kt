package com.tribe.tribe_api.itinerary.service

import com.tribe.tribe_api.common.exception.BusinessException
import com.tribe.tribe_api.common.exception.ErrorCode
import com.tribe.tribe_api.common.util.security.CustomUserDetails
import com.tribe.tribe_api.itinerary.dto.CategoryDto
import com.tribe.tribe_api.itinerary.entity.Category
import com.tribe.tribe_api.itinerary.repository.CategoryRepository
import com.tribe.tribe_api.member.entity.Member
import com.tribe.tribe_api.member.entity.Provider
import com.tribe.tribe_api.member.entity.Role
import com.tribe.tribe_api.member.repository.MemberRepository
import com.tribe.tribe_api.trip.entity.Country
import com.tribe.tribe_api.trip.entity.Trip
import com.tribe.tribe_api.trip.entity.TripRole
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
class CategoryServiceTest @Autowired constructor(
    private val categoryService: CategoryService,
    private val memberRepository: MemberRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tripRepository: TripRepository,
    private val categoryRepository: CategoryRepository
) {
    private lateinit var member1: Member
    private lateinit var nonMember: Member
    private lateinit var trip: Trip
    private lateinit var category1_day1: Category
    private lateinit var category2_day1: Category
    private lateinit var category3_day2: Category
    private lateinit var category4_day2: Category

    @BeforeEach
    fun setUp() {
        member1 = memberRepository.save(
            Member(
                "member1@test.com", passwordEncoder.encode("pw"), "멤버1",
                null, Role.USER, Provider.LOCAL, null, false
            )
        )

        nonMember = memberRepository.save(
            Member(
                "nonmember@test.com", passwordEncoder.encode("pw"), "비멤버",
                null, Role.USER, Provider.LOCAL, null, false
            )
        )

        trip = Trip("테스트 여행", LocalDate.now(), LocalDate.now().plusDays(5), Country.JAPAN)

        trip.addMember(member1, TripRole.OWNER)
        tripRepository.save(trip)

        // 3. 카테고리 5개 생성 (엔티티 생성자 순서 준수)
        category1_day1 = categoryRepository.save(Category(trip, 1, "숙소 (Day 1)", 1))
        category2_day1 = categoryRepository.save(Category(trip, 1, "식당 (Day 1)", 2))
        category3_day2 = categoryRepository.save(Category(trip, 2, "교통 (Day 2)", 1))
        category4_day2 = categoryRepository.save(Category(trip, 2, "관광 (Day 2)", 2))
    }

    private fun setAuthentication(member: Member) {
        val userDetails = CustomUserDetails(member)
        val authentication = UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities)
        SecurityContextHolder.getContext().authentication = authentication
    }

    @Nested
    @DisplayName("카테고리 생성 (createCategory)")
    inner class CreateCategoryTest {
        @Test
        @DisplayName("성공")
        fun createCategory_success() {
            // given
            setAuthentication(member1)
            val request = CategoryDto.CreateRequest(name = "새 카테고리", day = 3, order = 1)

            // when
            val response = categoryService.createCategory(trip.id!!, request)

            // then
            assertThat(response.categoryId).isNotNull()
            assertThat(response.name).isEqualTo("새 카테고리")
            assertThat(response.day).isEqualTo(3)

            val foundCategory = categoryRepository.findById(response.categoryId).get()
            assertThat(foundCategory.trip.id).isEqualTo(trip.id)
        }

        @Test
        @DisplayName("실패 - 여행(Trip)을 찾을 수 없음")
        fun createCategory_fail_tripNotFound() {
            // given
            setAuthentication(member1)
            val request = CategoryDto.CreateRequest(name = "새 카테고리", day = 1, order = 1)
            val nonExistentTripId = 999L

            // when & then
            val exception = assertThrows<BusinessException> {
                categoryService.createCategory(nonExistentTripId, request)
            }
            assertThat(exception.errorCode).isEqualTo(ErrorCode.NOT_A_TRIP_MEMBER)
        }
    }

    @Nested
    @DisplayName("카테고리 조회 (getCategory / getAllCategories)")
    inner class GetCategoryTest {
        @Test
        @DisplayName("단건 조회 성공")
        fun getCategory_success() {
            setAuthentication(member1)

            // given
            val categoryId = category1_day1.id!!

            // when
            val response = categoryService.getCategory(trip.id!!, categoryId)

            // then
            assertThat(response.categoryId).isEqualTo(categoryId)
            assertThat(response.name).isEqualTo("숙소 (Day 1)")
        }

        @Test
        @DisplayName("목록 조회 성공 - 특정 'day' 기준")
        fun getAllCategories_byDay_success() {
            setAuthentication(member1)

            // when
            val responses = categoryService.getAllCategories(trip.id!!, 1)

            // then
            assertThat(responses).hasSize(2)
            assertThat(responses.map { it.name }).containsExactlyInAnyOrder("숙소 (Day 1)", "식당 (Day 1)")
        }

        @Test
        @DisplayName("목록 조회 성공 - 'day'가 null인 경우 (전체)")
        fun getAllCategories_allDays_success() {
            setAuthentication(member1)
            // when
            val responses = categoryService.getAllCategories(trip.id!!, null)

            // then
            assertThat(responses).hasSize(4)
        }

        @Test
        @DisplayName("단건 조회 실패 - 카테고리를 찾을 수 없음")
        fun getCategory_fail_categoryNotFound() {
            setAuthentication(member1)

            // when & then
            val exception = assertThrows<BusinessException> {
                categoryService.getCategory(trip.id!!, 999L)
            }
            assertThat(exception.errorCode).isEqualTo(ErrorCode.CATEGORY_NOT_FOUND)
        }
    }

    @Nested
    @DisplayName("카테고리 수정 (updateCategory)")
    inner class UpdateCategoryTest {
        @Test
        @DisplayName("성공")
        fun updateCategory_success() {
            // given
            setAuthentication(member1)
            val categoryId = category1_day1.id!!
            val request = CategoryDto.UpdateRequest(name = "수정된 숙소", day = 2, memo = "메모 변경")

            // when
            val response = categoryService.updateCategory(trip.id!!, categoryId, request)

            // then
            assertThat(response.name).isEqualTo("수정된 숙소")
            assertThat(response.day).isEqualTo(2)
            assertThat(response.memo).isEqualTo("메모 변경")
            assertThat(response.order).isEqualTo(category1_day1.order)

            val updatedCategory = categoryRepository.findById(categoryId).get()
            assertThat(updatedCategory.name).isEqualTo("수정된 숙소")
        }

        @Test
        @DisplayName("실패 - 여행 멤버가 아님")
        fun updateCategory_fail_notATripMember() {
            // given
            setAuthentication(nonMember)
            val categoryId = category1_day1.id!!
            val request = CategoryDto.UpdateRequest(name = "해킹 시도")

            // when & then
            val exception = assertThrows<BusinessException> {
                categoryService.updateCategory(trip.id!!, categoryId, request)
            }
            assertThat(exception.errorCode).isEqualTo(ErrorCode.NOT_A_TRIP_MEMBER)
        }
    }

    @Nested
    @DisplayName("카테고리 삭제 (deleteCategory)")
    inner class DeleteCategoryTest {
        @Test
        @DisplayName("성공")
        fun deleteCategory_success() {
            // given
            setAuthentication(member1)
            val categoryId = category1_day1.id!!

            // when
            categoryService.deleteCategory(trip.id!!, categoryId)

            // then
            val exists = categoryRepository.existsById(categoryId)
            assertThat(exists).isFalse()
        }

        @Test
        @DisplayName("실패 - 여행 멤버가 아님")
        fun deleteCategory_fail_notATripMember() {
            // given
            setAuthentication(nonMember) // 비멤버로 인증
            val categoryId = category1_day1.id!!

            // when & then
            val exception = assertThrows<BusinessException> {
                categoryService.deleteCategory(trip.id!!, categoryId)
            }
            assertThat(exception.errorCode).isEqualTo(ErrorCode.NOT_A_TRIP_MEMBER)
        }
    }

    @Nested
    @DisplayName("카테고리 순서 변경 (orderUpdateCategory)")
    inner class OrderUpdateCategoryTest {

        @Test
        @DisplayName("성공 - 특정 'day'의 순서 변경")
        fun orderUpdateCategory_byDay_success() {
            // given
            setAuthentication(member1)
            // Day 1의 순서를 1, 2 -> 2, 1로 변경
            val request = CategoryDto.OrderUpdate(
                items = listOf(
                    CategoryDto.OrderCategory(categoryId = category1_day1.id!!, order = 2), // 숙소 1 -> 2
                    CategoryDto.OrderCategory(categoryId = category2_day1.id!!, order = 1)  // 식당 2 -> 1
                )
            )

            // when
            val response = categoryService.orderUpdateCategory(trip.id!!, request)

            // then
            // 1. 반환된 DTO가 새 순서(1, 2)대로 정렬되었는지 확인
            assertThat(response).hasSize(2)
            assertThat(response.map { it.categoryId }).containsExactly(category2_day1.id!!, category1_day1.id!!)

            // 2. DB에서 직접 확인
            val updatedCat1 = categoryRepository.findById(category1_day1.id!!).get()
            val updatedCat2 = categoryRepository.findById(category2_day1.id!!).get()
            assertThat(updatedCat1.order).isEqualTo(2)
            assertThat(updatedCat2.order).isEqualTo(1)
        }

        @Test
        @DisplayName("실패 - 여행 멤버가 아님")
        fun orderUpdateCategory_fail_notATripMember() {
            // given
            setAuthentication(nonMember)
            val request = CategoryDto.OrderUpdate(items = listOf(
                CategoryDto.OrderCategory(categoryId = category1_day1.id!!, order = 1)
            ))

            // when & then
            val exception = assertThrows<BusinessException> {
                categoryService.orderUpdateCategory(trip.id!!, request)
            }
            assertThat(exception.errorCode).isEqualTo(ErrorCode.NOT_A_TRIP_MEMBER)
        }

        @Test
        @DisplayName("실패 - 요청에 중복된 ID 포함")
        fun orderUpdateCategory_fail_duplicateCategoryId() {
            // given
            setAuthentication(member1)
            val request = CategoryDto.OrderUpdate(
                items = listOf(
                    CategoryDto.OrderCategory(categoryId = category1_day1.id!!, order = 1),
                    CategoryDto.OrderCategory(categoryId = category1_day1.id!!, order = 2) // 중복 ID
                )
            )
            // when & then
            val exception = assertThrows<BusinessException> {
                categoryService.orderUpdateCategory(trip.id!!, request)
            }
            assertThat(exception.errorCode).isEqualTo(ErrorCode.DUPLICATE_CATEGORY_ID_REQUEST)
        }

        @Test
        @DisplayName("실패 - 요청한 'day'와 카테고리의 'day'가 불일치")
        fun orderUpdateCategory_fail_dayMismatch() {
            // given
            setAuthentication(member1)
            // Day 1의 순서를 변경하는데, Day 2의 카테고리(category3_day2) ID를 포함
            val request = CategoryDto.OrderUpdate(
                items = listOf(
                    CategoryDto.OrderCategory(categoryId = category1_day1.id!!, order = 1),
                    CategoryDto.OrderCategory(categoryId = category3_day2.id!!, order = 2)
                )
            )

            // when & then
            val exception = assertThrows<BusinessException> {
                categoryService.orderUpdateCategory(trip.id!!, request)
            }
            assertThat(exception.errorCode).isEqualTo(ErrorCode.CATEGORY_DAY_MISMATCH)
        }
    }
}