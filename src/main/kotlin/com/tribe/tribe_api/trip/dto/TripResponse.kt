package com.tribe.tribe_api.trip.dto

import com.tribe.tribe_api.trip.entity.Trip
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
                    memberCount = trip.members
                        .filterNot({ it.isGuest }).size
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
        val members: List<TripMemberDto.Details>
    ) {
        companion object {
            fun from(trip: Trip): TripDetail {
                return TripDetail(
                    tripId = trip.id!!,
                    title = trip.title,
                    startDate = trip.startDate,
                    endDate = trip.endDate,
                    country = trip.country.code,
                    members = trip.members.map { TripMemberDto.Details.from(it) }
                )
            }
        }
    }
}