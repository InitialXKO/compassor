package com.growsnova.compassor

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchHistoryDao {
    @Insert
    suspend fun insert(searchHistory: SearchHistory): Long

    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT 20")
    suspend fun getRecentSearches(): List<SearchHistory>

    @Query("SELECT * FROM search_history ORDER BY timestamp DESC")
    fun getAllHistoryFlow(): Flow<List<SearchHistory>>

    @Query("DELETE FROM search_history")
    suspend fun deleteAll()

    @Query("DELETE FROM search_history WHERE id = :id")
    suspend fun delete(id: Long)
}
