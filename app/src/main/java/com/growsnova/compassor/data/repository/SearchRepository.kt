package com.growsnova.compassor.data.repository

import com.growsnova.compassor.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchRepository @Inject constructor(
    private val searchHistoryDao: SearchHistoryDao
) {
    fun getSearchHistoryFlow(): Flow<List<SearchHistory>> = searchHistoryDao.getAllHistoryFlow()

    suspend fun insertSearchHistory(query: String) {
        val history = SearchHistory(query = query, timestamp = System.currentTimeMillis())
        searchHistoryDao.insert(history)
    }

    suspend fun clearSearchHistory() {
        searchHistoryDao.deleteAll()
    }

    suspend fun deleteSearchHistory(id: Long) {
        searchHistoryDao.delete(id)
    }
}
