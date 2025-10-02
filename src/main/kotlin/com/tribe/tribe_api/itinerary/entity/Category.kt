package com.tribe.tribe_api.itinerary.entity

import com.tribe.tribe_api.common.util.BaseTimeEntity
import com.tribe.tribe_api.trip.entity.Trip
import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime

@Entity
@EntityListeners(AuditingEntityListener::class)
class Category(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    val trip: Trip,

    var day: Int,

    var name: String,

    @Column(name = "category_order")
    var order: Int
): BaseTimeEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    val id: Long? = null


    @Column(columnDefinition = "TEXT")
    var memo: String? = null

    @OneToMany(mappedBy = "category", cascade = [CascadeType.ALL], orphanRemoval = true)
    var itineraryItems: MutableList<ItineraryItem> = mutableListOf()
}