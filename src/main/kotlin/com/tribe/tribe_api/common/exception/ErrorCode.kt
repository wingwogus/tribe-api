package com.tribe.tribe_api.common.exception

import org.springframework.http.HttpStatus


enum class ErrorCode(val status: HttpStatus, val message: String) {
    // 400 BAD_REQUEST
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "비밀번호 형식이 올바르지 않습니다."),
    EMAIL_NOT_VERIFIED(HttpStatus.BAD_REQUEST, "이메일 인증이 완료되지 않았습니다."),
    AUTH_CODE_MISMATCH(HttpStatus.BAD_REQUEST, "인증코드가 일치하지 않습니다."),
    ALREADY_LOGGED_OUT(HttpStatus.BAD_REQUEST, "이미 로그아웃된 상태입니다."),
    INVALID_INVITE_TOKEN(HttpStatus.BAD_REQUEST, "유효하지 않은 초대 코드입니다."),
    ALREADY_JOINED_TRIP(HttpStatus.BAD_REQUEST, "이미 여행에 참여한 유저입니다."),
    EXPENSE_ITEM_NOT_IN_EXPENSE(HttpStatus.BAD_REQUEST, "지출 내역에 해당 항목이 존재하지 않습니다."),
    EXPENSE_TOTAL_AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "지출 총액과 품목 금액의 합이 일치하지 않습니다."),
    IMAGE_UPLOAD_FAILED(HttpStatus.BAD_GATEWAY, "이미지 업로드에 실패했습니다."),
    NO_BELONG_TRIP(HttpStatus.BAD_REQUEST, "해당 카테고리는 현재 여행에 속해있지 않습니다."),

    // 401 UNAUTHORIZED,
    UNAUTHORIZED_ACCESS(HttpStatus.UNAUTHORIZED, "인증이 필요한 접근입니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다."),
    UNSUPPORTED_TOKEN(HttpStatus.UNAUTHORIZED, "지원하지 않는 형식의 토큰입니다."),
    UNKNOWN_TOKEN_ERROR(HttpStatus.UNAUTHORIZED, "알 수 없는 토큰 오류가 발생했습니다."),
    INVALID_EMAIL(HttpStatus.UNAUTHORIZED, "인증되지 않은 이메일입니다"),
    NO_AUTHORITY_TRIP(HttpStatus.UNAUTHORIZED, "해당 여행의 수정 권한이 없습니다."),

    // 404 NOT_FOUND
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 사용자를 찾을 수 없습니다."),
    TRIP_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 여행을 찾을 수 없습니다."),
    TRIP_REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 여행의 검토 내역을 찾을 수 없습니다."),
    AUTH_CODE_NOT_FOUND(HttpStatus.NOT_FOUND, "인증코드를 찾을 수 없습니다."),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 카테고리를 찾을 수 없습니다."),
    ITINERARY_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 일정을 찾을 수 없습니다."),
    PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 장소를 찾을 수 없습니다."),
    NOT_A_TRIP_MEMBER(HttpStatus.NOT_FOUND, "해당 여행에 멤버가 아닙니다."),
    WISHLIST_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "해당하는 위시리스트를 찾을 수 없습니다."),
    ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 여정을 찾을 수 없습니다."),
    EXPENSE_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 지출 내역을 찾을 수 없습니다."),
    EXPENSE_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 지출 항목을 찾을 수 없습니다."),

    // 409 CONFLICT
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    ALREADY_SIGNED_EMAIL(HttpStatus.CONFLICT, "이미 회원가입한 이메일입니다"),

    // 500 INTERNAL_SERVER_ERROR
    AI_RESPONSE_PARSING_ERROR(HttpStatus.INTERNAL_SERVER_ERROR,  "AI 응답 파싱에 실패했습니다."),
    SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부에 문제가 발생했습니다."),
    CODE_GENERATION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "코드 생성 중 오류가 발생했습니다."),
    AI_FEEDBACK_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "AI 여행 검토 중 오류가 발생했습니다")
}