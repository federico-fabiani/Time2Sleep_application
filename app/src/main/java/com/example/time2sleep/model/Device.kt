package com.example.time2sleep.model

class Device(
    val ip: String,
    val port: Int,
    val name: String,
    val sensors: List<String>,
    val actuators: List<String>
)