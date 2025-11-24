package com.tribe.tribe_api.community.repository

import com.tribe.tribe_api.community.dto.PostSearchCondition
import com.tribe.tribe_api.community.entity.CommunityPost
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository

@Repository
interface CommunityPostRepositoryCustom {
    fun searchPost(condition: PostSearchCondition, pageable: Pageable): Page<CommunityPost>
}