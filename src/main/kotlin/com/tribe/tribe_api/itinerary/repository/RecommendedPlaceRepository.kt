package com.tribe.tribe_api.itinerary.repository

import com.tribe.tribe_api.itinerary.entity.RecommendedPlace
import org.springframework.data.jpa.repository.JpaRepository

interface RecommendedPlaceRepository: JpaRepository<RecommendedPlace, Long> {
}