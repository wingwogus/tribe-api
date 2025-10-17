package com.tribe.tribe_api.trip.service

import com.tribe.tribe_api.common.exception.BusinessException
import com.tribe.tribe_api.common.exception.ErrorCode
import com.tribe.tribe_api.common.util.security.SecurityUtil
import com.tribe.tribe_api.common.util.service.RedisService
import com.tribe.tribe_api.member.entity.Member
import com.tribe.tribe_api.member.repository.MemberRepository
import com.tribe.tribe_api.trip.dto.TripRequest
import com.tribe.tribe_api.trip.dto.TripResponse
import com.tribe.tribe_api.trip.entity.Trip
import com.tribe.tribe_api.trip.entity.TripRole
import com.tribe.tribe_api.trip.repository.TripMemberRepository
import com.tribe.tribe_api.trip.repository.TripRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
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
    @Value("\${app.url}") private val appUrl: String
) {
    companion object {
        private const val INVITE_TOKEN_PREFIX = "INVITE:"
        private const val INVITE_PATH = "/invite?token="
        private val INVITE_EXPIRATION = Duration.ofDays(7)
    }

    fun createTrip(request: TripRequest.Create): TripResponse.TripDetail {
        val currentMemberId = SecurityUtil.getCurrentMemberId()

        val member = findMember(currentMemberId)

        return request.toEntity()
            .apply { addMember(member, TripRole.OWNER) }
            .also { tripRepository.save(it) }
            .let { TripResponse.TripDetail.from(it) }
    }

    fun updateTrip(tripId: Long, request: TripRequest.Update): TripResponse.TripDetail {
        return findTripWithMembers(tripId)
            .apply {
                update(
                request.title,
                request.startDate,
                request.endDate,
                    request.country
                )
            }
            .let { TripResponse.TripDetail.from(it) }
    }

    fun deleteTrip(tripId: Long) {
        tripRepository.findByIdOrNull(tripId)
            ?.let { tripRepository.delete(it) }
            ?: throw BusinessException(ErrorCode.TRIP_NOT_FOUND)
    }

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

    fun createInvitation(tripId: Long): TripResponse.Invitation {
        tripRepository.existsById(tripId).takeIf { it }
            ?: throw BusinessException(ErrorCode.TRIP_NOT_FOUND)

        val token = generateInvitationToken()
        redisService.setValues("$INVITE_TOKEN_PREFIX$token", tripId.toString(), INVITE_EXPIRATION)
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

        return TripResponse.TripDetail.from(trip)
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