package com.tribe.tribe_api.exchange.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate

@Entity
@Table(name = "currency")
class Currency(
    // 통화 코드 (USD, JPY)를 ID로 사용합니다.
    @Id
    @Column(name = "cur_unit", length = 10)
    val curUnit: String,

    @Column(nullable = false)
    val curName: String,

    // 금융 정밀도를 위해 BigDecimal 사용 (scale=4: 소수점 4자리까지 저장)
    @Column(nullable = false, precision = 10, scale = 4)
    val exchangeRate: BigDecimal,

    // 날짜를 LocalDate로 저장
    @Column(nullable = false)
    val date: LocalDate,
) {
    // JpaRepository 사용을 위해 기본 생성자 필요
    constructor() : this("", "", BigDecimal.ZERO, LocalDate.MIN)

    // 엔티티가 PK (curUnit)만으로 동일성을 판단하도록 equals/hashCode 오버라이딩
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Currency

        if (curUnit != other.curUnit) return false

        return true
    }

    override fun hashCode(): Int {
        return curUnit.hashCode()
    }
}