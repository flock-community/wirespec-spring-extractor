package com.acme.api.dto

data class Page<T>(
    val content: List<T>,
    val totalElements: Long,
    val number: Int,
)
