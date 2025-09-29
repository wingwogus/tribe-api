package com.tribe.tribe_api.itinerary.dto

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

private data class CustomItem(val id: Long, val name: String)
private data class NestedCustomItem(val id: Long, val items: List<String>)

@DisplayName("PageResponseDto")
class PageResponseDtoTest {

    @Nested
    @DisplayName("Construction")
    inner class ConstructionTests {

        @Test
        @DisplayName("creates instance with valid parameters")
        fun createsInstanceWithValidParameters() {
            val content = listOf("item1", "item2", "item3")
            val pageResponse = PageResponseDto.PageResponseDto(
                content = content,
                pageNumber = 0,
                pageSize = 10,
                totalPages = 5,
                totalElements = 50L,
                isLast = false
            )

            assertAll(
                { assertEquals(content, pageResponse.content) },
                { assertEquals(0, pageResponse.pageNumber) },
                { assertEquals(10, pageResponse.pageSize) },
                { assertEquals(5, pageResponse.totalPages) },
                { assertEquals(50L, pageResponse.totalElements) },
                { assertFalse(pageResponse.isLast) }
            )
        }

        @Test
        @DisplayName("creates instance with empty content")
        fun createsInstanceWithEmptyContent() {
            val pageResponse = PageResponseDto.PageResponseDto(
                content = emptyList<String>(),
                pageNumber = 0,
                pageSize = 10,
                totalPages = 0,
                totalElements = 0L,
                isLast = true
            )

            assertAll(
                { assertTrue(pageResponse.content.isEmpty()) },
                { assertEquals(0, pageResponse.pageNumber) },
                { assertEquals(10, pageResponse.pageSize) },
                { assertEquals(0, pageResponse.totalPages) },
                { assertEquals(0L, pageResponse.totalElements) },
                { assertTrue(pageResponse.isLast) }
            )
        }

        @Test
        @DisplayName("supports generic content types")
        fun supportsGenericContentTypes() {
            val intResponse = PageResponseDto.PageResponseDto(
                content = listOf(1, 2, 3),
                pageNumber = 0,
                pageSize = 3,
                totalPages = 1,
                totalElements = 3L,
                isLast = true
            )
            val customResponse = PageResponseDto.PageResponseDto(
                content = listOf(CustomItem(1L, "alpha"), CustomItem(2L, "beta")),
                pageNumber = 0,
                pageSize = 2,
                totalPages = 1,
                totalElements = 2L,
                isLast = true
            )

            assertAll(
                { assertEquals(listOf(1, 2, 3), intResponse.content) },
                { assertEquals(2, customResponse.content.size) },
                { assertEquals(CustomItem(1L, "alpha"), customResponse.content.first()) },
                { assertEquals(CustomItem(2L, "beta"), customResponse.content.last()) }
            )
        }
    }

    @Nested
    @DisplayName("Edge cases")
    inner class EdgeCases {

        @Test
        @DisplayName("handles first page correctly")
        fun handlesFirstPageCorrectly() {
            val firstPage = PageResponseDto.PageResponseDto(
                content = listOf("item1", "item2"),
                pageNumber = 0,
                pageSize = 10,
                totalPages = 3,
                totalElements = 25L,
                isLast = false
            )

            assertAll(
                { assertEquals(0, firstPage.pageNumber) },
                { assertFalse(firstPage.isLast) }
            )
        }

        @Test
        @DisplayName("handles last page correctly")
        fun handlesLastPageCorrectly() {
            val lastPage = PageResponseDto.PageResponseDto(
                content = listOf("item1"),
                pageNumber = 2,
                pageSize = 10,
                totalPages = 3,
                totalElements = 21L,
                isLast = true
            )

            assertAll(
                { assertEquals(2, lastPage.pageNumber) },
                { assertTrue(lastPage.isLast) }
            )
        }

        @Test
        @DisplayName("handles single page scenario")
        fun handlesSinglePageScenario() {
            val singlePage = PageResponseDto.PageResponseDto(
                content = listOf("only-item"),
                pageNumber = 0,
                pageSize = 10,
                totalPages = 1,
                totalElements = 1L,
                isLast = true
            )

            assertAll(
                { assertEquals(1, singlePage.totalPages) },
                { assertTrue(singlePage.isLast) }
            )
        }

        @Test
        @DisplayName("handles large numeric values")
        fun handlesLargeNumericValues() {
            val largePage = PageResponseDto.PageResponseDto(
                content = emptyList<String>(),
                pageNumber = Int.MAX_VALUE,
                pageSize = Int.MAX_VALUE,
                totalPages = Int.MAX_VALUE,
                totalElements = Long.MAX_VALUE,
                isLast = true
            )

            assertAll(
                { assertEquals(Int.MAX_VALUE, largePage.pageNumber) },
                { assertEquals(Int.MAX_VALUE, largePage.pageSize) },
                { assertEquals(Int.MAX_VALUE, largePage.totalPages) },
                { assertEquals(Long.MAX_VALUE, largePage.totalElements) },
                { assertTrue(largePage.isLast) }
            )
        }

        @Test
        @DisplayName("retains negative numeric values when provided")
        fun retainsNegativeNumericValuesWhenProvided() {
            val negativePage = PageResponseDto.PageResponseDto(
                content = emptyList<String>(),
                pageNumber = -1,
                pageSize = -5,
                totalPages = -2,
                totalElements = -10L,
                isLast = false
            )

            assertAll(
                { assertEquals(-1, negativePage.pageNumber) },
                { assertEquals(-5, negativePage.pageSize) },
                { assertEquals(-2, negativePage.totalPages) },
                { assertEquals(-10L, negativePage.totalElements) },
                { assertFalse(negativePage.isLast) }
            )
        }
    }

    @Nested
    @DisplayName("Data class semantics")
    inner class DataClassSemantics {

        @Test
        @DisplayName("supports equality comparisons")
        fun supportsEqualityComparisons() {
            val content = listOf("a", "b")
            val first = PageResponseDto.PageResponseDto(content, 0, 10, 1, 2L, true)
            val second = PageResponseDto.PageResponseDto(content, 0, 10, 1, 2L, true)
            val different = PageResponseDto.PageResponseDto(listOf("a"), 0, 10, 1, 1L, true)

            assertAll(
                { assertEquals(first, second) },
                { assertNotEquals(first, different) }
            )
        }

        @Test
        @DisplayName("provides consistent hashCode")
        fun providesConsistentHashCode() {
            val content = listOf("a", "b")
            val first = PageResponseDto.PageResponseDto(content, 0, 10, 1, 2L, true)
            val second = PageResponseDto.PageResponseDto(content, 0, 10, 1, 2L, true)

            assertEquals(first.hashCode(), second.hashCode())
        }

        @Test
        @DisplayName("includes all properties in toString")
        fun includesAllPropertiesInToString() {
            val page = PageResponseDto.PageResponseDto(
                content = listOf("item1"),
                pageNumber = 1,
                pageSize = 5,
                totalPages = 2,
                totalElements = 6L,
                isLast = false
            )
            val representation = page.toString()

            assertAll(
                { assertTrue(representation.contains("content")) },
                { assertTrue(representation.contains("pageNumber=1")) },
                { assertTrue(representation.contains("pageSize=5")) },
                { assertTrue(representation.contains("totalPages=2")) },
                { assertTrue(representation.contains("totalElements=6")) },
                { assertTrue(representation.contains("isLast=false")) }
            )
        }

        @Test
        @DisplayName("supports copying with overridden values")
        fun supportsCopyingWithOverriddenValues() {
            val original = PageResponseDto.PageResponseDto(
                content = listOf("item1"),
                pageNumber = 0,
                pageSize = 10,
                totalPages = 1,
                totalElements = 1L,
                isLast = true
            )
            val copied = original.copy(pageNumber = 1, isLast = false)

            assertAll(
                { assertEquals(listOf("item1"), copied.content) },
                { assertEquals(1, copied.pageNumber) },
                { assertEquals(10, copied.pageSize) },
                { assertEquals(1, copied.totalPages) },
                { assertEquals(1L, copied.totalElements) },
                { assertFalse(copied.isLast) }
            )
        }

        @Test
        @DisplayName("supports destructuring declarations")
        fun supportsDestructuringDeclarations() {
            val page = PageResponseDto.PageResponseDto(
                content = listOf("item1", "item2"),
                pageNumber = 1,
                pageSize = 2,
                totalPages = 3,
                totalElements = 4L,
                isLast = false
            )

            val (content, pageNumber, pageSize, totalPages, totalElements, isLast) = page

            assertAll(
                { assertEquals(listOf("item1", "item2"), content) },
                { assertEquals(1, pageNumber) },
                { assertEquals(2, pageSize) },
                { assertEquals(3, totalPages) },
                { assertEquals(4L, totalElements) },
                { assertFalse(isLast) }
            )
        }
    }

    @Nested
    @DisplayName("Pagination logic validation")
    inner class PaginationLogicValidation {

        @Test
        @DisplayName("validates middle page invariants")
        fun validatesMiddlePageInvariants() {
            val page = PageResponseDto.PageResponseDto(
                content = listOf("item1", "item2", "item3"),
                pageNumber = 2,
                pageSize = 3,
                totalPages = 5,
                totalElements = 13L,
                isLast = false
            )

            assertAll(
                { assertTrue(page.pageNumber in 0 until page.totalPages) },
                { assertTrue(page.pageSize > 0) },
                { assertTrue(page.totalElements >= 0) },
                { assertTrue(page.content.size <= page.pageSize) },
                { assertFalse(page.isLast) }
            )
        }

        @Test
        @DisplayName("validates last page with partial content")
        fun validatesLastPageWithPartialContent() {
            val page = PageResponseDto.PageResponseDto(
                content = listOf("item1"),
                pageNumber = 4,
                pageSize = 3,
                totalPages = 5,
                totalElements = 13L,
                isLast = true
            )

            assertAll(
                { assertEquals(page.totalPages - 1, page.pageNumber) },
                { assertTrue(page.content.size < page.pageSize) },
                { assertTrue(page.isLast) }
            )
        }

        @Test
        @DisplayName("handles completely empty result set")
        fun handlesCompletelyEmptyResultSet() {
            val page = PageResponseDto.PageResponseDto(
                content = emptyList<String>(),
                pageNumber = 0,
                pageSize = 10,
                totalPages = 0,
                totalElements = 0L,
                isLast = true
            )

            assertAll(
                { assertTrue(page.content.isEmpty()) },
                { assertEquals(0, page.totalPages) },
                { assertEquals(0L, page.totalElements) },
                { assertTrue(page.isLast) }
            )
        }
    }

    @Nested
    @DisplayName("Type safety")
    inner class TypeSafety {

        @Test
        @DisplayName("maintains type declarations across different content types")
        fun maintainsTypeDeclarationsAcrossDifferentContentTypes() {
            val stringPage = PageResponseDto.PageResponseDto(
                content = listOf("string1", "string2"),
                pageNumber = 0,
                pageSize = 10,
                totalPages = 1,
                totalElements = 2L,
                isLast = true
            )
            val longPage = PageResponseDto.PageResponseDto(
                content = listOf(1L, 2L, 3L),
                pageNumber = 0,
                pageSize = 10,
                totalPages = 1,
                totalElements = 3L,
                isLast = true
            )
            val nullablePage = PageResponseDto.PageResponseDto(
                content = listOf("value", null, "another"),
                pageNumber = 0,
                pageSize = 10,
                totalPages = 1,
                totalElements = 3L,
                isLast = true
            )

            assertAll(
                { assertTrue(stringPage.content.all { it is String }) },
                { assertTrue(longPage.content.all { it is Long }) },
                { assertEquals(3, nullablePage.content.size) },
                { assertNull(nullablePage.content[1]) }
            )
        }

        @Test
        @DisplayName("supports complex nested content types")
        fun supportsComplexNestedContentTypes() {
            val complexItems = listOf(
                NestedCustomItem(1L, listOf("a", "b")),
                NestedCustomItem(2L, listOf("c", "d", "e"))
            )
            val page = PageResponseDto.PageResponseDto(
                content = complexItems,
                pageNumber = 0,
                pageSize = 10,
                totalPages = 1,
                totalElements = 2L,
                isLast = true
            )

            assertAll(
                { assertEquals(2, page.content.size) },
                { assertEquals(NestedCustomItem(1L, listOf("a", "b")), page.content.first()) },
                { assertEquals(NestedCustomItem(2L, listOf("c", "d", "e")), page.content.last()) }
            )
        }
    }
}