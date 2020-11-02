package com.example.time2sleep.model

class Catalog(
    val broker_host: String,
    val broker_port: Int,
    val devices: List<Device>,
    val last_updated: String
)