package com.tribe.tribe_api.common.annotation

import jakarta.validation.Constraint
import jakarta.validation.Payload
import kotlin.reflect.KClass

@MustBeDocumented // Java의 @Documented
@Constraint(validatedBy = [CustomEmailValidator::class])
@Target(
    AnnotationTarget.FIELD, // 필드에 적용
    AnnotationTarget.VALUE_PARAMETER // 파라미터에 적용
)
@Retention(AnnotationRetention.RUNTIME)
annotation class CustomEmail(
    val message: String = "유효하지 않은 이메일 형식입니다.",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)