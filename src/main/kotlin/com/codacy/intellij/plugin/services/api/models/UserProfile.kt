package com.codacy.intellij.plugin.services.api.models

// https://api.codacy.com/api/api-docs#getuser
data class UserProfile(
    val id: Int,
    val name: String
)
