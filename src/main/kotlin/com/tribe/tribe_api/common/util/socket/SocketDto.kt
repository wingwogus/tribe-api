package com.tribe.tribe_api.common.util.socket

sealed class SocketDto {
    data class TripEditMessage(
        val type: EditType,
        val tripId: Long,
        val senderId: Long,
        val data: Any?
    )

    enum class EditType {
        ADD_ITINERARY,
        MOVE_ITINERARY,
        EDIT_ITINERARY,
        DELETE_ITINERARY,
        ADD_WISHLIST,
        DELETE_WISHLIST,
        ADD_CATEGORY,
        MOVE_CATEGORY,
        EDIT_CATEGORY,
        DELETE_CATEGORY,
        JOIN_MEMBER,
        LEAVE_MEMBER,
        CHANGE_ROLE
    }
}