package com.tribe.tribe_api.itinerary.service

/*
Testing stack:
- JUnit 5 (useJUnitPlatform)
- MockK for mocking (pure unit tests; no Spring context)
- spring-boot-starter-test available but unused here to keep tests fast/unit-scoped
*/

import com.tribe.tribe_api.itinerary.dto.WishlistDto
import com.tribe.tribe_api.itinerary.entity.Place
import com.tribe.tribe_api.itinerary.entity.WishlistItem
import com.tribe.tribe_api.itinerary.repository.PlaceRepository
import com.tribe.tribe_api.itinerary.repository.WishlistItemRepository
import com.tribe.tribe_api.member.entity.Member
import com.tribe.tribe_api.trip.entity.Trip
import com.tribe.tribe_api.trip.entity.TripMember
import com.tribe.tribe_api.trip.repository.TripMemberRepository
import com.tribe.tribe_api.trip.repository.TripRepository
import io.mockk.*
import io.mockk.junit5.MockKExtension
import jakarta.persistence.EntityNotFoundException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import java.math.BigDecimal
import java.util.*

@ExtendWith(MockKExtension::class)
@DisplayName("WishlistService unit tests")
class WishlistServiceTest {

    @MockK lateinit var wishlistItemRepository: WishlistItemRepository
    @MockK lateinit var placeRepository: PlaceRepository
    @MockK lateinit var tripMemberRepository: TripMemberRepository
    @MockK lateinit var tripRepository: TripRepository

    private lateinit var service: WishlistService

    // Common fixtures/mocks
    private lateinit var member: Member
    private lateinit var trip: Trip
    private lateinit var tripMember: TripMember
    private lateinit var existingPlace: Place
    private lateinit var saveReturnWishlistItem: WishlistItem
    private lateinit var addRequest: WishlistDto.WishListAddRequest
    private lateinit var pageable: Pageable

    @BeforeEach
    fun setup() {
        service = WishlistService(
            wishlistItemRepository,
            placeRepository,
            tripMemberRepository,
            tripRepository
        )

        // Use mocks for complex JPA entities we don't need to fully construct
        member = mockk(relaxed = true)
        trip = mockk(relaxed = true)
        tripMember = mockk(relaxed = true)

        existingPlace = Place(
            externalPlaceId = "place123",
            name = "Test Restaurant",
            address = "123 Test Street",
            latitude = BigDecimal("37.7749000"),
            longitude = BigDecimal("-122.4194000")
        )

        // Mock WishlistItem returned from repo.save (id + place used by DTO.from)
        saveReturnWishlistItem = mockk(relaxed = true) {
            every { id } returns 1L
            every { place } returns existingPlace
        }

        addRequest = WishlistDto.WishListAddRequest(
            placeId = "place123",
            placeName = "Test Restaurant",
            address = "123 Test Street",
            latitude = BigDecimal("37.7749000"),
            longitude = BigDecimal("-122.4194000")
        )

        pageable = PageRequest.of(0, 10)
        clearAllMocks()
    }

    @Nested
    @DisplayName("addWishList")
    inner class AddWishListTests {

        @Test
        @DisplayName("Adds wishlist item when place exists")
        fun add_withExistingPlace_success() {
            every { tripRepository.findById(1L) } returns Optional.of(trip)
            every { tripMemberRepository.findByTripAndMember(trip, member) } returns Optional.of(tripMember)
            every { placeRepository.findByExternalPlaceId("place123") } returns existingPlace
            every { wishlistItemRepository.save(any()) } returns saveReturnWishlistItem

            // Mock companion object mapping
            mockkObject(WishlistDto.WishlistItemDto.Companion)
            val dto = WishlistDto.WishlistItemDto(
                wishlistItemId = 1L,
                placeId = "place123",
                name = "Test Restaurant",
                address = "123 Test Street",
                latitude = BigDecimal("37.7749000"),
                longitude = BigDecimal("-122.4194000")
            )
            every { WishlistDto.WishlistItemDto.from(saveReturnWishlistItem) } returns dto

            val result = service.addWishList(member, 1L, addRequest)

            assertNotNull(result)
            assertEquals(1L, result.wishlistItemId)
            assertEquals("place123", result.placeId)
            assertEquals("Test Restaurant", result.name)

            verify(exactly = 1) { tripRepository.findById(1L) }
            verify(exactly = 1) { tripMemberRepository.findByTripAndMember(trip, member) }
            verify(exactly = 1) { placeRepository.findByExternalPlaceId("place123") }
            verify(exactly = 1) { wishlistItemRepository.save(any()) }
        }

        @Test
        @DisplayName("Creates new place and adds wishlist item when place not found")
        fun add_withNewPlace_createsAndSaves() {
            val newReq = WishlistDto.WishListAddRequest(
                placeId = "place456",
                placeName = "New Restaurant",
                address = "456 New Street",
                latitude = BigDecimal("37.7849000"),
                longitude = BigDecimal("-122.4094000")
            )
            val placeSlot = slot<Place>()

            every { tripRepository.findById(1L) } returns Optional.of(trip)
            every { tripMemberRepository.findByTripAndMember(trip, member) } returns Optional.of(tripMember)
            every { placeRepository.findByExternalPlaceId("place456") } returns null
            every { placeRepository.save(capture(placeSlot)) } answers { placeSlot.captured }
            every { wishlistItemRepository.save(any()) } returns saveReturnWishlistItem

            mockkObject(WishlistDto.WishlistItemDto.Companion)
            val dto = WishlistDto.WishlistItemDto(
                wishlistItemId = 2L,
                placeId = "place456",
                name = "New Restaurant",
                address = "456 New Street",
                latitude = BigDecimal("37.7849000"),
                longitude = BigDecimal("-122.4094000")
            )
            every { WishlistDto.WishlistItemDto.from(saveReturnWishlistItem) } returns dto

            val result = service.addWishList(member, 1L, newReq)

            assertNotNull(result)
            assertEquals("place456", result.placeId)
            assertEquals("New Restaurant", result.name)

            // Verify a Place was constructed from request and saved
            assertEquals("place456", placeSlot.captured.externalPlaceId)
            assertEquals("New Restaurant", placeSlot.captured.name)
            assertEquals("456 New Street", placeSlot.captured.address)
            assertEquals(BigDecimal("37.7849000"), placeSlot.captured.latitude)
            assertEquals(BigDecimal("-122.4094000"), placeSlot.captured.longitude)

            verify(exactly = 1) { placeRepository.save(any<Place>()) }
            verify(exactly = 1) { wishlistItemRepository.save(any()) }
        }

        @Test
        @DisplayName("Throws when trip not found")
        fun add_tripMissing_throws() {
            every { tripRepository.findById(1L) } returns Optional.empty()

            assertThrows<NoSuchElementException> {
                service.addWishList(member, 1L, addRequest)
            }

            verify(exactly = 1) { tripRepository.findById(1L) }
            verify(exactly = 0) { tripMemberRepository.findByTripAndMember(any(), any()) }
            verify(exactly = 0) { placeRepository.findByExternalPlaceId(any()) }
        }

        @Test
        @DisplayName("Throws when trip member not found")
        fun add_tripMemberMissing_throws() {
            every { tripRepository.findById(1L) } returns Optional.of(trip)
            every { tripMemberRepository.findByTripAndMember(trip, member) } returns Optional.empty()

            assertThrows<NoSuchElementException> {
                service.addWishList(member, 1L, addRequest)
            }

            verify(exactly = 1) { tripRepository.findById(1L) }
            verify(exactly = 1) { tripMemberRepository.findByTripAndMember(trip, member) }
            verify(exactly = 0) { placeRepository.findByExternalPlaceId(any()) }
        }

        @Test
        @DisplayName("Propagates repository exception")
        fun add_repositoryException_propagates() {
            every { tripRepository.findById(1L) } throws RuntimeException("DB down")

            assertThrows<RuntimeException> {
                service.addWishList(member, 1L, addRequest)
            }

            verify(exactly = 1) { tripRepository.findById(1L) }
        }
    }

    @Nested
    @DisplayName("searchWishList")
    inner class SearchWishListTests {

        @Test
        @DisplayName("Returns mapped search result")
        fun search_success() {
            val page = PageImpl(listOf(saveReturnWishlistItem), pageable, 1)
            every { wishlistItemRepository
                .findAllByTrip_IdAndPlace_NameContainingIgnoreCase(1L, "test", pageable) } returns page

            mockkObject(WishlistDto.WishlistSearchResponse.Companion)
            val itemDto = WishlistDto.WishlistItemDto(
                wishlistItemId = 1L,
                placeId = "place123",
                name = "Test Restaurant",
                address = "123 Test Street",
                latitude = BigDecimal("37.7749000"),
                longitude = BigDecimal("-122.4194000")
            )
            val mapped = WishlistDto.WishlistSearchResponse(
                content = listOf(itemDto),
                pageNumber = 0,
                pageSize = 10,
                totalPages = 1,
                totalElements = 1L,
                isLast = true
            )
            every { WishlistDto.WishlistSearchResponse.from(page) } returns mapped

            val result = service.searchWishList(1L, "test", pageable)

            assertEquals(1L, result.totalElements)
            assertEquals(1, result.totalPages)
            assertEquals(0, result.pageNumber)
            assertEquals(10, result.pageSize)

            verify(exactly = 1) {
                wishlistItemRepository
                    .findAllByTrip_IdAndPlace_NameContainingIgnoreCase(1L, "test", pageable)
            }
        }

        @Test
        @DisplayName("Empty result when no matches")
        fun search_noMatches_empty() {
            val emptyPage = PageImpl<WishlistItem>(emptyList(), pageable, 0)
            every { wishlistItemRepository
                .findAllByTrip_IdAndPlace_NameContainingIgnoreCase(1L, "none", pageable) } returns emptyPage

            mockkObject(WishlistDto.WishlistSearchResponse.Companion)
            val mapped = WishlistDto.WishlistSearchResponse(
                content = emptyList(),
                pageNumber = 0,
                pageSize = 10,
                totalPages = 0,
                totalElements = 0L,
                isLast = true
            )
            every { WishlistDto.WishlistSearchResponse.from(emptyPage) } returns mapped

            val result = service.searchWishList(1L, "none", pageable)

            assertTrue(result.content.isEmpty())
            assertEquals(0L, result.totalElements)

            verify(exactly = 1) {
                wishlistItemRepository
                    .findAllByTrip_IdAndPlace_NameContainingIgnoreCase(1L, "none", pageable)
            }
        }

        @Test
        @DisplayName("Handles special characters in query")
        fun search_specialChars_ok() {
            val q = "café@#1"
            val page = PageImpl<WishlistItem>(emptyList(), pageable, 0)
            every { wishlistItemRepository
                .findAllByTrip_IdAndPlace_NameContainingIgnoreCase(1L, q, pageable) } returns page

            mockkObject(WishlistDto.WishlistSearchResponse.Companion)
            every { WishlistDto.WishlistSearchResponse.from(page) } returns WishlistDto.WishlistSearchResponse(
                content = emptyList(),
                pageNumber = 0,
                pageSize = 10,
                totalPages = 0,
                totalElements = 0L,
                isLast = true
            )

            val result = service.searchWishList(1L, q, pageable)
            assertNotNull(result)

            verify(exactly = 1) {
                wishlistItemRepository
                    .findAllByTrip_IdAndPlace_NameContainingIgnoreCase(1L, q, pageable)
            }
        }
    }

    @Nested
    @DisplayName("getWishList")
    inner class GetWishListTests {

        @Test
        @DisplayName("Returns mapped page for trip")
        fun get_success() {
            val page = PageImpl(listOf(saveReturnWishlistItem), pageable, 1)
            every { wishlistItemRepository.findAllByTrip_Id(1L, pageable) } returns page

            mockkObject(WishlistDto.WishlistSearchResponse.Companion)
            val dto = WishlistDto.WishlistItemDto(
                wishlistItemId = 1L,
                placeId = "place123",
                name = "Test Restaurant",
                address = "123 Test Street",
                latitude = BigDecimal("37.7749000"),
                longitude = BigDecimal("-122.4194000")
            )
            val mapped = WishlistDto.WishlistSearchResponse(
                content = listOf(dto),
                pageNumber = 0,
                pageSize = 10,
                totalPages = 1,
                totalElements = 1L,
                isLast = true
            )
            every { WishlistDto.WishlistSearchResponse.from(page) } returns mapped

            val result = service.getWishList(1L, pageable)

            assertEquals(1L, result.totalElements)
            assertEquals(1, result.totalPages)
            assertEquals(10, result.pageSize)
            assertEquals(0, result.pageNumber)

            verify(exactly = 1) { wishlistItemRepository.findAllByTrip_Id(1L, pageable) }
        }

        @Test
        @DisplayName("Empty list when no items exist")
        fun get_empty() {
            val emptyPage = PageImpl<WishlistItem>(emptyList(), pageable, 0)
            every { wishlistItemRepository.findAllByTrip_Id(1L, pageable) } returns emptyPage

            mockkObject(WishlistDto.WishlistSearchResponse.Companion)
            every { WishlistDto.WishlistSearchResponse.from(emptyPage) } returns WishlistDto.WishlistSearchResponse(
                content = emptyList(),
                pageNumber = 0,
                pageSize = 10,
                totalPages = 0,
                totalElements = 0L,
                isLast = true
            )

            val result = service.getWishList(1L, pageable)

            assertTrue(result.content.isEmpty())
            assertEquals(0L, result.totalElements)

            verify(exactly = 1) { wishlistItemRepository.findAllByTrip_Id(1L, pageable) }
        }

        @Test
        @DisplayName("Handles large page size")
        fun get_largePageSize() {
            val big = PageRequest.of(0, 1000)
            val emptyPage = PageImpl<WishlistItem>(emptyList(), big, 0)
            every { wishlistItemRepository.findAllByTrip_Id(1L, big) } returns emptyPage

            mockkObject(WishlistDto.WishlistSearchResponse.Companion)
            every { WishlistDto.WishlistSearchResponse.from(emptyPage) } returns WishlistDto.WishlistSearchResponse(
                content = emptyList(),
                pageNumber = 0,
                pageSize = 1000,
                totalPages = 0,
                totalElements = 0L,
                isLast = true
            )

            val result = service.getWishList(1L, big)
            assertEquals(1000, result.pageSize)

            verify(exactly = 1) { wishlistItemRepository.findAllByTrip_Id(1L, big) }
        }

        @Test
        @DisplayName("Handles different page numbers")
        fun get_differentPageNumber() {
            val p2 = PageRequest.of(1, 5)
            val emptyPage = PageImpl<WishlistItem>(emptyList(), p2, 0)
            every { wishlistItemRepository.findAllByTrip_Id(1L, p2) } returns emptyPage

            mockkObject(WishlistDto.WishlistSearchResponse.Companion)
            every { WishlistDto.WishlistSearchResponse.from(emptyPage) } returns WishlistDto.WishlistSearchResponse(
                content = emptyList(),
                pageNumber = 1,
                pageSize = 5,
                totalPages = 0,
                totalElements = 0L,
                isLast = true
            )

            val result = service.getWishList(1L, p2)
            assertEquals(1, result.pageNumber)
            assertEquals(5, result.pageSize)

            verify(exactly = 1) { wishlistItemRepository.findAllByTrip_Id(1L, p2) }
        }
    }

    @Nested
    @DisplayName("deleteWishlistItem")
    inner class DeleteWishlistItemTests {

        @Test
        @DisplayName("Deletes when exists")
        fun delete_exists_deletes() {
            every { wishlistItemRepository.existsById(1L) } returns true
            every { wishlistItemRepository.deleteById(1L) } just Runs

            service.deleteWishlistItem(1L)

            verify(exactly = 1) { wishlistItemRepository.existsById(1L) }
            verify(exactly = 1) { wishlistItemRepository.deleteById(1L) }
        }

        @Test
        @DisplayName("Throws EntityNotFoundException when missing")
        fun delete_missing_throws() {
            every { wishlistItemRepository.existsById(999L) } returns false

            val ex = assertThrows<EntityNotFoundException> {
                service.deleteWishlistItem(999L)
            }
            assertEquals("해당 위시리스트 항목을 찾을 수 없습니다. id: 999", ex.message)

            verify(exactly = 1) { wishlistItemRepository.existsById(999L) }
            verify(exactly = 0) { wishlistItemRepository.deleteById(any()) }
        }

        @Test
        @DisplayName("Handles zero and negative IDs")
        fun delete_zeroAndNegative_throws() {
            every { wishlistItemRepository.existsById(0L) } returns false
            every { wishlistItemRepository.existsById(-1L) } returns false

            val ex0 = assertThrows<EntityNotFoundException> { service.deleteWishlistItem(0L) }
            val exNeg = assertThrows<EntityNotFoundException> { service.deleteWishlistItem(-1L) }

            assertEquals("해당 위시리스트 항목을 찾을 수 없습니다. id: 0", ex0.message)
            assertEquals("해당 위시리스트 항목을 찾을 수 없습니다. id: -1", exNeg.message)

            verify(exactly = 1) { wishlistItemRepository.existsById(0L) }
            verify(exactly = 1) { wishlistItemRepository.existsById(-1L) }
            verify(exactly = 0) { wishlistItemRepository.deleteById(any()) }
        }
    }

    @Nested
    @DisplayName("deleteWishlistItems")
    inner class DeleteWishlistItemsTests {

        @Test
        @DisplayName("Deletes provided IDs in batch")
        fun delete_batch_callsRepo() {
            val ids = listOf(1L, 2L, 3L)
            every { wishlistItemRepository.deleteAllByIdInBatch(ids) } just Runs

            service.deleteWishlistItems(ids)

            verify(exactly = 1) { wishlistItemRepository.deleteAllByIdInBatch(ids) }
        }

        @Test
        @DisplayName("Handles empty list")
        fun delete_emptyList_ok() {
            val ids = emptyList<Long>()
            every { wishlistItemRepository.deleteAllByIdInBatch(ids) } just Runs

            service.deleteWishlistItems(ids)

            verify(exactly = 1) { wishlistItemRepository.deleteAllByIdInBatch(ids) }
        }

        @Test
        @DisplayName("Handles duplicates and mixed IDs")
        fun delete_duplicates_ok() {
            val ids = listOf(1L, 2L, 1L, 3L, 2L, 0L, -5L)
            every { wishlistItemRepository.deleteAllByIdInBatch(ids) } just Runs

            service.deleteWishlistItems(ids)

            verify(exactly = 1) { wishlistItemRepository.deleteAllByIdInBatch(ids) }
        }
    }
}