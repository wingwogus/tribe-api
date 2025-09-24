package com.tribe.tribe_api.itinerary.repository

import com.tribe.tribe_api.itinerary.entity.Category
import org.springframework.data.jpa.repository.JpaRepository

interface CategoryRepository: JpaRepository<Category, Long> {
}