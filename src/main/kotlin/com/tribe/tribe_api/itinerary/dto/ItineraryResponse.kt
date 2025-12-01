package com.tribe.tribe_api.itinerary.dto

import com.tribe.tribe_api.itinerary.entity.ItineraryItem
import com.tribe.tribe_api.itinerary.entity.Place
import java.time.LocalDateTime

sealed class ItineraryResponse {
    data class ItineraryDetail(
        val itineraryId: Long,
        val categoryId: Long,
        val name: String,
        val time: LocalDateTime?,
        val order: Int,
        val memo: String?,
        val location: LocationInfo?
    ) {
        companion object {
            fun from(item: ItineraryItem): ItineraryDetail {
                return ItineraryDetail(
                    itineraryId = item.id!!,
                    categoryId = item.category.id!!,
                    name = item.place?.name ?: item.title!!,
                    time = item.time,
                    order = item.order,
                    memo = item.memo,
                    location = item.place?.let { LocationInfo.from(it) }
                )
            }
        }
                                                                                                                                                
        data class LocationInfo(
            val lat: Double,
            val lng: Double,
            val address: String?,
            val externalPlaceId: String // Place 엔티티의 externalPlaceId 매핑
        ) {
            companion object {
                fun from(place: Place): LocationInfo {
                    return LocationInfo(
                        lat = place.latitude.toDouble(),
                        lng = place.longitude.toDouble(),
                        address = place.address,
                        externalPlaceId = place.externalPlaceId // externalPlaceId 매핑 (마커 클릭시 photo, content보여주기 위함)
                    )
                }
            }
        }
    }
                                                                                                                                                
    /**
     * 전체 경로 응답을 담는 메인 DTO
     */
    data class RouteDetails(
        val travelMode: String,
        val originPlace: PlaceDto.Simple,
        val destinationPlace: PlaceDto.Simple,
        val totalDuration: String,      // 총 시간, 예: "24 mins"
        val totalDistance: String,      // 총 거리, 예: "7.3 km"
//        val overviewPolyline: String, // 지도 위에 경로선 표시할 수 있는 폴라라인, 예: "erndF..."
        val steps: List<RouteStep> // 경로의 상세 단계 목록
    ) {
        /**
         * 경로의 각 단계(Step)를 나타내는 DTO
         */
        data class RouteStep(
            val travelMode: String,         // 이동 방식, 예: "WALKING" 또는 "TRANSIT"
            val instructions: String,       // 이동 방식 설명, 예: "Walk to Eung-Am" (html_instructions)
            val duration: String,           // 스텝 당 걸리는 시간, 예: "2 mins"
            val distance: String,           // 스텝 당 거리, 예: "0.1 km"
                                            
            // TRANSIT 모드일 때만 값이 존재, WALKING일 때는 null
            val transitDetails: TransitDetails?
        )
                                                                                                                                                
        /**
         * 대중교통(TRANSIT) 단계일시 상세 정보를 담는 DTO
         */
        data class TransitDetails(
            val lineName: String,
            val vehicleType: String,
            val vehicleIconUrl: String?,    // 이동수단 아이콘(버스, 지하철 등)
            val numStops: Int,              // 정류장 개수
            val departureStop: String,      // 출발 정류장 예: "Eung-Am"
            val arrivalStop: String         // 도착 정류장 예: "Eung-Am"
        )
    }
}
