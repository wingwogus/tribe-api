package com.tribe.tribe_api.trip.service

import com.tribe.tribe_api.common.exception.BusinessException
import com.tribe.tribe_api.common.exception.ErrorCode
import com.tribe.tribe_api.common.util.service.GeminiApiClient
import com.tribe.tribe_api.trip.dto.TripReviewRequest
import com.tribe.tribe_api.trip.dto.TripReviewResponse
import com.tribe.tribe_api.trip.entity.Trip
import com.tribe.tribe_api.trip.entity.TripReview
import com.tribe.tribe_api.trip.repository.TripRepository
import com.tribe.tribe_api.trip.repository.TripReviewRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class TripReviewService(
    private val tripRepository: TripRepository,
    private val tripReviewRepository: TripReviewRepository,
    private val geminiApiClient: GeminiApiClient
) {

    fun createReview(
        tripId: Long,
        request: TripReviewRequest.CreateReview
    ): TripReviewResponse.ReviewDetail {
        val trip = tripRepository.findTripWithFullItineraryById(tripId)
            ?: throw BusinessException(ErrorCode.TRIP_NOT_FOUND)

        val prompt = createPromptFromTrip(trip, request.concept)
        val aiFeedback = geminiApiClient.getFeedback(prompt)
            ?: throw BusinessException(ErrorCode.AI_FEEDBACK_ERROR)

        val review = TripReview(trip, aiFeedback)
        tripReviewRepository.save(review)

        return TripReviewResponse.ReviewDetail.from(review)
    }

    private fun createPromptFromTrip(trip: Trip, concept: String?): String {
        return """
        당신은 최고의 여행 전문가이자 꼼꼼한 여행 계획 검토자입니다. 주어진 [여행 정보]와 [상세 일정]을 바탕으로, 아래 [단계별 지침]에 따라 사용자에게 매우 구체적이고 실용적인 피드백을 제공해야 합니다.

        **[단계별 지침]**

        **1단계: 계획의 유효성 검증 (가장 먼저 수행할 작업)**
        본격적인 리뷰에 앞서, 계획에 치명적인 오류가 있는지 먼저 확인합니다.
        * **A. 국가 일치 여부 확인:** [상세 일정] 중 주소가 `${trip.country.koreanName}`에 속하지 않는 장소가 [상세 일정]에 단 하나라도 포함되어 있다면, **즉시 리뷰를 중단**하세요. 그리고 다음과 같이 오류를 명확히 지적하는 메시지만을 출력해야 합니다.
            > "계획 검토를 중단합니다. '${trip.country.koreanName}' 여행 계획과 다른 장소인 '[잘못된 장소 이름]'이 포함되어 있습니다. 전체 일정을 다시 확인하고 수정해주세요."
        
        * **B. 비논리적인 계획 확인:** 사용자가 상식적으로 이해하기 어려운 "이상한" 계획을 세웠는지 확인합니다.
            * **비현실적인 이동:** 하루 안에 도시의 양 끝을 오가거나, 이동 시간만 4~5시간이 걸리는 장소들을 식사만 하고 돌아오는 것처럼 비상식적인 동선이 있는지 확인합니다.
            * **과도한 일정:** 하루에 소화하기 불가능할 정도로 너무 많은 활동(예: 박물관 3곳, 쇼핑, 야경 감상)이 포함되어 있는지 확인합니다.
        
            만약 이런 비논리적인 부분이 발견되면, 본격적인 리뷰 전에 해당 문제점을 먼저 지적하고 수정을 제안하는 경고 문구를 포함시켜 주세요.
        
        **2단계: 상세 검토 및 제안 (1단계 통과 시에만 수행)**
        계획에 큰 오류가 없다고 판단되면, 다음 항목에 따라 상세한 피드백을 작성합니다.
        * **A. 전체적인 동선의 효율성:** 각 날짜별 동선을 지리적으로 분석하고, 이동 시간을 줄일 수 있는 더 효율적인 방문 순서나 방법을 제안합니다.
        * **B. 각 날짜별 일정의 현실성:** 각 활동에 필요한 최소 시간(관람, 식사, 대기 시간 등)을 고려하여 일정이 너무 빡빡하거나 여유로운지 평가하고 조언합니다.
        * **C. 숨겨진 명소 및 맛집 추천:**
            * 각 일정의 주요 장소 근처에 있는, 관광객들에게는 잘 알려지지 않았지만 방문 가치가 높은 로컬 명소나 카페를 추천합니다.
            * 일정의 동선과 분위기에 맞는 맛집을 2~3곳 추천합니다.
            * **중요:** 추천하는 모든 식당은 **정확한 이름(현지어 표기 병행)과 함께, 클릭하면 바로 구글 맵으로 연결되는 검색용 주소를 반드시 포함**해야 합니다. (예: `[킨류 라멘 金龍ラーメン](http.googleusercontent.com/maps/google.com/7)`)


        [여행 정보]
        - 여행 제목: ${trip.title}
        - 여행 국가: ${trip.country.koreanName}
        - 여행 기간: ${trip.startDate} ~ ${trip.endDate}

        [상세 일정]
        ${trip.categories.groupBy { it.day }.toSortedMap().map { (day, categories) ->
            """
            - Day $day
            ${categories.joinToString("\n") { category ->
                """
                  - 카테고리: ${category.name}
                ${category.itineraryItems.joinToString("\n") { "    - 이름: ${it.place?.name}, 주소: ${it.place?.address}" }}
                """
            }}
            """.trimIndent()
        }.joinToString("\n")}
        """.trimIndent()
    }
}