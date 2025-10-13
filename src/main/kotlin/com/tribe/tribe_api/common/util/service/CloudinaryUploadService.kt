package com.tribe.tribe_api.common.util.service

import com.cloudinary.Cloudinary
import com.cloudinary.utils.ObjectUtils
import com.tribe.tribe_api.common.exception.BusinessException
import com.tribe.tribe_api.common.exception.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

@Service
class CloudinaryUploadService(
    private val cloudinary: Cloudinary
) {
    fun upload(file: MultipartFile): String {
        try {
            val options = ObjectUtils.asMap(
                "folder", "receipts", // 이미지를 Cloudinary의 'receipts' 폴더에 저장
                "resource_type", "image"
            )
            val result = cloudinary.uploader().upload(file.bytes, options)
            return result["secure_url"] as String // 업로드된 이미지의 URL 반환
        } catch (e: Exception) {
            throw BusinessException(ErrorCode.IMAGE_UPLOAD_FAILED)
        }
    }
}