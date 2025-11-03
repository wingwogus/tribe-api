package com.tribe.tribe_api.exchange.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate

@Entity
@Table(name = "currency")
@IdClass(CurrencyId::class) // [추가됨] 복합 키 클래스 지정
class Currency(
    // [수정됨] curUnit에 @Id 추가
    @Id
    @Column(name = "cur_unit", length = 10)
    val curUnit: String,

    // [수정됨] date에 @Id 추가
    @Id
    @Column(nullable = false)
    val date: LocalDate,

    @Column(nullable = false)
    val curName: String,

    // 금융 정밀도를 위해 BigDecimal 사용 (scale=4: 소수점 4자리까지 저장)
    @Column(nullable = false, precision = 10, scale = 4)
    val exchangeRate: BigDecimal,
) {
    // JpaRepository 사용을 위해 기본 생성자 및 equals/hashCode 로직 변경 (IdClass 사용으로 불필요)
    constructor() : this("", LocalDate.MIN, "", BigDecimal.ZERO)
}