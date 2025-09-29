package com.tribe.tribe_api.itinerary.controller

import com.tribe.tribe_api.common.util.ApiResponse
import com.tribe.tribe_api.itinerary.dto.CategoryDto
import com.tribe.tribe_api.itinerary.service.CategoryService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/trips/{tripId}/categories")
class CategoryController(
    private val categoryService: CategoryService
) {
    /**
     * 카테고리 생성
     */
    @PostMapping
    fun createCategory(
        @PathVariable tripId: Long,
        @Valid @RequestBody request: CategoryDto.CreateRequest
    ): ResponseEntity<ApiResponse<CategoryDto.CategoryResponse>> {
        val response = categoryService.createCategory(tripId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response))
    }

    /**
     * 카테고리 목록 조회
     */
    @GetMapping
    fun getAllCategories(
        @PathVariable tripId: Long,
        @RequestParam(required = false) day: Int?
    ): ResponseEntity<ApiResponse<List<CategoryDto.CategoryResponse>>> {
        val response = categoryService.getAllCategories(tripId, day)
        return ResponseEntity.ok(ApiResponse.success(response))
    }

    /**
     * 카테고리 단건 조회
     */
    @GetMapping("/{categoryId}")
    fun getCategory(
        @PathVariable tripId: Long,
        @PathVariable categoryId: Long
    ): ResponseEntity<ApiResponse<CategoryDto.CategoryResponse>> {
        val response = categoryService.getCategory(categoryId)
        return ResponseEntity.ok(ApiResponse.success(response))
    }

    /**
     * 카테고리 수정
     */
    @PatchMapping("/{categoryId}")
    fun updateCategory(
        @PathVariable tripId: Long,
        @PathVariable categoryId: Long,
        @RequestBody request: CategoryDto.UpdateRequest
    ): ResponseEntity<ApiResponse<CategoryDto.CategoryResponse>> {
        val response = categoryService.updateCategory(categoryId, request)
        return ResponseEntity.ok(ApiResponse.success(response))
    }

    /**
     * 카테고리 삭제
     */
    @DeleteMapping("/{categoryId}")
    fun deleteCategory(
        @PathVariable tripId: Long,
        @PathVariable categoryId: Long
    ): ResponseEntity<ApiResponse<Unit>> {
        categoryService.deleteCategory(categoryId)
        return ResponseEntity.ok(ApiResponse.success("카테고리가 삭제되었습니다.", null))
    }
}