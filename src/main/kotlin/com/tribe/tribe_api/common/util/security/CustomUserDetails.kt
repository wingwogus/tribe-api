package com.tribe.tribe_api.common.util.security

import com.tribe.tribe_api.member.entity.Member
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.oauth2.core.user.OAuth2User

class CustomUserDetails(
    val member: Member,
    private val attributes: Map<String, Any>? = null
) : UserDetails, OAuth2User {

    override fun getAuthorities(): Collection<GrantedAuthority> {
        return listOf(SimpleGrantedAuthority("ROLE_${member.role.name}"))
    }

    override fun getPassword(): String? = member.password
    override fun getUsername(): String = member.email
    override fun isAccountNonExpired(): Boolean = true
    override fun isAccountNonLocked(): Boolean = true
    override fun isCredentialsNonExpired(): Boolean = true
    override fun isEnabled(): Boolean = true
    override fun getAttributes(): Map<String, Any>? = this.attributes
    override fun getName(): String {
        return member.providerId ?: member.id.toString()
    }
}