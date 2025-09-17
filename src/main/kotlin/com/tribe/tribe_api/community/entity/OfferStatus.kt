package com.tribe.tribe_api.community.entity

enum class OfferStatus {
    PENDING,  // 제안 검토 중
    ACCEPTED,  // 제안 수락됨
    REJECTED,  // 제안 거절됨
    CANCELED // 제안 취소
}