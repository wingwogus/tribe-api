package com.tribe.tribe_api.itinerary.dto

// Testing framework note:
// - JUnit 5 (kotlin-test-junit5) provides the test engine and assertion utilities.
// - Jakarta Validation validates @NotBlank on CreateRequest, mirroring production constraints.

import com.tribe.tribe_api.itinerary.dto.CategoryDto.CategoryResponse
import com.tribe.tribe_api.itinerary.dto.CategoryDto.CategoryResponse.ItineraryItemResponse
import com.tribe.tribe_api.itinerary.entity.Category
import com.tribe.tribe_api.itinerary.entity.ItineraryItem
import com.tribe.tribe_api.itinerary.entity.Place
import com.tribe.tribe_api.trip.entity.Country
import com.tribe.tribe_api.trip.entity.Trip
import jakarta.validation.ConstraintViolation
import jakarta.validation.Validation
import jakarta.validation.Validator
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@DisplayName("CategoryDto unit tests")
class CategoryDtoTest {

    private lateinit var validator: Validator

    @BeforeEach
    fun setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().validator
    }

    @Nested
    @DisplayName("CreateRequest validation")
    inner class CreateRequestValidation {

        @Test
        fun `valid CreateRequest passes jakarta validation`() {
            val request = CategoryDto.CreateRequest(
                name = "Sightseeing",
                day = 1,
                order = 2
            )

            val violations: Set<ConstraintViolation<CategoryDto.CreateRequest>> = validator.validate(request)
            assertTrue(violations.isEmpty())
            assertEquals("Sightseeing", request.name)
            assertEquals(1, request.day)
            assertEquals(2, request.order)
        }

        @Test
        fun `blank name fails with NotBlank message`() {
            val request = CategoryDto.CreateRequest(
                name = "",
                day = 1,
                order = 1
            )

            val violations = validator.validate(request)
            assertFalse(violations.isEmpty())
            val violation = violations.first()
            assertEquals("name", violation.propertyPath.toString())
            assertEquals("카테고리 이름은 비워둘 수 없습니다.", violation.message)
        }

        @Test
        fun `whitespace-only name fails validation`() {
            val request = CategoryDto.CreateRequest(
                name = "   ",
                day = 0,
                order = -1
            )

            val violations = validator.validate(request)
            assertFalse(violations.isEmpty())
            assertEquals("카테고리 이름은 비워둘 수 없습니다.", violations.first().message)
        }

        @Test
        fun `allows large values and negatives for optional numeric fields`() {
            val longName = "a".repeat(2048)
            val request = CategoryDto.CreateRequest(
                name = longName,
                day = -5,
                order = Int.MAX_VALUE
            )

            val violations = validator.validate(request)
            assertTrue(violations.isEmpty())
            assertEquals(longName.length, request.name.length)
            assertEquals(-5, request.day)
            assertEquals(Int.MAX_VALUE, request.order)
        }
    }

    @Nested
    @DisplayName("CategoryResponse mapping from Category entity")
    inner class CategoryResponseMapping {

        @Test
        fun `maps Category with populated itinerary items`() {
            val now = LocalDateTime.now()
            val trip = createTripWithId(10L)
            val category = createCategoryWithId(
                id = 1L,
                trip = trip,
                name = "Attractions",
                day = 2,
                order = 3,
                memo = "Category memo",
                createdAt = now,
                updatedAt = now
            )
            val mainItem = createItineraryItemWithId(
                id = 100L,
                category = category,
                placeName = "N Seoul Tower",
                order = 1,
                memo = "Observation deck visit"
            )
            category.itineraryItems = mutableListOf(mainItem)

            val response = CategoryResponse.from(category)

            assertEquals(1L, response.categoryId)
            assertEquals("Attractions", response.name)
            assertEquals(2, response.day)
            assertEquals(3, response.order)
            assertEquals(10L, response.tripId)
            assertEquals("Category memo", response.memo)
            assertEquals(now, response.createdAt)
            assertEquals(now, response.updatedAt)
            assertEquals(1, response.itineraryItems.size)

            response.itineraryItems.first().apply {
                assertEquals(100L, itemId)
                assertEquals("N Seoul Tower", placeName)
                assertEquals(1, order)
                assertEquals("Observation deck visit", memo)
            }
        }

        @Test
        fun `maps Category with empty items and null memo`() {
            val now = LocalDateTime.now()
            val trip = createTripWithId(11L)
            val category = createCategoryWithId(
                id = 2L,
                trip = trip,
                name = "Empty Day",
                day = 0,
                order = 0,
                memo = null,
                createdAt = now,
                updatedAt = now
            )
            category.itineraryItems = mutableListOf()

            val response = CategoryResponse.from(category)

            assertEquals(2L, response.categoryId)
            assertNull(response.memo)
            assertTrue(response.itineraryItems.isEmpty())
        }

        @Test
        fun `maps Category with diverse itinerary items including nullables`() {
            val trip = createTripWithId(12L)
            val category = createCategoryWithId(
                id = 3L,
                trip = trip,
                name = "Multi",
                day = 3,
                order = 1,
                memo = "has items",
                createdAt = LocalDateTime.MIN,
                updatedAt = LocalDateTime.MAX
            )
            val first = createItineraryItemWithId(1L, category, "N Seoul Tower", 1, "first memo")
            val second = createItineraryItemWithId(2L, category, "Gyeongbokgung", 2, "second memo")
            val third = createItineraryItemWithId(3L, category, null, 3, null)
            category.itineraryItems = mutableListOf(first, second, third)

            val response = CategoryResponse.from(category)

            assertEquals(3, response.itineraryItems.size)
            response.itineraryItems[0].apply {
                assertEquals(1L, itemId)
                assertEquals("N Seoul Tower", placeName)
                assertEquals(1, order)
                assertEquals("first memo", memo)
            }
            response.itineraryItems[1].apply {
                assertEquals(2L, itemId)
                assertEquals("Gyeongbokgung", placeName)
                assertEquals(2, order)
                assertEquals("second memo", memo)
            }
            response.itineraryItems[2].apply {
                assertEquals(3L, itemId)
                assertNull(placeName)
                assertEquals(3, order)
                assertNull(memo)
            }
        }
    }

    @Nested
    @DisplayName("ItineraryItemResponse mapping")
    inner class ItineraryItemResponseMapping {

        @Test
        fun `maps itinerary item with place and memo`() {
            val category = createCategoryWithId(
                id = 5L,
                trip = createTripWithId(20L),
                name = "Day 1",
                day = 1,
                order = 1,
                memo = null,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )
            val item = createItineraryItemWithId(
                id = 7L,
                category = category,
                placeName = "COEX",
                order = 9,
                memo = "Meet friends"
            )

            val response = ItineraryItemResponse.from(item)

            assertEquals(7L, response.itemId)
            assertEquals("COEX", response.placeName)
            assertEquals(9, response.order)
            assertEquals("Meet friends", response.memo)
        }

        @Test
        fun `maps itinerary item with null place and null memo`() {
            val category = createCategoryWithId(
                id = 6L,
                trip = createTripWithId(21L),
                name = "Day 2",
                day = 2,
                order = 2,
                memo = "Notes",
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )
            val item = createItineraryItemWithId(
                id = 8L,
                category = category,
                placeName = null,
                order = 0,
                memo = null
            )

            val response = ItineraryItemResponse.from(item)

            assertEquals(8L, response.itemId)
            assertNull(response.placeName)
            assertEquals(0, response.order)
            assertNull(response.memo)
        }
    }

    @Nested
    @DisplayName("UpdateRequest construction")
    inner class UpdateRequestConstruction {

        @Test
        fun `allows setting all fields`() {
            val request = CategoryDto.UpdateRequest(
                name = "Updated name",
                day = 2,
                order = 10,
                memo = "Updated memo"
            )

            assertEquals("Updated name", request.name)
            assertEquals(2, request.day)
            assertEquals(10, request.order)
            assertEquals("Updated memo", request.memo)
        }

        @Test
        fun `allows all fields to be null`() {
            val request = CategoryDto.UpdateRequest(
                name = null,
                day = null,
                order = null,
                memo = null
            )

            assertNull(request.name)
            assertNull(request.day)
            assertNull(request.order)
            assertNull(request.memo)
        }

        @Test
        fun `allows partial updates`() {
            val request = CategoryDto.UpdateRequest(
                name = "Rename only",
                day = null,
                order = 5,
                memo = null
            )

            assertEquals("Rename only", request.name)
            assertNull(request.day)
            assertEquals(5, request.order)
            assertNull(request.memo)
        }
    }

    private fun createTripWithId(
        id: Long,
        title: String = "테스트 여행",
        startDate: LocalDate = LocalDate.of(2024, 1, 1),
        endDate: LocalDate = startDate.plusDays(3),
        country: Country = Country.JAPAN
    ): Trip {
        return Trip(title, startDate, endDate, country).apply {
            setField("id", id)
        }
    }

    private fun createCategoryWithId(
        id: Long,
        trip: Trip,
        name: String,
        day: Int,
        order: Int,
        memo: String?,
        createdAt: LocalDateTime,
        updatedAt: LocalDateTime
    ): Category {
        return Category(trip, day, name, order).apply {
            setField("id", id)
            this.memo = memo
            this.createdAt = createdAt
            this.updatedAt = updatedAt
        }
    }

    private fun createItineraryItemWithId(
        id: Long,
        category: Category,
        placeName: String?,
        order: Int,
        memo: String?
    ): ItineraryItem {
        val place = placeName?.let { createPlace(it) }
        return ItineraryItem(category, place, order, memo).apply {
            setField("id", id)
        }
    }

    private fun createPlace(name: String): Place {
        return Place(
            "place-${name.hashCode()}",
            name,
            "$name address",
            BigDecimal("37.5665"),
            BigDecimal("126.9780")
        )
    }

    private fun Any.setField(fieldName: String, value: Any?) {
        var current: Class<*>? = this::class.java
        while (current \!= null) {
            try {
                val field = current.getDeclaredField(fieldName)
                field.isAccessible = true
                field.set(this, value)
                return
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        fail("Field '$fieldName' not found on ${this::class.java.name}")
    }
}