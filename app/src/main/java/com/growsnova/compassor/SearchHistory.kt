package com.growsnova.compassor

import androidx.room.Entity
import androidx.room.PrimaryKey

import androidx.room.Index

@Entity(
    tableName = "search_history",
    indices = [Index(value = ["timestamp"])]
)
data class SearchHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val query: String,
    val timestamp: Long = System.currentTimeMillis()
)
