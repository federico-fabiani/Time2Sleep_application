package com.example.time2sleep.model

class SmartAlarm (
    var alarm_set: Boolean = false,
    var alarm_time: String = "",
    var light_set: Boolean = false,
    var network_name: String = "",
    var night_start: String = "",
    var room_name: String = "New Room",
    var night_monitoring: Boolean = false,
    var adaptive_alarm: Boolean = false,
    var sensors: List<String> = emptyList(),
    var actuators: List<String> = emptyList()
){
    fun copyFieldFrom(other: SmartAlarm) {
        this.alarm_set = other.alarm_set
        this.alarm_time = other.alarm_time
        this.light_set = other.light_set
        this.network_name = other.network_name
        this.night_start = other.night_start
        this.room_name = other.room_name
        this.night_monitoring = other.night_monitoring
        this.adaptive_alarm = other.adaptive_alarm
        this.sensors = other.sensors
        this.actuators = other.actuators
    }
}