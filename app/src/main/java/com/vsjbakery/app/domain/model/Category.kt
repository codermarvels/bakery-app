package com.vsjbakery.app.domain.model

/** A product category, as stored in the database and managed by the administrator. */
data class Category(
    val id: String,
    val slug: String,
    val name: String,
    val tagline: String,
)
