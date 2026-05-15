package com.moment.api.db

import kotlinx.serialization.Serializable

@Serializable
data class ClerkUserData(
    val id: String,
    val email: String,
    val displayName: String?,
    val imageUrl: String?,
    val username: String?,
)

@Serializable
data class Profile(
    val id: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val coupleId: String? = null,
    val partnerId: String? = null,
    val isCoupleLeader: Boolean = false,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class Couple(
    val id: String,
    val inviteCode: String,
    val leaderId: String,
    val partnerId: String? = null,
    val name: String,
    val createdAt: String,
)

@Serializable
data class UpsertProfileRequest(
    val username: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
)

@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: String? = null,
)
