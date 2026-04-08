package com.hemax.models

import kotlinx.datetime.Instant

data class User(
    val id: Long,
    val phoneNumber: String,
    val firstName: String,
    val lastName: String? = null,
    val username: String? = null,
    val photoUrl: String? = null,
    val bio: String? = null,
    val isVerified: Boolean = false,
    val isPremium: Boolean = false,
    val lastSeen: Instant? = null
)
