package com.tribe.tribe_api.itinerary.entity

import com.tribe.tribe_api.trip.entity.Trip
import jakarta.persistence.*

@Entity
class Category(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    val trip: Trip,

    var day: Int,

    var name: String,

    @Column(name = "category_order")
    var order: Int
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    val id: Long? = null

    @OneToMany(mappedBy = "category", cascade = [CascadeType.ALL], orphanRemoval = true)
    var itineraryItems: MutableList<ItineraryItem> = mutableListOf()
}