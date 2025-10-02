package com.tribe.tribe_api.trip.dto

import com.tribe.tribe_api.trip.entity.TripMember

object TripMemberDto {
    data class AddGuestRequest(
        val name: String
    )

    data class AddGuestResponse(
        val participantId: Long,
        val name: String,
        val isGuest: Boolean
    )

    data class Info(
        val id: Long,
        val name: String,
        val isGuest: Boolean
    ) {
        companion object {
            fun from(tripMember: TripMember): Info {
                return Info(
                    id = tripMember.id!!,
                    name = tripMember.name,
                    isGuest = tripMember.isGuest
                )
            }
        }
    }
}