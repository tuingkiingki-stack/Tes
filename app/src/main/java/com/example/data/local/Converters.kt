package com.example.data.local

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromDoubleList(list: List<Double>?): String {
        return list?.joinToString(",") ?: ""
    }

    @TypeConverter
    fun toDoubleList(data: String?): List<Double> {
        if (data.isNullOrBlank()) return emptyList()
        return data.split(",").mapNotNull { it.trim().toDoubleOrNull() }
    }

    @TypeConverter
    fun fromIntList(list: List<Int>?): String {
        return list?.joinToString(",") ?: ""
    }

    @TypeConverter
    fun toIntList(data: String?): List<Int> {
        if (data.isNullOrBlank()) return emptyList()
        return data.split(",").mapNotNull { it.trim().toIntOrNull() }
    }
}
