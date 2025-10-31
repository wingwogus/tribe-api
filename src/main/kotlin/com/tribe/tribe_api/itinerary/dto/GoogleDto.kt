package com.tribe.tribe_api.itinerary.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

object GoogleDto {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class PlacesResponse(
        val places: List<PlaceResult>?,
    ) {
        data class PlaceResult(
            val id: String,
            val formattedAddress: String?,
            val location: Location?,
            val displayName: DisplayName?,
        )

        data class Location(
            val latitude: Double,
            val longitude: Double
        )

        data class DisplayName(
            val text: String,
            val languageCode: String
        )
    }

    /**
     * Google Directions API v1 JSON 응답을 파싱 Raw DTO
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class DirectionsRawResponse(
        // "OK", "ZERO_RESULTS" 등
        val status: String,

        // 경로 목록 (보통 1개)
        val routes: List<Route>
    ) {
        /**
         * 경로 요약 및 상세
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        data class Route(
            @JsonProperty("overview_polyline")
            val overviewPolyline: Polyline,

            // 여정 상세 (보통 1개)
            val legs: List<Leg>
        )

        /**
         * 인코딩된 폴리라인 문자열
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        data class Polyline(
            val points: String
        )

        /**
         * 전체 여정 (출발부터 도착까지)
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        data class Leg(
            val duration: TextValue, // 총 시간
            val distance: TextValue, // 총 거리
            val steps: List<Step>    // 단계별 경로
        )

        /**
         * "text"와 "value"를 갖는 공통 객체
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        data class TextValue(
            val text: String // 예: "24 mins"
        )

        /**
         * 경로의 각 단계
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        data class Step(
            @JsonProperty("travel_mode")
            val travelMode: String, // "WALKING", "TRANSIT"

            @JsonProperty("html_instructions")
            val htmlInstructions: String, // 예: "Walk to Eung-Am"

            val duration: TextValue,
            val distance: TextValue,

            @JsonProperty("transit_details")
            val transitDetails: TransitDetails? // WALKING일 경우 null
        )

        /**
         * 대중교통 상세
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        data class TransitDetails(
            @JsonProperty("num_stops")
            val numStops: Int,

            @JsonProperty("departure_stop")
            val departureStop: StopInfo,

            @JsonProperty("arrival_stop")
            val arrivalStop: StopInfo,

            val line: Line
        )

        /**
         * 정류장 정보
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        data class StopInfo(
            val name: String // 예: "Eung-Am"
        )

        /**
         * 노선 정보 (지하철, 버스)
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        data class Line(
            @JsonProperty("short_name")
            val shortName: String, // "6호선", "7716"

            val vehicle: Vehicle
        )

        /**
         * 차량 정보
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        data class Vehicle(
            val type: String, // 이동수단 방법 예: "SUBWAY", "BUS"
            val icon: String? // 예: "//maps.gstatic.com/..."
        )
    }
}