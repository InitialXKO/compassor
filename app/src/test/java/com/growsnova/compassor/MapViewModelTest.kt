package com.growsnova.compassor

import com.growsnova.compassor.data.repository.WaypointRepository
import com.growsnova.compassor.ui.viewmodel.MapViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {

    private lateinit var mapViewModel: MapViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    private class FakeWaypointDao : WaypointDao {
        override fun getAllWaypointsFlow(): Flow<List<Waypoint>> = flowOf(emptyList())
        override suspend fun getAllWaypoints(): List<Waypoint> = emptyList()
        override suspend fun insert(waypoint: Waypoint): Long = 1L
        override suspend fun update(waypoint: Waypoint) {}
        override suspend fun delete(waypoint: Waypoint) {}
        override suspend fun getWaypointById(id: Long): Waypoint? = null
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val repository = WaypointRepository(FakeWaypointDao())
        val exceptionHandler = CoroutineExceptionHandler { _, _ -> }
        mapViewModel = MapViewModel(repository, exceptionHandler)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialState() {
        assertFalse(mapViewModel.isTrackingMode.value)
        assertTrue(mapViewModel.isFollowMode.value)
    }

    @Test
    fun testSetTrackingMode() {
        mapViewModel.setTrackingMode(true)
        assertTrue(mapViewModel.isTrackingMode.value)
        assertTrue(mapViewModel.isFollowMode.value)

        mapViewModel.setTrackingMode(false)
        assertFalse(mapViewModel.isTrackingMode.value)
    }

    @Test
    fun testToggleTrackingMode() {
        assertFalse(mapViewModel.isTrackingMode.value)

        mapViewModel.toggleTrackingMode()
        assertTrue(mapViewModel.isTrackingMode.value)

        mapViewModel.toggleTrackingMode()
        assertFalse(mapViewModel.isTrackingMode.value)
    }

    @Test
    fun testSetFollowModeFalseClearsTrackingMode() {
        mapViewModel.setTrackingMode(true)
        assertTrue(mapViewModel.isTrackingMode.value)

        mapViewModel.setFollowMode(false)
        assertFalse(mapViewModel.isFollowMode.value)
        assertFalse(mapViewModel.isTrackingMode.value)
    }
}
