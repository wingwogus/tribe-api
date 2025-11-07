package com.tribe.tribe_api.itinerary.entity

import jakarta.persistence.*
import java.math.BigDecimal

@Entity
class Place(
    val externalPlaceId: String,

    val name: String,

    val address: String? = null,

    @Column(precision = 10, scale = 7)
    val latitude: BigDecimal,

    @Column(precision = 10, scale = 7)
    val longitude: BigDecimal
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "place_id")
    val id: Long? = null

    @OneToMany(mappedBy = "place")
    var wishlistItems: MutableList<WishlistItem> = mutableListOf()

    @OneToMany(mappedBy = "place")
    var itineraryItems: MutableList<ItineraryItem> = mutableListOf()

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "place", cascade = [CascadeType.ALL], orphanRemoval = true)
    val recommendedPlaces: MutableList<RecommendedPlace> = mutableListOf()
}