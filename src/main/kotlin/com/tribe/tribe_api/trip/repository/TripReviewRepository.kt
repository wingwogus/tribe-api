package com.tribe.tribe_api.trip.repository

import com.tribe.tribe_api.trip.entity.TripReview
import org.springframework.data.jpa.repository.JpaRepository

interface TripReviewRepository:  JpaRepository<TripReview, Long>{
}