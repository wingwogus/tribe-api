package com.tribe.tribe_api.itinerary.entity

import jakarta.persistence.*
import org.apache.logging.log4j.util.BiConsumer
import java.math.BigDecimal

@Entity
class Place(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "place_id")
    val id: Long,

    val externalPlaceId: String,

    val name: String,

    val address: String? = null,

    @Column(precision = 10, scale = 7)
    val latitude: BigDecimal,

    @Column(precision = 10, scale = 7)
    val longitude: BigDecimal
) {
    @OneToMany(mappedBy = "place")
    var wishlistItems: MutableList<WishlistItem> = mutableListOf()

    @OneToMany(mappedBy = "place")
    var itineraryItems: MutableList<ItineraryItem> = mutableListOf()
}