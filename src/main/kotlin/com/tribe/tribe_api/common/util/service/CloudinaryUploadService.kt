package com.tribe.tribe_api.common.util.service

import com.cloudinary.Cloudinary
import com.cloudinary.utils.ObjectUtils
import com.tribe.tribe_api.common.exception.BusinessException
import com.tribe.tribe_api.common.exception.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import org.slf4j.LoggerFactory

@Service
class CloudinaryUploadService(
    private val cloudinary: Cloudinary
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun upload(file: MultipartFile, folder: String): String {
        try {
            val options = ObjectUtils.asMap(
                "folder", folder, // receipts로 하드 코딩 된거 말고 folder별로 사진 정리할 수 있게 파라미터 사용
                "resource_type", "image"
            )
            val result = cloudinary.uploader().upload(file.bytes, options)
            return result["secure_url"] as String // 업로드된 이미지의 URL 반환
        } catch (e: Exception) {
            log.error("Cloudinary upload failed: {}", e.message, e) // 오류검출 쉽게 로그메시지 추가
            throw BusinessException(ErrorCode.IMAGE_UPLOAD_FAILED)
        }
    }

    /**
     * Cloudinary에서 이미지를 삭제하는 메서드
     * @param imageUrl DB에 저장된 full URL
     */
    fun delete(imageUrl: String) {
        try {
            val publicId = extractPublicIdFromUrl(imageUrl)
            if (publicId.isNotBlank()) {
                // "resource_type"을 "image"로 지정해야 이미지를 찾아서 삭제됨
                cloudinary.uploader().destroy(publicId, ObjectUtils.asMap("resource_type", "image"))
            }
        } catch (e: Exception) {
            // 에러를 던지는 대신 로그만 남김. 작업 중단 x
            log.error("Failed to delete Cloudinary image. URL: {}, Error: {}", imageUrl, e.message)
        }
    }

    /**
     * Cloudinary의 full URL에서 public_id (예: "community/image_name")를 추출
     */
    private fun extractPublicIdFromUrl(imageUrl: String): String {
        // 예: "https://res.cloudinary.com/demo/image/upload/v123456789/community/my_image.jpg"
        try {
            val uploadMarker = "/upload/"
            val versionIndex = imageUrl.indexOf(uploadMarker)
            if (versionIndex == -1) return ""

            // 버전 정보(예: /v123456/) 이후의 경로를 찾습니다.
            val publicIdStartIndex = imageUrl.indexOf('/', versionIndex + uploadMarker.length)
            if (publicIdStartIndex == -1) return ""

            // 파일 확장자(예: .jpg) 앞까지를 public_id로 간주합니다.
            val publicIdEndIndex = imageUrl.lastIndexOf('.')
            if (publicIdEndIndex == -1 || publicIdEndIndex < publicIdStartIndex) return ""

            // "community/my_image"를 반환합니다.
            return imageUrl.substring(publicIdStartIndex + 1, publicIdEndIndex)
        } catch (e: Exception) {
            log.warn("Could not parse public_id from Cloudinary URL: {}", imageUrl)
            return ""
        }
    }
}