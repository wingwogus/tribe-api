package com.tribe.tribe_api.common.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.JavaMailSenderImpl
import java.util.*

@Configuration
class EmailConfig(
    @Value("\${spring.mail.host}") private val host: String,
    @Value("\${spring.mail.port}") private val port: Int,
    @Value("\${spring.mail.username}") private val username: String,
    @Value("\${spring.mail.password}") private val password: String,
    @Value("\${spring.mail.properties.mail.smtp.auth}") private val auth: Boolean,
    @Value("\${spring.mail.properties.mail.smtp.starttls.enable}") private val starttlsEnable: Boolean,
    @Value("\${spring.mail.properties.mail.smtp.starttls.required}") private val starttlsRequired: Boolean,
    @Value("\${spring.mail.properties.mail.smtp.connectiontimeout}") private val connectionTimeout: Int,
    @Value("\${spring.mail.properties.mail.smtp.timeout}") private val timeout: Int,
    @Value("\${spring.mail.properties.mail.smtp.writetimeout}") private val writeTimeout: Int
) {

    @Bean
    fun javaMailSender(): JavaMailSender {
        return JavaMailSenderImpl().apply {
            this.host = this@EmailConfig.host
            this.port = this@EmailConfig.port
            this.username = this@EmailConfig.username
            this.password = this@EmailConfig.password
            this.defaultEncoding = "UTF-8"
            this.javaMailProperties = getMailProperties()
        }
    }

    private fun getMailProperties(): Properties {
        return Properties().apply {
            this["mail.smtp.auth"] = auth
            this["mail.smtp.starttls.enable"] = starttlsEnable
            this["mail.smtp.starttls.required"] = starttlsRequired
            this["mail.smtp.connectiontimeout"] = connectionTimeout
            this["mail.smtp.timeout"] = timeout
            this["mail.smtp.writeTimeout"] = writeTimeout
        }
    }
}