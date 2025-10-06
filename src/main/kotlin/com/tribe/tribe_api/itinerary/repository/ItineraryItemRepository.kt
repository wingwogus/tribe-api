package com.tribe.tribe_api.itinerary.repository

import com.tribe.tribe_api.itinerary.entity.ItineraryItem
import org.springframework.data.jpa.repository.JpaRepository

interface ItineraryItemRepository: JpaRepository<ItineraryItem, Long> {
}