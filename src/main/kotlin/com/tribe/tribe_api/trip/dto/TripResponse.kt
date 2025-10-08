package com.tribe.tribe_api.trip.dto

import com.tribe.tribe_api.trip.entity.Trip
import com.tribe.tribe_api.trip.entity.TripMember
import com.tribe.tribe_api.trip.entity.TripRole
import java.time.LocalDate

sealed class TripResponse {

    data class Invitation(
        val inviteLink: String
    )

    data class SimpleTrip(
        val tripId: Long,
        val title: String,
        val startDate: LocalDate,
        val endDate: LocalDate,
        val country: String,
        val memberCount: Int
    ) {
        companion object {
            fun from(trip: Trip): SimpleTrip {
                return SimpleTrip(
                    tripId = trip.id!!,
                    title = trip.title,
                    startDate = trip.startDate,
                    endDate = trip.endDate,
                    country = trip.country.koreanName,
                    memberCount = trip.members.size
                )
            }
        }
    }

    data class TripDetail(
        val tripId: Long,
        val title: String,
        val startDate: LocalDate,
        val endDate: LocalDate,
        val country: String,
        val members: List<MemberInfo>
    ) {
        companion object {
            fun from(trip: Trip): TripDetail {
                return TripDetail(
                    tripId = trip.id!!,
                    title = trip.title,
                    startDate = trip.startDate,
                    endDate = trip.endDate,
                    country = trip.country.code,
                    members = trip.members.map { MemberInfo.from(it) }
                )
            }
        }
    }

    data class MemberInfo(
        val memberId: Long?,
        val nickname: String,
        val role: TripRole
    ) {
        companion object {
            fun from(tripMember: TripMember): MemberInfo {
                val displayName = tripMember.member?.nickname ?: tripMember.guestNickname ?: "게스트"
                val memberId = tripMember.member?.id

                return MemberInfo(
                    memberId = memberId,
                    nickname = displayName,
                    role = tripMember.role
                )
            }
        }
    }
}