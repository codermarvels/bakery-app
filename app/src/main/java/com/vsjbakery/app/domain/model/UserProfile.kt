package com.vsjbakery.app.domain.model

/** The signed-in person, with the role the server has assigned to them. */
data class UserProfile(
    val id: String,
    val email: String,
    val fullName: String,
    val role: AppRole,
)
