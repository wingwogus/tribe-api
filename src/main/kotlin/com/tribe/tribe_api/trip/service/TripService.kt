package com.tribe.tribe_api.trip.service

import com.tribe.tribe_api.common.exception.BusinessException
import com.tribe.tribe_api.common.exception.ErrorCode
import com.tribe.tribe_api.common.util.security.SecurityUtil
import com.tribe.tribe_api.common.util.service.RedisService
import com.tribe.tribe_api.community.repository.CommunityPostRepository
import com.tribe.tribe_api.itinerary.entity.Category
import com.tribe.tribe_api.itinerary.entity.ItineraryItem
import com.tribe.tribe_api.member.entity.Member
import com.tribe.tribe_api.member.repository.MemberRepository
import com.tribe.tribe_api.trip.dto.TripRequest
import com.tribe.tribe_api.trip.dto.TripResponse
import com.tribe.tribe_api.trip.entity.Trip
import com.tribe.tribe_api.trip.entity.TripRole
import com.tribe.tribe_api.trip.repository.TripMemberRepository
import com.tribe.tribe_api.trip.repository.TripRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Duration
import java.util.*

@Service
@Transactional
class TripService(
    private val memberRepository: MemberRepository,
    private val tripRepository: TripRepository,
    private val redisService: RedisService,
    private val tripMemberRepository: TripMemberRepository,
    private val communityPostRepository: CommunityPostRepository,
    @Value("\${app.url}") private val appUrl: String
) {
    companion object {
        private const val INVITE_TOKEN_PREFIX = "INVITE:"
        private const val INVITE_PATH = "/invite?token="
        private val INVITE_EXPIRATION = Duration.ofDays(7)
    }

    val logger = LoggerFactory.getLogger(javaClass)

    fun createTrip(request: TripRequest.Create): TripResponse.TripDetail {
        val currentMemberId = SecurityUtil.getCurrentMemberId()

        val member = findMember(currentMemberId)

        return request.toEntity()
            .apply { addMember(member, TripRole.OWNER) }
            .also {
                val save = tripRepository.save(it)
                logger.info("Trip created, Trip Id: {} ", save.id)
            }
            .let { TripResponse.TripDetail.from(it) }
    }

    @PreAuthorize("@tripSecurityService.isTripOwner(#tripId)")
    fun updateTrip(tripId: Long, request: TripRequest.Update): TripResponse.TripDetail {
        return findTripWithMembers(tripId)
            .apply {
                update(
                request.title,
                request.startDate,
                request.endDate,
                    request.country
                )

                logger.info("Trip updated, Trip Id: {}", this.id)
            }
            .let { TripResponse.TripDetail.from(it) }
    }

    @PreAuthorize("@tripSecurityService.isTripOwner(#tripId)")
    fun deleteTrip(tripId: Long) {
        tripRepository.findByIdOrNull(tripId)
            ?.let { tripRepository.delete(it) }
            ?: throw BusinessException(ErrorCode.TRIP_NOT_FOUND)

        logger.info("Trip deleted, Trip Id: {}", tripId)
    }

    @PreAuthorize("@tripSecurityService.isTripMember(#tripId)")
    @Transactional(readOnly = true)
    fun getTripDetails(tripId: Long): TripResponse.TripDetail {
        return TripResponse.TripDetail.from(findTripWithMembers(tripId))
    }

    @Transactional(readOnly = true)
    fun getAllTrips(pageable: Pageable): Page<TripResponse.SimpleTrip> {
        val currentMemberId = SecurityUtil.getCurrentMemberId()

        val trips = tripRepository.findTripsByMemberId(currentMemberId, pageable)

        return trips.map { TripResponse.SimpleTrip.from(it) }
    }

    @PreAuthorize("@tripSecurityService.isTripAdmin(#tripId)")
    fun createInvitation(tripId: Long): TripResponse.Invitation {
        tripRepository.existsById(tripId).takeIf { it }
            ?: throw BusinessException(ErrorCode.TRIP_NOT_FOUND)

        val token = generateInvitationToken()
        redisService.setValues("$INVITE_TOKEN_PREFIX$token", tripId.toString(), INVITE_EXPIRATION)
        logger.info("Invitation created for Trip Id: {}", tripId)
        return TripResponse.Invitation("$appUrl$INVITE_PATH$token")
    }

    fun joinTrip(request: TripRequest.Join): TripResponse.TripDetail {
        val currentMemberId = SecurityUtil.getCurrentMemberId()
        val tripId= (redisService.getValues("$INVITE_TOKEN_PREFIX${request.token}")
            ?: throw BusinessException(ErrorCode.INVALID_INVITE_TOKEN))
            .toLong()

        if (tripMemberRepository.existsByTripIdAndMemberId(tripId, currentMemberId)) {
            throw BusinessException(ErrorCode.ALREADY_JOINED_TRIP)
        }

        val member = findMember(currentMemberId)

        val trip = findTripWithMembers(tripId)

        trip.addMember(member, TripRole.MEMBER)

        logger.info("Trip joined successfully, Trip Id: {}, Member Id: {}", trip.id, currentMemberId)

        return TripResponse.TripDetail.from(trip)
    }

    fun importTripFromPost(request: TripRequest.Import):TripResponse.TripDetail {
        val member = memberRepository.findByIdOrNull(SecurityUtil.getCurrentMemberId())
            ?: throw BusinessException(ErrorCode.MEMBER_NOT_FOUND)

        val post = (communityPostRepository.findByIdOrNull(request.postId)
            ?: throw BusinessException(ErrorCode.POST_NOT_FOUND))

        val originalTripId = post.trip.id!!

        val originalTrip = tripRepository.findTripWithFullItineraryById(originalTripId)
            ?: throw BusinessException(ErrorCode.TRIP_NOT_FOUND)

        val importTrip = Trip(
            title = request.title,
            startDate = request.startDate,
            endDate = request.endDate,
            country = originalTrip.country,
        )

        importTrip.addMember(member, TripRole.OWNER)

        // 각 원본 카테고리를 반복해서
        originalTrip.categories.forEach { originalCategory ->

            // 새로운 카테고리를 생성하고 새로운 여행에 연결
            val importCategory = Category(
                importTrip,         // 새 부모
                originalCategory.day,
                originalCategory.name,
                originalCategory.order
            )

            // 원본 아이템들을 새 아이템으로 매핑하여
            // 새 카테고리의 itineraryItems 리스트에 바로 추가(mapTo 활용)
            originalCategory.itineraryItems.mapTo(importCategory.itineraryItems) { originalItem ->
                ItineraryItem(
                    importCategory,
                    originalItem.place,
                    originalItem.title,
                    originalItem.time,
                    originalItem.order,
                    originalItem.memo
                )
            }

            // 새로운 카테고리를 새로운 여행에 추가
            importTrip.categories.add(importCategory)
        }

        // CascadeAll 설정으로 trip만 저장해도 전부 저장됨
        tripRepository.save(importTrip)

        logger.info("Trip imported successfully, Trip Id: {}", importTrip.id)

        return TripResponse.TripDetail.from(importTrip)
    }

    private fun findTripWithMembers(tripId: Long): Trip {
        return tripRepository.findTripWithMembersById(tripId)
            ?: throw BusinessException(ErrorCode.TRIP_NOT_FOUND)
    }

    private fun generateInvitationToken(): String {
        val randomBytes = ByteArray(16)
        SecureRandom().nextBytes(randomBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes)
    }


    private fun findMember(currentMemberId: Long): Member {
        return memberRepository.findByIdOrNull(currentMemberId)
            ?: throw BusinessException(ErrorCode.MEMBER_NOT_FOUND)
    }
}