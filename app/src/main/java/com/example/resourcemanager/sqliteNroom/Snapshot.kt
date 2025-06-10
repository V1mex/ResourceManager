package com.example.resourcemanager.sqliteNroom

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "snapshots")
data class Snapshot(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val packageName: String,
    val timestamp: Long = System.currentTimeMillis(), // Дата створення

    // Зберігаємо кожне значення з таблиці
    val timeCpuFgs: String,
    val batteryCpuFgs: String,
    val mahCpuFgs: String,

    val timeCpuFg: String,
    val batteryCpuFg: String,
    val mahCpuFg: String,

    val timeCpuBg: String,
    val batteryCpuBg: String,
    val mahCpuBg: String,

    val timeForegroundServices: String,
    val batteryForegroundServices: String,
    val mahForegroundServices: String,

    val timeForegroundActivities: String,
    val batteryForegroundActivities: String,
    val mahForegroundActivities: String,

    val timeWakelock: String,
    val batteryWakelock: String,
    val mahWakelock: String,

    val timeSensors: String,
    val batterySensors: String,
    val mahSensors: String,

    val timeNetwork: String,
    val batteryNetwork: String,
    val mahNetwork: String,

    val timeJobscheduler: String,
    val batteryJobscheduler: String,
    val mahJobscheduler: String,

    val timeSync: String,
    val batterySync: String,
    val mahSync: String,

    val timeAlarms: String,
    val batteryAlarms: String,
    val mahAlarms: String,

    val timeTop: String,
    val batteryTop: String,
    val mahTop: String
)