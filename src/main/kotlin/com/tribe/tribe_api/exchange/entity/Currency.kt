package com.tribe.tribe_api.exchange.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate

@Entity
@Table(name = "currency")
@IdClass(CurrencyId::class) // [추가됨] 복합 키 클래스 지정
class Currency(
    // [수정됨] val -> var 변경
    @Id
    @Column(name = "cur_unit", length = 10)
    var curUnit: String, // <- val에서 var로 수정

    // [수정됨] val -> var 변경
    @Id
    @Column(nullable = false)
    var date: LocalDate, // <- val에서 var로 수정

    @Column(nullable = false)
    var curName: String, // <- val에서 var로 수정

    // 금융 정밀도를 위해 BigDecimal 사용 (scale=4: 소수점 4자리까지 저장)
    @Column(nullable = false, precision = 10, scale = 4)
    var exchangeRate: BigDecimal, // <- val에서 var로 수정
) {
    // JpaRepository 사용을 위해 기본 생성자 및 equals/hashCode 로직 변경 (IdClass 사용으로 불필요)
    constructor() : this("", LocalDate.MIN, "", BigDecimal.ZERO)
}