package com.tribe.tribe_api.community.repository

import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.jpa.impl.JPAQuery
import com.querydsl.jpa.impl.JPAQueryFactory
import com.tribe.tribe_api.community.dto.PostSearchCondition
import com.tribe.tribe_api.community.entity.CommunityPost
import com.tribe.tribe_api.community.entity.QCommunityPost.communityPost
import com.tribe.tribe_api.trip.entity.Country
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.support.PageableExecutionUtils

class CommunityPostRepositoryImpl(
    private val queryFactory: JPAQueryFactory
): CommunityPostRepositoryCustom {

    fun <T> JPAQuery<T>.limit(pageSize:Int): JPAQuery<T> {
        return this.limit(pageSize.toLong())
    }

    override fun searchPost(
        condition: PostSearchCondition,
        pageable: Pageable
    ): Page<CommunityPost> {

        val content = queryFactory
            .selectFrom(communityPost)
            .where(
                countryEq(condition.country),
                authorIdEq(condition.authorId)
            )
            .offset(pageable.offset)
            .limit(pageable.pageSize)
            .fetch()

        val countQuery = queryFactory
            .select(communityPost.count())
            .from(communityPost)
            .where(
                countryEq(condition.country),
                authorIdEq(condition.authorId)
            )

        return PageableExecutionUtils.getPage(content, pageable) { countQuery.fetchOne() ?: 0L }
    }


    private fun countryEq(country: String?): BooleanExpression? {
        return country
            ?.takeIf { it.isNotBlank() }
            ?.let { communityPost.trip.country.eq(Country.valueOf(it.uppercase())) }
    }


    private fun authorIdEq(authorId: Long?): BooleanExpression? {
        return authorId?.let { communityPost.author.id.eq(it) }
    }
}