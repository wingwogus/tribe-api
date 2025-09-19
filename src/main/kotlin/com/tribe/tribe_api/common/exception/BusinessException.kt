package com.tribe.tribe_api.common.exception

class BusinessException(val errorCode: ErrorCode) : RuntimeException(errorCode.message)