package com.example.a6thfingercontrollapp.ble

data class DeviceSettings(
    val fsrPin: Int = 34,
    val fsrPullupOhm: Int = 4700,
    val fsrStartOhm: Int = 100000,
    val fsrMaxOhm: Int = 20000,

    val flexPin: Int = 35,
    val flexFlatOhm: Int = 45000,
    val flexBendOhm: Int = 33400,

    val vibroPin: Int = 25,
    val vibroPulseFreqHz: Int = 10,
    val vibroThreshold: Int = 50,
    val vibroPowerPct: Int = 60,

    val servoPin: Int = 18,
    val servoMinDeg: Int = 40,
    val servoMaxDeg: Int = 180,
    val servoManualMode: Boolean = false,
    val servoManualDeg: Int = 90
)
