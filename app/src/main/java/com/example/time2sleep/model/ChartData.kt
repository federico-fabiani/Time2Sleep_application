package com.example.time2sleep.model

data class ChartData(
    val channel: Channel,
    val feeds: List<Feed>
)

data class Channel(
    val id: Int,
    val name: String,
    val latitude: Float,
    val longitude: Float,
    val field1: String,
    val field2: String,
    val field3: String,
    val field4: String,
    val field5: String,
    val field6: String,
    val created_at: String,
    val updated_at: String,
    val last_entry_id: Int
)

data class Feed(
    val created_at: String,
    val entry_id: Int,
    val field1: Double,
    val field2: Double,
    val field3: Double,
    val field4: Double,
    val field5: Double,
    val field6: Double,
)