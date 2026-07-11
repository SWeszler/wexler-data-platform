package com.example.eventstore

import kotlinx.serialization.Serializable

@Serializable
data class EventRecord(
    val event_id: String,
    val event_type: String,
    val source: String,
    val message: String,
    val created_at: String
)
