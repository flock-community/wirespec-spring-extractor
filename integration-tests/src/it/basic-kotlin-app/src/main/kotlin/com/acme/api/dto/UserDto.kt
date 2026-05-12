package com.acme.api.dto

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

data class UserDto(
    val id: String,
    val age: Int,
    val active: Boolean,
    val role: Role,
    val tags: List<String>,
    val nickname: String?,
    val createdAt: LocalDateTime,
    val lastSeen: Instant,
    val timezone: ZoneOffset,
    val balance: BigDecimal,
    val _internalId: String,
    val SystemKey: String,
)
