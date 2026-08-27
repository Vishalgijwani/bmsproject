package com.rhythm.app.optimem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DecisionEngineTest {

    private val engine = DecisionEngine()

    private val cheapResource = DemoResource(
        id = "res_cheap", forPackage = "com.example.cheap",
        label = "cheap", sizeKB = 50, baseFetchLatencyMs = 800
    )

    private val expensiveResource = DemoResource(
        id = "res_big", forPackage = "com.example.big",
        label = "big", sizeKB = 4000, baseFetchLatencyMs = 300
    )

    private val healthyContext = DeviceContext(
        batteryPercent = 80, isCharging = false, isWifi = true, availableStorageMB = 2000
    )

    @Test
    fun `high confidence, cheap resource, wifi, healthy battery means PREFETCH`() {
        val d = engine.decide(cheapResource, probability = 0.85, healthyContext)
        assertEquals(Action.PREFETCH, d.action)
        assertTrue(d.netScore > 0)
    }

    @Test
    fun `low confidence means SKIP regardless of cost`() {
        val d = engine.decide(cheapResource, probability = 0.10, healthyContext)
        assertEquals(Action.SKIP, d.action)
    }

    @Test
    fun `high probability but huge resource on mobile data means SKIP`() {
        val mobile = healthyContext.copy(isWifi = false)
        val d = engine.decide(expensiveResource, probability = 0.9, mobile)
        assertEquals(Action.SKIP, d.action)
    }

    @Test
    fun `same resource on wifi is cheap enough to prefetch`() {
        val d = engine.decide(expensiveResource, probability = 0.9, healthyContext)
        assertEquals(Action.PREFETCH, d.action)
    }

    @Test
    fun `low battery and not charging means SKIP even with good confidence`() {
        val lowBattery = healthyContext.copy(batteryPercent = 10, isCharging = false)
        val d = engine.decide(cheapResource, probability = 0.85, lowBattery)
        assertEquals(Action.SKIP, d.action)
    }

    @Test
    fun `low battery but charging means battery penalty does not block`() {
        val chargingLow = healthyContext.copy(batteryPercent = 10, isCharging = true)
        val d = engine.decide(cheapResource, probability = 0.85, chargingLow)
        assertEquals(Action.PREFETCH, d.action)
    }

    @Test
    fun `low storage headroom means SKIP`() {
        val lowStorage = healthyContext.copy(availableStorageMB = 10)
        val d = engine.decide(cheapResource, probability = 0.85, lowStorage)
        assertEquals(Action.SKIP, d.action)
    }
}