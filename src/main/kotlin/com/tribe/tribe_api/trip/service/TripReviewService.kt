package com.tribe.tribe_api.trip.service

import com.tribe.tribe_api.common.exception.BusinessException
import com.tribe.tribe_api.common.exception.ErrorCode
import com.tribe.tribe_api.common.util.security.SecurityUtil
import com.tribe.tribe_api.common.util.service.GeminiApiClient
import com.tribe.tribe_api.trip.dto.TripReviewRequest
import com.tribe.tribe_api.trip.dto.TripReviewResponse
import com.tribe.tribe_api.trip.entity.Trip
import com.tribe.tribe_api.trip.entity.TripReview
import com.tribe.tribe_api.trip.entity.TripRole
import com.tribe.tribe_api.trip.repository.TripRepository
import com.tribe.tribe_api.trip.repository.TripReviewRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.ResponseEntity
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
        val currentMemberId = SecurityUtil.getCurrentMemberId()

        val trip = tripRepository.findTripWithFullItineraryById(tripId)
            ?: throw BusinessException(ErrorCode.TRIP_NOT_FOUND)

        validateTripOwner(trip, currentMemberId)

        val prompt = createPromptFromTrip(trip, request.concept)
        val aiFeedback = geminiApiClient.getFeedback(prompt)
            ?: throw BusinessException(ErrorCode.AI_FEEDBACK_ERROR)

        val review = TripReview(trip, request.concept, aiFeedback)
        tripReviewRepository.save(review)

        return TripReviewResponse.ReviewDetail.from(review)
    }

    fun getAllReviews(tripId: Long, pageable: Pageable): Page<TripReviewResponse.SimpleReviewInfo> {
        return tripReviewRepository.findTripReviewsByTripId(tripId, pageable)
            .map { TripReviewResponse.SimpleReviewInfo.from(it) }
    }

    fun getReview(tripId: Long, reviewId: Long): TripReviewResponse.ReviewDetail {
        val review = tripReviewRepository.findByIdOrNull(reviewId)
            ?: throw BusinessException(ErrorCode.TRIP_REVIEW_NOT_FOUND)

        if (review.trip.id != tripId) {
            throw BusinessException(ErrorCode.TRIP_NOT_FOUND)
        }

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
        
        * **C. 콘셉트와 일정의 부합 여부 확인:** 여행 콘셉트('${concept}')와 실제 상세 일정이 전혀 맞지 않는 경우를 확인합니다.
            * 예시: 콘셉트는 '여유로운 힐링 여행'인데, 일정이 새벽부터 밤까지 쇼핑과 클럽 활동으로 꽉 차 있는 경우.
    
            만약 B 또는 C 항목에서 심각한 문제점이 발견되면, 본격적인 리뷰 전에 해당 문제점을 먼저 지적하고 수정을 제안하는 경고 문구를 포함시켜 주세요.

        **2단계: 상세 검토 및 제안 (1단계 통과 시에만 수행)**
        계획에 큰 오류가 없다고 판단되면, 모든 항목을 '${concept}'에 맞춰 상세 분석합니다.
        * **A. 전체적인 동선의 효율성:** 여행 콘셉트를 고려하여 동선을 분석합니다. (예: '뚜벅이 여행' 콘셉트라면 대중교통 중심의 효율적인 동선을, '드라이브 여행'이라면 주차 편의성을 고려)
        * **B. 각 날짜별 일정의 현실성:** '${concept}'에 맞춰 일정의 밀도를 평가합니다. (예: '힐링 여행'이라면 여유로운 일정을, '알찬 여행'이라면 빡빡해도 괜찮다는 점을 감안)
        * **C. 숨겨진 명소 및 맛집 추천:**
            * **가장 중요:** 추천하는 모든 장소와 맛집은 '${concept}'과 **직접적으로 관련된 곳이어야 합니다.**
            * **예시:**
                - 콘셉트가 '가성비 맛집 탐방'이면, 저렴하지만 평점 높은 로컬 식당 추천.
                - 콘셉트가 '인생샷 여행'이면, 맛도 좋지만 인테리어가 예쁘거나 뷰가 좋은 곳 추천.
                - 콘셉트가 '가족 여행'이면, 아이와 함께 가기 좋은 식당이나 장소 추천.
            * **링크 생성 규칙:** 추천하는 모든 식당과 장소는 **클릭하면 구글 맵에서 해당 장소의 정보를 보여주는 검색하는 '검색 링크'**를 생성해야 합니다.
                - **링크 형식:** `https://www.google.com/maps/search/?api=1&query=https://mandumanti.wordpress.com/2014/12/01/%EC%9E%A5%EC%86%8C/`
                - **예시:** '킨류 라멘 도톤보리점'을 추천하는 경우, 링크는 `[킨류 라멘 金龍ラーメン](https://www.google.com/maps/search/?api=1&query=%ED%82%A8%EB%A5%98+%EB%9D%BC%EB%A9%98+%EB%8F%84%ED%86%A4%EB%B3%B4%EB%A6%AC%EC%A0%90)` 와 같이 생성되어야 합니다.
        
        ---
        **[출력 규칙 (사용자에게 보여줄 최종 결과물 형식)]**
        
        **1. 국가 불일치 오류가 발견된 경우:**
           모든 리뷰를 중단하고 다음 메시지만을 출력합니다:
           > "계획 검토를 중단합니다. '${trip.country.koreanName}' 여행 계획에 다른 국가의 장소인 '[잘못된 장소 이름]'이 포함되어 있습니다. 전체 일정을 다시 확인하고 수정해주세요."
        
        **2. 논리 또는 콘셉트 불일치 등 수정이 필요한 문제가 발견된 경우:**
           '1단계', '통과' 같은 단어는 **절대 사용하지 말고**, 아래 구조로 즉시 본론을 시작합니다.
           * **제목:** `## [여행 제목] 수정 제안`
           * **경고 문단:** 1단계에서 발견된 모든 문제점(비논리적 계획, 콘셉트 불일치 등)을 통합하여 명확하게 설명하는 경고 문단을 작성합니다.
           * **구체적인 제안:** 사용자가 계획을 수정하는 데 도움이 될 구체적인 제안(추천 레스토랑, 활동 타입 등)을 제시합니다.
           * **다음 단계:** 수정된 계획을 다시 제출하도록 안내하며 리뷰를 마무리합니다.
        
        **3. 계획에 큰 문제가 없는 경우:**
           '1단계', '유효성 검증' 같은 단어는 **절대 사용하지 말고**, 아래 구조로 바로 상세 리뷰를 시작합니다.
           * **제목:** `## [여행 제목] 상세 검토 및 제안`
           * **상세 검토 본문:** 2단계에서 분석한 동선 효율성, 일정 현실성, 그리고 콘셉트에 맞는 명소 및 맛집 추천 등을 날짜별로 상세하게 작성하여 완전한 리뷰를 제공합니다.
                
        ---
        
        [여행 정보]
        - 여행 제목: ${trip.title}
        - 여행 국가: ${trip.country.koreanName}
        - 여행 기간: ${trip.startDate} ~ ${trip.endDate}
        - **여행 콘셉트: `${concept}`**

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

    private fun validateTripOwner(trip: Trip, currentMemberId: Long) {
        val isOwner = trip.members.any {
            it.member?.id == currentMemberId && it.role == TripRole.OWNER
        }

        if (!isOwner) {
            throw BusinessException(ErrorCode.NO_AUTHORITY_TRIP)
        }
    }
}