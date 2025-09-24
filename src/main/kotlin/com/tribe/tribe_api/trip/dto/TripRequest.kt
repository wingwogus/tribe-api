package com.tribe.tribe_api.trip.dto

import com.tribe.tribe_api.trip.entity.Country
import com.tribe.tribe_api.trip.entity.Trip
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.LocalDate

sealed class TripRequest {

    data class Create(
        @field:NotBlank(message = "여행 제목은 필수입니다.")
        val title: String,

        @field:NotNull(message = "여행 시작일은 필수입니다.")
        val startDate: LocalDate,

        @field:NotNull(message = "여행 종료일은 필수입니다.")
        val endDate: LocalDate,

        @field:NotNull(message = "여행 국가는 필수입니다.")
        val country: Country
    ) {
        fun toEntity(): Trip {
            return Trip(
                title = this.title,
                startDate = this.startDate,
                endDate = this.endDate,
                country = this.country
            )
        }

        @AssertTrue(message = "여행 시작일은 종료일보다 이전이거나 같아야 합니다.")
        private fun isDatesValid(): Boolean {
            return !startDate.isAfter(endDate)
        }
    }

    data class Update(
        @field:NotBlank(message = "여행 제목은 필수입니다.")
        val title: String,

        @field:NotNull(message = "여행 시작일은 필수입니다.")
        val startDate: LocalDate,

        @field:NotNull(message = "여행 종료일은 필수입니다.")
        val endDate: LocalDate,

        @field:NotNull(message = "여행 국가는 필수입니다.")
        val country: Country
    ) {
        @AssertTrue(message = "여행 시작일은 종료일보다 이전이거나 같아야 합니다.")
        private fun isDatesValid(): Boolean {
            return !startDate.isAfter(endDate)
        }
    }

    data class Join(
        @field:NotBlank(message = "초대 토큰은 필수입니다.")
        val token: String
    )
}