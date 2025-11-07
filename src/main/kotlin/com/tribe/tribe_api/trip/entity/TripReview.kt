package com.tribe.tribe_api.trip.entity

import com.tribe.tribe_api.common.util.BaseTimeEntity
import com.tribe.tribe_api.itinerary.entity.RecommendedPlace
import jakarta.persistence.*

@Entity
class TripReview(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    val trip: Trip,

    val concept: String?,

    @Lob
    var content: String,
) : BaseTimeEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "trip_review_id")
    val id: Long? = null

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "tripReview", cascade = [CascadeType.ALL], orphanRemoval = true)
    val recommendedPlaces: MutableList<RecommendedPlace> = mutableListOf()
}