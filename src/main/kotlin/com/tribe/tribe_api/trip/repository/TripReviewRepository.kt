package com.tribe.tribe_api.trip.repository

import com.tribe.tribe_api.trip.entity.TripReview
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface TripReviewRepository:  JpaRepository<TripReview, Long>{

    fun findTripReviewsByTripId(tripId: Long, pageable: Pageable): Page<TripReview>

    @Query("""
            SELECT t FROM TripReview t 
            LEFT JOIN FETCH t.recommendedPlaces rp
            LEFT JOIN FETCH rp.place p
            WHERE t.id = :tripReviewId
        """)
    fun findTripReviewWithRecommendedPlacesById(tripReviewId:Long): TripReview?
}