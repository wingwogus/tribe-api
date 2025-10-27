package com.tribe.tribe_api.itinerary.entity

import com.tribe.tribe_api.trip.entity.TripReview
import jakarta.persistence.*

@Entity
class RecommendedPlace(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id")
    val place: Place,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_review_id")
    val tripReview: TripReview,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recommended_place_id")
    val id: Long? = null

    companion object {
        fun from(place: Place, tripReview: TripReview): RecommendedPlace {
            return RecommendedPlace(place, tripReview).also {
                tripReview.recommendedPlaces.add(it)
                place.recommendedPlaces.add(it)
            }
        }
    }
}