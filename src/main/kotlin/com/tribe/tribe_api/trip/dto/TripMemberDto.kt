package com.tribe.tribe_api.trip.dto

import com.tribe.tribe_api.trip.entity.TripMember
import com.tribe.tribe_api.trip.entity.TripRole
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

object TripMemberDto {
    data class AddGuestRequest(
        @field:NotNull(message = "tripId는 필수입니다.")
        val tripId: Long,

        @field:NotBlank(message = "게스트 이름은 비워둘 수 없습니다.")
        val name: String
    )

    data class DeleteGuestRequest(
        @field:NotNull(message = "tripId는 필수입니다.")
        val tripId: Long,

        @field:NotNull(message = "삭제할 게스트의 tripMemberId는 필수입니다.")
        val guestTripMemberId: Long
    )

    data class KickMemberRequest(
        @field:NotNull(message = "tripId는 필수입니다.")
        val tripId: Long,

        @field:NotNull(message = "강퇴할 멤버의 tripMemberId는 필수입니다.")
        val targetTripMemberId: Long
    )

    data class LeaveTripRequest(
        @field:NotNull(message = "tripId는 필수입니다.")
        val tripId: Long
    )

    data class AssignRoleRequest(
        @field:NotNull(message = "tripId는 필수입니다.")
        val tripId: Long,

        @field:NotNull(message = "memberId는 필수입니다.")
        val tripMemberId: Long,

        @field:NotNull(message = "role은 필수입니다.")
        val requestRole: TripRole
    )

    data class Simple(
        val id: Long,
        val name: String,
        val isGuest: Boolean
    ) {
        companion object {
            fun from(tripMember: TripMember): Simple {
                return Simple(
                    id = tripMember.id!!,
                    name = tripMember.name,
                    isGuest = tripMember.isGuest
                )
            }
        }
    }

    data class Details(
        val memberId: Long?,
        val nickname: String,
        val avatar: String?,
        val role: TripRole
    ) {
        companion object {
            fun from(tripMember: TripMember): Details {
                val displayName = tripMember.member?.nickname ?: tripMember.guestNickname ?: "게스트"
                val memberId = tripMember.id
                val avatar = tripMember.member?.avatar

                return Details(
                    memberId,
                    displayName,
                    avatar,
                    tripMember.role
                )
            }
        }
    }
}