package com.tribe.tribe_api.common.exception

import lombok.Getter

@Getter
class BusinessException(val errorCode: ErrorCode) : RuntimeException(errorCode.message)