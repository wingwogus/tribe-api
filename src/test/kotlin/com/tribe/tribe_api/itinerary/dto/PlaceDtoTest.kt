package com.tribe.tribe_api.itinerary.dto

import io.mockk.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.util.stream.Stream

@DisplayName("PlaceDto Tests")
class PlaceDtoTest {

    @Nested
    @DisplayName("SearchResponse Tests")
    inner class SearchResponseTest {

        @Test
        @DisplayName("Should create SearchResponse with valid places and nextPageToken")
        fun `should create SearchResponse with valid places and nextPageToken`() {
            // Given
            val places = listOf(
                PlaceDto.Simple("1", "Place 1", "Address 1", 1.0, 2.0),
                PlaceDto.Simple("2", "Place 2", "Address 2", 3.0, 4.0)
            )
            val nextPageToken = "next_page_token_123"

            // When
            val searchResponse = PlaceDto.SearchResponse(places, nextPageToken)

            // Then
            assertEquals(places, searchResponse.places)
            assertEquals(nextPageToken, searchResponse.nextPageToken)
            assertEquals(2, searchResponse.places.size)
        }

        @Test
        @DisplayName("Should create SearchResponse with empty places list")
        fun `should create SearchResponse with empty places list`() {
            // Given
            val places = emptyList<PlaceDto.Simple>()
            val nextPageToken = "token"

            // When
            val searchResponse = PlaceDto.SearchResponse(places, nextPageToken)

            // Then
            assertTrue(searchResponse.places.isEmpty())
            assertEquals(nextPageToken, searchResponse.nextPageToken)
        }

        @Test
        @DisplayName("Should create SearchResponse with null nextPageToken")
        fun `should create SearchResponse with null nextPageToken`() {
            // Given
            val places = listOf(PlaceDto.Simple("1", "Place", "Address", 0.0, 0.0))

            // When
            val searchResponse = PlaceDto.SearchResponse(places, null)

            // Then
            assertEquals(places, searchResponse.places)
            assertNull(searchResponse.nextPageToken)
        }

        @Test
        @DisplayName("Should handle large list of places")
        fun `should handle large list of places`() {
            // Given
            val places = (1..1000).map { 
                PlaceDto.Simple("$it", "Place $it", "Address $it", it.toDouble(), (it + 1000).toDouble())
            }

            // When
            val searchResponse = PlaceDto.SearchResponse(places, "large_list_token")

            // Then
            assertEquals(1000, searchResponse.places.size)
            assertEquals("large_list_token", searchResponse.nextPageToken)
        }

        @Test
        @DisplayName("Should support equality comparison")
        fun `should support equality comparison`() {
            // Given
            val places = listOf(PlaceDto.Simple("1", "Place", "Address", 1.0, 2.0))
            val token = "token"
            val response1 = PlaceDto.SearchResponse(places, token)
            val response2 = PlaceDto.SearchResponse(places, token)
            val response3 = PlaceDto.SearchResponse(places, "different_token")

            // Then
            assertEquals(response1, response2)
            assertNotEquals(response1, response3)
        }
    }

    @Nested
    @DisplayName("Simple Tests")
    inner class SimpleTest {

        @Test
        @DisplayName("Should create Simple with all valid parameters")
        fun `should create Simple with all valid parameters`() {
            // Given
            val placeId = "place_123"
            val placeName = "Test Place"
            val address = "123 Test Street"
            val latitude = 37.7749
            val longitude = -122.4194

            // When
            val simple = PlaceDto.Simple(placeId, placeName, address, latitude, longitude)

            // Then
            assertEquals(placeId, simple.placeId)
            assertEquals(placeName, simple.placeName)
            assertEquals(address, simple.address)
            assertEquals(latitude, simple.latitude)
            assertEquals(longitude, simple.longitude)
        }

        @ParameterizedTest
        @DisplayName("Should handle various coordinate values")
        @MethodSource("coordinateProvider")
        fun `should handle various coordinate values`(lat: Double, lon: Double, description: String) {
            // When
            val simple = PlaceDto.Simple("id", "name", "address", lat, lon)

            // Then
            assertEquals(lat, simple.latitude)
            assertEquals(longitude, simple.longitude)
        }

        @Test
        @DisplayName("Should handle empty strings")
        fun `should handle empty strings`() {
            // When
            val simple = PlaceDto.Simple("", "", "", 0.0, 0.0)

            // Then
            assertEquals("", simple.placeId)
            assertEquals("", simple.placeName)
            assertEquals("", simple.address)
        }

        @Test
        @DisplayName("Should handle Unicode characters in strings")
        fun `should handle Unicode characters in strings`() {
            // Given
            val placeId = "플레이스_123"
            val placeName = "테스트 장소 🏢"
            val address = "서울특별시 강남구 123번지"

            // When
            val simple = PlaceDto.Simple(placeId, placeName, address, 37.5665, 126.9780)

            // Then
            assertEquals(placeId, simple.placeId)
            assertEquals(placeName, simple.placeName)
            assertEquals(address, simple.address)
        }

        @Test
        @DisplayName("Should support equality and hashCode")
        fun `should support equality and hashCode`() {
            // Given
            val simple1 = PlaceDto.Simple("1", "Place", "Address", 1.0, 2.0)
            val simple2 = PlaceDto.Simple("1", "Place", "Address", 1.0, 2.0)
            val simple3 = PlaceDto.Simple("2", "Place", "Address", 1.0, 2.0)

            // Then
            assertEquals(simple1, simple2)
            assertEquals(simple1.hashCode(), simple2.hashCode())
            assertNotEquals(simple1, simple3)
        }

        @Test
        @DisplayName("Should have proper toString representation")
        fun `should have proper toString representation`() {
            // Given
            val simple = PlaceDto.Simple("123", "Test Place", "Test Address", 1.5, 2.5)

            // When
            val stringRepresentation = simple.toString()

            // Then
            assertNotNull(stringRepresentation)
            assertTrue(stringRepresentation.contains("123"))
            assertTrue(stringRepresentation.contains("Test Place"))
        }

        companion object {
            @JvmStatic
            fun coordinateProvider(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of(0.0, 0.0, "Zero coordinates"),
                    Arguments.of(90.0, 180.0, "Maximum valid coordinates"),
                    Arguments.of(-90.0, -180.0, "Minimum valid coordinates"),
                    Arguments.of(37.7749, -122.4194, "San Francisco coordinates"),
                    Arguments.of(-33.8688, 151.2093, "Sydney coordinates"),
                    Arguments.of(Double.MAX_VALUE, Double.MIN_VALUE, "Extreme values"),
                    Arguments.of(Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, "Infinity values"),
                    Arguments.of(Double.NaN, Double.NaN, "NaN values")
                )
            }
        }
    }

    @Nested
    @DisplayName("Simple.from() GoogleDto Conversion Tests")
    inner class SimpleFromGoogleDtoTest {

        private lateinit var mockGooglePlace: GoogleDto.GoogleApiResponse.PlaceResult
        private lateinit var mockDisplayName: GoogleDto.GoogleApiResponse.PlaceResult.DisplayName
        private lateinit var mockLocation: GoogleDto.GoogleApiResponse.PlaceResult.Location

        @BeforeEach
        fun setUp() {
            MockKAnnotations.init(this)
            mockGooglePlace = mockk<GoogleDto.GoogleApiResponse.PlaceResult>()
            mockDisplayName = mockk<GoogleDto.GoogleApiResponse.PlaceResult.DisplayName>()
            mockLocation = mockk<GoogleDto.GoogleApiResponse.PlaceResult.Location>()
        }

        @AfterEach
        fun tearDown() {
            unmockkAll()
        }

        @Test
        @DisplayName("Should convert GooglePlace with all valid fields")
        fun `should convert GooglePlace with all valid fields`() {
            // Given
            every { mockGooglePlace.id } returns "google_place_123"
            every { mockGooglePlace.displayName } returns mockDisplayName
            every { mockDisplayName.text } returns "Google Place Name"
            every { mockGooglePlace.formattedAddress } returns "123 Google Street, City"
            every { mockGooglePlace.location } returns mockLocation
            every { mockLocation.latitude } returns 37.7749
            every { mockLocation.longitude } returns -122.4194

            // When
            val result = PlaceDto.Simple.from(mockGooglePlace)

            // Then
            assertEquals("google_place_123", result.placeId)
            assertEquals("Google Place Name", result.placeName)
            assertEquals("123 Google Street, City", result.address)
            assertEquals(37.7749, result.latitude)
            assertEquals(-122.4194, result.longitude)
        }

        @Test
        @DisplayName("Should handle null displayName")
        fun `should handle null displayName`() {
            // Given
            every { mockGooglePlace.id } returns "test_id"
            every { mockGooglePlace.displayName } returns null
            every { mockGooglePlace.formattedAddress } returns "Test Address"
            every { mockGooglePlace.location } returns mockLocation
            every { mockLocation.latitude } returns 1.0
            every { mockLocation.longitude } returns 2.0

            // When
            val result = PlaceDto.Simple.from(mockGooglePlace)

            // Then
            assertEquals("이름 없음", result.placeName)
            assertEquals("test_id", result.placeId)
            assertEquals("Test Address", result.address)
        }

        @Test
        @DisplayName("Should handle null displayName text")
        fun `should handle null displayName text`() {
            // Given
            every { mockGooglePlace.id } returns "test_id"
            every { mockGooglePlace.displayName } returns mockDisplayName
            every { mockDisplayName.text } returns null
            every { mockGooglePlace.formattedAddress } returns "Test Address"
            every { mockGooglePlace.location } returns mockLocation
            every { mockLocation.latitude } returns 1.0
            every { mockLocation.longitude } returns 2.0

            // When
            val result = PlaceDto.Simple.from(mockGooglePlace)

            // Then
            assertEquals("이름 없음", result.placeName)
        }

        @Test
        @DisplayName("Should handle null formattedAddress")
        fun `should handle null formattedAddress`() {
            // Given
            every { mockGooglePlace.id } returns "test_id"
            every { mockGooglePlace.displayName } returns mockDisplayName
            every { mockDisplayName.text } returns "Test Name"
            every { mockGooglePlace.formattedAddress } returns null
            every { mockGooglePlace.location } returns mockLocation
            every { mockLocation.latitude } returns 1.0
            every { mockLocation.longitude } returns 2.0

            // When
            val result = PlaceDto.Simple.from(mockGooglePlace)

            // Then
            assertEquals("주소 정보 없음", result.address)
        }

        @Test
        @DisplayName("Should handle null location")
        fun `should handle null location`() {
            // Given
            every { mockGooglePlace.id } returns "test_id"
            every { mockGooglePlace.displayName } returns mockDisplayName
            every { mockDisplayName.text } returns "Test Name"
            every { mockGooglePlace.formattedAddress } returns "Test Address"
            every { mockGooglePlace.location } returns null

            // When
            val result = PlaceDto.Simple.from(mockGooglePlace)

            // Then
            assertEquals(0.0, result.latitude)
            assertEquals(0.0, result.longitude)
        }

        @Test
        @DisplayName("Should handle null latitude in location")
        fun `should handle null latitude in location`() {
            // Given
            every { mockGooglePlace.id } returns "test_id"
            every { mockGooglePlace.displayName } returns mockDisplayName
            every { mockDisplayName.text } returns "Test Name"
            every { mockGooglePlace.formattedAddress } returns "Test Address"
            every { mockGooglePlace.location } returns mockLocation
            every { mockLocation.latitude } returns null
            every { mockLocation.longitude } returns 2.0

            // When
            val result = PlaceDto.Simple.from(mockGooglePlace)

            // Then
            assertEquals(0.0, result.latitude)
            assertEquals(2.0, result.longitude)
        }

        @Test
        @DisplayName("Should handle null longitude in location")
        fun `should handle null longitude in location`() {
            // Given
            every { mockGooglePlace.id } returns "test_id"
            every { mockGooglePlace.displayName } returns mockDisplayName
            every { mockDisplayName.text } returns "Test Name"
            every { mockGooglePlace.formattedAddress } returns "Test Address"
            every { mockGooglePlace.location } returns mockLocation
            every { mockLocation.latitude } returns 1.0
            every { mockLocation.longitude } returns null

            // When
            val result = PlaceDto.Simple.from(mockGooglePlace)

            // Then
            assertEquals(1.0, result.latitude)
            assertEquals(0.0, result.longitude)
        }

        @Test
        @DisplayName("Should handle all null fields")
        fun `should handle all null fields`() {
            // Given
            every { mockGooglePlace.id } returns "test_id"
            every { mockGooglePlace.displayName } returns null
            every { mockGooglePlace.formattedAddress } returns null
            every { mockGooglePlace.location } returns null

            // When
            val result = PlaceDto.Simple.from(mockGooglePlace)

            // Then
            assertEquals("test_id", result.placeId)
            assertEquals("이름 없음", result.placeName)
            assertEquals("주소 정보 없음", result.address)
            assertEquals(0.0, result.latitude)
            assertEquals(0.0, result.longitude)
        }

        @Test
        @DisplayName("Should handle empty string values from Google DTO")
        fun `should handle empty string values from Google DTO`() {
            // Given
            every { mockGooglePlace.id } returns ""
            every { mockGooglePlace.displayName } returns mockDisplayName
            every { mockDisplayName.text } returns ""
            every { mockGooglePlace.formattedAddress } returns ""
            every { mockGooglePlace.location } returns mockLocation
            every { mockLocation.latitude } returns 0.0
            every { mockLocation.longitude } returns 0.0

            // When
            val result = PlaceDto.Simple.from(mockGooglePlace)

            // Then
            assertEquals("", result.placeId)
            assertEquals("이름 없음", result.placeName) // Empty string is falsy, so default is used
            assertEquals("주소 정보 없음", result.address) // Empty string is falsy, so default is used
        }

        @ParameterizedTest
        @DisplayName("Should handle various coordinate edge cases from Google DTO")
        @ValueSource(doubles = [Double.MAX_VALUE, Double.MIN_VALUE, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY])
        fun `should handle various coordinate edge cases from Google DTO`(coordinate: Double) {
            // Given
            every { mockGooglePlace.id } returns "edge_case_id"
            every { mockGooglePlace.displayName } returns mockDisplayName
            every { mockDisplayName.text } returns "Edge Case Place"
            every { mockGooglePlace.formattedAddress } returns "Edge Case Address"
            every { mockGooglePlace.location } returns mockLocation
            every { mockLocation.latitude } returns coordinate
            every { mockLocation.longitude } returns coordinate

            // When
            val result = PlaceDto.Simple.from(mockGooglePlace)

            // Then
            assertEquals(coordinate, result.latitude)
            assertEquals(coordinate, result.longitude)
        }

        @Test
        @DisplayName("Should handle NaN coordinates from Google DTO")
        fun `should handle NaN coordinates from Google DTO`() {
            // Given
            every { mockGooglePlace.id } returns "nan_id"
            every { mockGooglePlace.displayName } returns mockDisplayName
            every { mockDisplayName.text } returns "NaN Place"
            every { mockGooglePlace.formattedAddress } returns "NaN Address"
            every { mockGooglePlace.location } returns mockLocation
            every { mockLocation.latitude } returns Double.NaN
            every { mockLocation.longitude } returns Double.NaN

            // When
            val result = PlaceDto.Simple.from(mockGooglePlace)

            // Then
            assertTrue(result.latitude.isNaN())
            assertTrue(result.longitude.isNaN())
        }

        @Test
        @DisplayName("Should verify all mock interactions")
        fun `should verify all mock interactions`() {
            // Given
            every { mockGooglePlace.id } returns "verify_id"
            every { mockGooglePlace.displayName } returns mockDisplayName
            every { mockDisplayName.text } returns "Verify Name"
            every { mockGooglePlace.formattedAddress } returns "Verify Address"
            every { mockGooglePlace.location } returns mockLocation
            every { mockLocation.latitude } returns 1.0
            every { mockLocation.longitude } returns 2.0

            // When
            PlaceDto.Simple.from(mockGooglePlace)

            // Then
            verify(exactly = 1) { mockGooglePlace.id }
            verify(exactly = 1) { mockGooglePlace.displayName }
            verify(exactly = 1) { mockDisplayName.text }
            verify(exactly = 1) { mockGooglePlace.formattedAddress }
            verify(exactly = 1) { mockGooglePlace.location }
            verify(exactly = 1) { mockLocation.latitude }
            verify(exactly = 1) { mockLocation.longitude }
        }

        @Test
        @DisplayName("Should create different objects for different Google places")
        fun `should create different objects for different Google places`() {
            // Given
            val mockGooglePlace2 = mockk<GoogleDto.GoogleApiResponse.PlaceResult>()
            val mockDisplayName2 = mockk<GoogleDto.GoogleApiResponse.PlaceResult.DisplayName>()
            val mockLocation2 = mockk<GoogleDto.GoogleApiResponse.PlaceResult.Location>()

            // First place setup
            every { mockGooglePlace.id } returns "place1"
            every { mockGooglePlace.displayName } returns mockDisplayName
            every { mockDisplayName.text } returns "Place 1"
            every { mockGooglePlace.formattedAddress } returns "Address 1"
            every { mockGooglePlace.location } returns mockLocation
            every { mockLocation.latitude } returns 1.0
            every { mockLocation.longitude } returns 2.0

            // Second place setup
            every { mockGooglePlace2.id } returns "place2"
            every { mockGooglePlace2.displayName } returns mockDisplayName2
            every { mockDisplayName2.text } returns "Place 2"
            every { mockGooglePlace2.formattedAddress } returns "Address 2"
            every { mockGooglePlace2.location } returns mockLocation2
            every { mockLocation2.latitude } returns 3.0
            every { mockLocation2.longitude } returns 4.0

            // When
            val result1 = PlaceDto.Simple.from(mockGooglePlace)
            val result2 = PlaceDto.Simple.from(mockGooglePlace2)

            // Then
            assertNotEquals(result1, result2)
            assertEquals("place1", result1.placeId)
            assertEquals("place2", result2.placeId)
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    inner class IntegrationTest {

        @Test
        @DisplayName("Should create complete SearchResponse from multiple Google places")
        fun `should create complete SearchResponse from multiple Google places`() {
            // Given
            val mockGooglePlace1 = mockk<GoogleDto.GoogleApiResponse.PlaceResult>()
            val mockGooglePlace2 = mockk<GoogleDto.GoogleApiResponse.PlaceResult>()
            val mockDisplayName1 = mockk<GoogleDto.GoogleApiResponse.PlaceResult.DisplayName>()
            val mockDisplayName2 = mockk<GoogleDto.GoogleApiResponse.PlaceResult.DisplayName>()
            val mockLocation1 = mockk<GoogleDto.GoogleApiResponse.PlaceResult.Location>()
            val mockLocation2 = mockk<GoogleDto.GoogleApiResponse.PlaceResult.Location>()

            // Setup first place
            every { mockGooglePlace1.id } returns "google1"
            every { mockGooglePlace1.displayName } returns mockDisplayName1
            every { mockDisplayName1.text } returns "Google Place 1"
            every { mockGooglePlace1.formattedAddress } returns "Google Address 1"
            every { mockGooglePlace1.location } returns mockLocation1
            every { mockLocation1.latitude } returns 10.0
            every { mockLocation1.longitude } returns 20.0

            // Setup second place
            every { mockGooglePlace2.id } returns "google2"
            every { mockGooglePlace2.displayName } returns mockDisplayName2
            every { mockDisplayName2.text } returns "Google Place 2"
            every { mockGooglePlace2.formattedAddress } returns "Google Address 2"
            every { mockGooglePlace2.location } returns mockLocation2
            every { mockLocation2.latitude } returns 30.0
            every { mockLocation2.longitude } returns 40.0

            // When
            val simplePlaces = listOf(
                PlaceDto.Simple.from(mockGooglePlace1),
                PlaceDto.Simple.from(mockGooglePlace2)
            )
            val searchResponse = PlaceDto.SearchResponse(simplePlaces, "integration_token")

            // Then
            assertEquals(2, searchResponse.places.size)
            assertEquals("integration_token", searchResponse.nextPageToken)
            assertEquals("google1", searchResponse.places[0].placeId)
            assertEquals("google2", searchResponse.places[1].placeId)
            assertEquals("Google Place 1", searchResponse.places[0].placeName)
            assertEquals("Google Place 2", searchResponse.places[1].placeName)
        }
    }
}

// Mock GoogleDto classes for testing (since they don't exist in our scope)
object GoogleDto {
    object GoogleApiResponse {
        data class PlaceResult(
            val id: String,
            val displayName: DisplayName?,
            val formattedAddress: String?,
            val location: Location?
        ) {
            data class DisplayName(val text: String?)
            data class Location(val latitude: Double?, val longitude: Double?)
        }
    }
}