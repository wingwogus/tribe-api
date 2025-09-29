package com.tribe.tribe_api.itinerary.service

import com.tribe.tribe_api.itinerary.dto.CategoryDto
import com.tribe.tribe_api.itinerary.entity.Category
import com.tribe.tribe_api.itinerary.repository.CategoryRepository
import com.tribe.tribe_api.trip.entity.Country
import com.tribe.tribe_api.trip.entity.Trip
import com.tribe.tribe_api.trip.repository.TripRepository
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import jakarta.persistence.EntityNotFoundException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.test.util.ReflectionTestUtils
import java.time.LocalDate
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Testing library and framework:
 * - JUnit 5 (Jupiter)
 * - MockK for mocking repositories (present in build.gradle)
 * - Spring Test's ReflectionTestUtils to set JPA @Id fields for DTO mapping
 */
@DisplayName("CategoryService unit tests")
class CategoryServiceTest {

    private lateinit var categoryRepository: CategoryRepository
    private lateinit var tripRepository: TripRepository
    private lateinit var service: CategoryService

    private lateinit var trip: Trip

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        categoryRepository = mockk()
        tripRepository = mockk()
        service = CategoryService(categoryRepository, tripRepository)

        trip = Trip(
            title = "Sample Trip",
            startDate = LocalDate.now(),
            endDate = LocalDate.now().plusDays(3),
            country = Country.JAPAN
        )
        ReflectionTestUtils.setField(trip, "id", 100L)
    }

    private fun newCategory(
        id: Long?,
        name: String,
        day: Int,
        order: Int,
        memo: String? = null,
        trp: Trip = this.trip
    ): Category {
        val c = Category(
            trip = trp,
            name = name,
            day = day,
            order = order
        )
        if (id \!= null) ReflectionTestUtils.setField(c, "id", id)
        if (memo \!= null) ReflectionTestUtils.setField(c, "memo", memo)
        return c
    }

    @Nested
    @DisplayName("createCategory")
    inner class CreateCategory {

        @Test
        fun `creates category when trip exists and maps response`() {
            // given
            val req = CategoryDto.CreateRequest(name = "Food", day = 2, order = 3)
            every { tripRepository.findById(100L) } returns Optional.of(trip)

            val saved = newCategory(
                id = 11L,
                name = req.name,
                day = req.day,
                order = req.order
            )
            val savedCaptor = slot<Category>()
            every { categoryRepository.save(capture(savedCaptor)) } returns saved

            // when
            val res = service.createCategory(100L, req)

            // then
            verify(exactly = 1) { tripRepository.findById(100L) }
            verify(exactly = 1) { categoryRepository.save(any()) }

            // verify the entity we attempted to save
            assertEquals("Food", savedCaptor.captured.name)
            assertEquals(2, savedCaptor.captured.day)
            assertEquals(3, savedCaptor.captured.order)

            // verify mapping
            assertEquals(11L, res.categoryId)
            assertEquals("Food", res.name)
            assertEquals(2, res.day)
            assertEquals(3, res.order)
            assertEquals(100L, res.tripId)
            assertTrue(res.itineraryItems.isEmpty())
            assertNotNull(res.createdAt)
            assertNotNull(res.updatedAt)
        }

        @Test
        fun `throws when trip not found`() {
            // given
            val req = CategoryDto.CreateRequest(name = "Any", day = 1, order = 1)
            every { tripRepository.findById(200L) } returns Optional.empty()

            // when & then
            val ex = assertThrows<EntityNotFoundException> {
                service.createCategory(200L, req)
            }
            assertEquals("Trip not found", ex.message)
            verify(exactly = 1) { tripRepository.findById(200L) }
            verify(exactly = 0) { categoryRepository.save(any()) }
        }

        @Test
        fun `handles boundary values`() {
            // given
            val req = CategoryDto.CreateRequest(name = "A", day = 0, order = Int.MAX_VALUE)
            every { tripRepository.findById(100L) } returns Optional.of(trip)
            val saved = newCategory(id = 1L, name = req.name, day = req.day, order = req.order)
            every { categoryRepository.save(any()) } returns saved

            // when
            val res = service.createCategory(100L, req)

            // then
            assertEquals("A", res.name)
            assertEquals(0, res.day)
            assertEquals(Int.MAX_VALUE, res.order)
        }
    }

    @Nested
    @DisplayName("getCategory")
    inner class GetCategory {

        @Test
        fun `returns mapped dto when category exists`() {
            // given
            val cat = newCategory(
                id = 9L,
                name = "Museums",
                day = 1,
                order = 1,
                memo = "Remember tickets",
                trp = trip
            )
            every { categoryRepository.findById(9L) } returns Optional.of(cat)

            // when
            val res = service.getCategory(9L)

            // then
            verify(exactly = 1) { categoryRepository.findById(9L) }
            assertEquals(9L, res.categoryId)
            assertEquals("Museums", res.name)
            assertEquals(1, res.day)
            assertEquals(1, res.order)
            assertEquals(100L, res.tripId)
            assertEquals("Remember tickets", res.memo)
            assertTrue(res.itineraryItems.isEmpty())
        }

        @Test
        fun `throws when category not found`() {
            // given
            every { categoryRepository.findById(1234L) } returns Optional.empty()

            // when & then
            val ex = assertThrows<EntityNotFoundException> {
                service.getCategory(1234L)
            }
            assertEquals("Category not found", ex.message)
        }
    }

    @Nested
    @DisplayName("getAllCategories")
    inner class GetAllCategories {

        @Test
        fun `day null - fetches all by trip ordered by day asc then order asc`() {
            // given
            val list = listOf(
                newCategory(1L, "A", 1, 1),
                newCategory(2L, "B", 1, 2),
                newCategory(3L, "C", 2, 1),
            )
            every { categoryRepository.findAllByTripIdOrderByDayAscOrderAsc(100L) } returns list

            // when
            val res = service.getAllCategories(100L, null)

            // then
            verify(exactly = 1) { categoryRepository.findAllByTripIdOrderByDayAscOrderAsc(100L) }
            verify(exactly = 0) { categoryRepository.findAllByTripIdAndDayOrderByOrderAsc(any(), any()) }
            assertEquals(list.size, res.size)
            assertEquals(list.map { it.name }, res.map { it.name })
        }

        @Test
        fun `with day - fetches by day ordered by order asc`() {
            // given
            val list = listOf(
                newCategory(10L, "D", 3, 1),
                newCategory(11L, "E", 3, 2),
            )
            every { categoryRepository.findAllByTripIdAndDayOrderByOrderAsc(100L, 3) } returns list

            // when
            val res = service.getAllCategories(100L, 3)

            // then
            verify(exactly = 1) { categoryRepository.findAllByTripIdAndDayOrderByOrderAsc(100L, 3) }
            verify(exactly = 0) { categoryRepository.findAllByTripIdOrderByDayAscOrderAsc(any()) }
            assertTrue(res.all { it.day == 3 })
            assertEquals(2, res.size)
        }

        @Test
        fun `returns empty list when repository returns none`() {
            every { categoryRepository.findAllByTripIdOrderByDayAscOrderAsc(100L) } returns emptyList()

            val res = service.getAllCategories(100L, null)

            assertTrue(res.isEmpty())
        }

        @Test
        fun `handles negative day parameter gracefully`() {
            every { categoryRepository.findAllByTripIdAndDayOrderByOrderAsc(100L, -1) } returns emptyList()

            val res = service.getAllCategories(100L, -1)

            assertTrue(res.isEmpty())
            verify(exactly = 1) { categoryRepository.findAllByTripIdAndDayOrderByOrderAsc(100L, -1) }
        }
    }

    @Nested
    @DisplayName("updateCategory")
    inner class UpdateCategory {

        @Test
        fun `updates all provided fields`() {
            // given
            val existing = newCategory(44L, "Original", 1, 10, memo = "m0")
            every { categoryRepository.findById(44L) } returns Optional.of(existing)

            val req = CategoryDto.UpdateRequest(
                name = "Updated",
                day = 2,
                order = 20,
                memo = "m1"
            )

            // when
            val res = service.updateCategory(44L, req)

            // then
            verify(exactly = 1) { categoryRepository.findById(44L) }

            // entity mutated
            assertEquals("Updated", existing.name)
            assertEquals(2, existing.day)
            assertEquals(20, existing.order)
            assertEquals("m1", existing.memo)

            // response mapped
            assertEquals(44L, res.categoryId)
            assertEquals("Updated", res.name)
            assertEquals(2, res.day)
            assertEquals(20, res.order)
            assertEquals("m1", res.memo)
        }

        @Test
        fun `null fields leave entity unchanged`() {
            val existing = newCategory(55L, "Keep", 5, 7, memo = "memo")
            every { categoryRepository.findById(55L) } returns Optional.of(existing)

            val req = CategoryDto.UpdateRequest(
                name = null, day = null, order = null, memo = null
            )

            val res = service.updateCategory(55L, req)

            assertEquals("Keep", existing.name)
            assertEquals(5, existing.day)
            assertEquals(7, existing.order)
            assertEquals("memo", existing.memo)

            assertEquals(55L, res.categoryId)
            assertEquals("Keep", res.name)
            assertEquals(5, res.day)
            assertEquals(7, res.order)
            assertEquals("memo", res.memo)
        }

        @Test
        fun `throws when category not found`() {
            every { categoryRepository.findById(999L) } returns Optional.empty()

            val ex = assertThrows<EntityNotFoundException> {
                service.updateCategory(999L, CategoryDto.UpdateRequest("x", 1, 1, null))
            }
            assertEquals("Category not found", ex.message)
        }
    }

    @Nested
    @DisplayName("deleteCategory")
    inner class DeleteCategory {

        @Test
        fun `deletes when exists`() {
            every { categoryRepository.existsById(70L) } returns true
            every { categoryRepository.deleteById(70L) } just Runs

            service.deleteCategory(70L)

            verify(exactly = 1) { categoryRepository.existsById(70L) }
            verify(exactly = 1) { categoryRepository.deleteById(70L) }
        }

        @Test
        fun `throws when not found`() {
            every { categoryRepository.existsById(71L) } returns false

            val ex = assertThrows<EntityNotFoundException> {
                service.deleteCategory(71L)
            }
            assertEquals("Category not found", ex.message)

            verify(exactly = 1) { categoryRepository.existsById(71L) }
            verify(exactly = 0) { categoryRepository.deleteById(any()) }
        }
    }
}