package com.tribe.tribe_api.itinerary.service

import com.ninjasquad.springmockk.MockkBean
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc

@SpringBootTest
@AutoConfigureMockMvc
class PlaceServiceTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var googleMapService: GoogleMapService

    @Test
    @WithMockUser // 4. Spring Security 인증을 통과하기 위해 가짜 유저를 설정합니다.
    @DisplayName("장소 검색 성공 테스트")
    fun `장소를 키워드로 검색하면 페이지네이션된 장소 목록을 반환한다`(){

    }

}