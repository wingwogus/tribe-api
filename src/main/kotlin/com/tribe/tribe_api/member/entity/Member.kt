package com.tribe.tribe_api.member.entity

import com.tribe.tribe_api.community.entity.CommunityOffer
import com.tribe.tribe_api.community.entity.Notification
import com.tribe.tribe_api.trip.entity.TripMember
import jakarta.persistence.*

@Entity 
class Member(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_id")
    val id: Long? = null,

    @Column(nullable = false, unique = true)
    val email: String,

    @Column(nullable = false)
    var password: String,

    @Column(nullable = false, unique = true)
    var nickname: String,

    @Column(nullable = true)
    var avatar: String? = null,

    @Enumerated(EnumType.STRING)
    val role: Role,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val provider: Provider,

    @Column(nullable = true)
    val providerId: String? = null,

    @Column(nullable = false)
    var isFirstLogin: Boolean,
) {
    @OneToMany(mappedBy = "member", cascade = [CascadeType.ALL], orphanRemoval = true)
    var tripMembers: MutableList<TripMember> = mutableListOf()

    @OneToMany(mappedBy = "provider", cascade = [CascadeType.ALL], orphanRemoval = true)
    var communityOffers: MutableList<CommunityOffer> = mutableListOf()

    @OneToMany(mappedBy = "recipient", cascade = [CascadeType.ALL], orphanRemoval = true)
    var notifications: MutableList<Notification> = mutableListOf()
}