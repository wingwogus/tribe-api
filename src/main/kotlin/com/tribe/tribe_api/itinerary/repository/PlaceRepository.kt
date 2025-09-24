package com.tribe.tribe_api.itinerary.repository

import com.tribe.tribe_api.itinerary.entity.Place
import org.springframework.data.jpa.repository.JpaRepository

interface PlaceRepository: JpaRepository<Place, Long> {
}