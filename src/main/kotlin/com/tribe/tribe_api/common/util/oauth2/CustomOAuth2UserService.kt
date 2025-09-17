package com.tribe.tribe_api.common.util.oauth2

import com.tribe.tribe_api.common.util.security.CustomUserDetails
import com.tribe.tribe_api.member.entity.Member
import com.tribe.tribe_api.member.repository.MemberRepository
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CustomOAuth2UserService(
    private val memberRepository: MemberRepository
) : DefaultOAuth2UserService() {

    @Transactional
    override fun loadUser(userRequest: OAuth2UserRequest): OAuth2User {
        val oAuth2User = super.loadUser(userRequest)

        val registrationId = userRequest.clientRegistration.registrationId
        val userNameAttributeName = userRequest.clientRegistration.providerDetails.userInfoEndpoint.userNameAttributeName
        val attributes = OAuthAttributes.of(registrationId, userNameAttributeName, oAuth2User.attributes)
        val providerId = oAuth2User.attributes[userNameAttributeName].toString()
        val member = saveOrUpdate(attributes, providerId)

        return CustomUserDetails(member, oAuth2User.attributes)
    }

    private fun saveOrUpdate(attributes: OAuthAttributes, providerId: String): Member {
        // orElseGet -> 엘비스 연산자(?:)로 대체
        return memberRepository.findByProviderAndProviderId(attributes.provider, providerId)
            ?: memberRepository.save(attributes.toEntity(providerId))
    }
}