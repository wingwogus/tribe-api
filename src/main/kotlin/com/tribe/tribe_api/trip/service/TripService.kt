package com.tribe.tribe_api.trip.service

import com.tribe.tribe_api.common.exception.BusinessException
import com.tribe.tribe_api.common.exception.ErrorCode
import com.tribe.tribe_api.common.util.security.SecurityUtil
import com.tribe.tribe_api.common.util.service.RedisService
import com.tribe.tribe_api.member.repository.MemberRepository
import com.tribe.tribe_api.trip.dto.TripRequest
import com.tribe.tribe_api.trip.dto.TripResponse
import com.tribe.tribe_api.trip.entity.Trip
import com.tribe.tribe_api.trip.entity.TripRole
import com.tribe.tribe_api.trip.repository.TripMemberRepository
import com.tribe.tribe_api.trip.repository.TripRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
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
    private val tripMemberRepository: TripMemberRepository
) {
    companion object {
        private const val INVITE_TOKEN_PREFIX = "INVITE:"
        private const val INVITE_BASE_URL = "https://wego.kro.kr/invite?token="
        private val INVITE_EXPIRATION = Duration.ofMinutes(10)
    }

    fun createTrip(request: TripRequest.Create): TripResponse.TripDetail {
        val currentMemberId = SecurityUtil.getCurrentMemberId()

        val member = memberRepository.findById(currentMemberId).orElse(null)
            ?: throw BusinessException(ErrorCode.MEMBER_NOT_FOUND)

        return request.toEntity()
            .apply { addMember(member, TripRole.OWNER) }
            .also { tripRepository.save(it) }
            .let { TripResponse.TripDetail.from(it) }
    }

    fun updateTrip(tripId: Long, request: TripRequest.Update): TripResponse.TripDetail {
        val currentMemberId = SecurityUtil.getCurrentMemberId()

        return findTripWithMembers(tripId)
            .also { validateTripOwner(it, currentMemberId) }
            .apply { update(request.title, request.startDate, request.endDate, request.country) }
            .let { TripResponse.TripDetail.from(it) }
    }

    fun deleteTrip(tripId: Long) {
        val currentMemberId = SecurityUtil.getCurrentMemberId()

        tripRepository.findById(tripId).orElse(null)
            ?.also { validateTripOwner(it, currentMemberId) }
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
        return TripResponse.Invitation("$INVITE_BASE_URL$token")
    }

    fun joinTrip(request: TripRequest.Join): TripResponse.TripDetail {
        val currentMemberId = SecurityUtil.getCurrentMemberId()
        val tripIdString = redisService.getValues("$INVITE_TOKEN_PREFIX${request.token}")
            ?: throw BusinessException(ErrorCode.INVALID_INVITE_TOKEN)

        val member = memberRepository.findById(currentMemberId).orElse(null)
            ?: throw BusinessException(ErrorCode.MEMBER_NOT_FOUND)
        val trip = findTripWithMembers(tripIdString.toLong())

        if (tripMemberRepository.existsByTripAndMember(trip, member)) {
            throw BusinessException(ErrorCode.ALREADY_JOINED_TRIP)
        }

        trip.addMember(member, TripRole.OWNER)

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

    private fun validateTripOwner(trip: Trip, currentMemberId: Long) {
        val isOwner = trip.members.any {
            it.member?.id == currentMemberId && it.role == TripRole.OWNER
        }

        if (!isOwner) {
            throw BusinessException(ErrorCode.NO_AUTHORITY_TRIP)
        }
    }
}