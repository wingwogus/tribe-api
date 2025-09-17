package com.tribe.tribe_api.common.util.jwt

data class JwtToken(
    val grantType: String,
    val accessToken: String,
    val refreshToken: String
)