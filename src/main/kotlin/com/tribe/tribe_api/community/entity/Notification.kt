package com.tribe.tribe_api.community.entity

import com.tribe.tribe_api.common.util.BaseTimeEntity
import com.tribe.tribe_api.member.entity.Member
import jakarta.persistence.*

@Entity
class Notification(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    val recipient: Member,

    val content: String,

    var isRead: Boolean = false
) : BaseTimeEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    val id: Long? = null
}