package com.tribe.tribe_api.trip.entity

import lombok.AllArgsConstructor
import lombok.Getter

@AllArgsConstructor
@Getter
enum class Country(
    val code: String,
    val koreanName: String,
) {
    SOUTH_KOREA("KR", "대한민국"),
    JAPAN("JP", "일본"),
    CHINA("CN", "중국"),
    THAILAND("TH", "태국"),
    VIETNAM("VN", "베트남"),
    PHILIPPINES("PH", "필리핀"),
    SINGAPORE("SG", "싱가포르"),
    MALAYSIA("MY", "말레이시아"),
    INDONESIA("ID", "인도네시아"),
    INDIA("IN", "인도"),
    UAE("AE", "아랍에미리트"),
    TURKEY("TR", "터키"),
    EGYPT("EG", "이집트"),
    ITALY("IT", "이탈리아"),
    FRANCE("FR", "프랑스"),
    SPAIN("ES", "스페인"),
    UK("GB", "영국"),
    GERMANY("DE", "독일"),
    SWITZERLAND("CH", "스위스"),
    NETHERLANDS("NL", "네덜란드"),
    GREECE("GR", "그리스"),
    USA("US", "미국"),
    CANADA("CA", "캐나다"),
    AUSTRALIA("AU", "호주"),
    NEW_ZEALAND("NZ", "뉴질랜드"),
    BRAZIL("BR", "브라질"),
    ARGENTINA("AR", "아르헨티나"),
    MEXICO("MX", "멕시코"),
    SOUTH_AFRICA("ZA", "남아프리카 공화국"),
    MOROCCO("MA", "모로코");
}