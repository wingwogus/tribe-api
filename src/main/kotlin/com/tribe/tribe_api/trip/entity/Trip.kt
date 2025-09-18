package com.tribe.tribe_api.trip.entity

import com.tribe.tribe_api.common.util.BaseTimeEntity
import com.tribe.tribe_api.expense.entity.Expense
import com.tribe.tribe_api.itinerary.entity.Category
import com.tribe.tribe_api.itinerary.entity.WishlistItem
import com.tribe.tribe_api.member.entity.Member
import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize
import java.time.LocalDate

@Entity
class Trip(
    var title: String,
    var startDate: LocalDate,
    var endDate: LocalDate,

    @Enumerated(EnumType.STRING)
    var country: Country,
) : BaseTimeEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "trip_id")
    val id: Long? = null

    @BatchSize(size = 10)
    @OneToMany(mappedBy = "trip", cascade = [CascadeType.ALL], orphanRemoval = true)
    var members: MutableList<TripMember> = mutableListOf()

    @OneToMany(mappedBy = "trip", cascade = [CascadeType.ALL], orphanRemoval = true)
    var categories: MutableList<Category> = mutableListOf()

    @OneToMany(mappedBy = "trip", cascade = [CascadeType.ALL], orphanRemoval = true)
    var expenses: MutableList<Expense> = mutableListOf()

    @OneToMany(mappedBy = "trip", cascade = [CascadeType.ALL], orphanRemoval = true)
    var reviews: MutableList<TripReview> = mutableListOf()

    @OneToMany(mappedBy = "trip", cascade = [CascadeType.ALL], orphanRemoval = true)
    var wishlistItems: MutableList<WishlistItem> = mutableListOf()

    //== 비즈니스 로직 ==//
    fun update(title: String, startDate: LocalDate, endDate: LocalDate, country: Country) {
        this.title = title
        this.startDate = startDate
        this.endDate = endDate
        this.country = country
    }

    //== 연관관계 편의 메소드 ==//
    fun addMember(member: Member, role: TripRole) {
        TripMember(
            member = member,
            trip = this,
            role = role
        ).apply {
            this@Trip.members.add(this)
            member.tripMembers.add(this)
        }
    }
}