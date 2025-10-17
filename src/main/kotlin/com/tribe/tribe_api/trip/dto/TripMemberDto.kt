package com.tribe.tribe_api.trip.dto

import com.tribe.tribe_api.trip.entity.TripMember
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

object TripMemberDto {
    data class AddGuestRequest(
        @field:NotNull(message = "tripId는 필수입니다.")
        val tripId: Long,

        @field:NotBlank(message = "게스트 이름은 비워둘 수 없습니다.")
        val name: String
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