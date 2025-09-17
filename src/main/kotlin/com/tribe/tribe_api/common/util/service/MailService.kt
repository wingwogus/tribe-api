package com.tribe.tribe_api.common.util.service

import jakarta.mail.internet.MimeMessage
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class MailService(
    private val emailSender: JavaMailSender,
    @Value("\${spring.mail.username}") private val fromAddress: String
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun sendEmail(toEmail: String, title: String, authCode: String) {
        val emailForm = createEmailForm(toEmail, title, authCode)
        try {
            emailSender.send(emailForm)
        } catch (e: RuntimeException) {
            log.debug("MailService.sendEmail exception occur toEmail: {}, title: {}, text: {}", toEmail, title, authCode)
            throw IllegalStateException("이메일 전송 중 오류가 발생했습니다.", e)
        }
    }

    private fun createEmailForm(toEmail: String, title: String, authCode: String): MimeMessage {
        val mimeMessage = emailSender.createMimeMessage()
        try {
            MimeMessageHelper(mimeMessage, true, "UTF-8").apply {
                setTo(toEmail)
                setFrom(fromAddress, "TriBe")
                setSubject(title)
                setText("""
                    <html><body style='background-color: #000000 !important; margin: 0 auto; max-width: 600px; word-break: break-all; padding-top: 50px; color: #ffffff;'>
                    <h1 style='padding-top: 50px; font-size: 30px;'>이메일 주소 인증</h1>
                    <p style='padding-top: 20px; font-size: 18px; opacity: 0.6; line-height: 30px; font-weight: 400;'>안녕하세요? TriBe입니다.<br />
                    TriBe 서비스 사용을 위해 회원가입시 고객님께서 입력하신 이메일 주소의 인증이 필요합니다.<br />
                    하단의 인증 번호로 이메일 인증을 완료하시면, 정상적으로 TriBe 서비스를 이용하실 수 있습니다.<br />
                    항상 최선의 노력을 다하는 TriBe가 되겠습니다.<br />
                    감사합니다.</p>
                    <div class='code-box' style='margin-top: 50px; padding-top: 20px; color: #000000; padding-bottom: 20px; font-size: 25px; text-align: center; background-color: #f4f4f4; border-radius: 10px;'>$authCode</div>
                    </body></html>
                """.trimIndent(), true)
            }
        } catch (e: Exception) {
            throw IllegalStateException("이메일 양식 생성 중 오류가 발생했습니다.", e)
        }
        return mimeMessage
    }
}