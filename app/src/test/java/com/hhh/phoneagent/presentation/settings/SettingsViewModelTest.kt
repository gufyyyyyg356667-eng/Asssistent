package com.hhh.phoneagent.presentation.settings

import com.hhh.phoneagent.domain.repository.GeminiKeyStore
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsViewModelTest {

    private class FakeStore(initial: List<String> = emptyList()) : GeminiKeyStore {
        var stored = initial
        override fun keys(): List<String> = stored
        override fun saveKeys(keys: List<String>) {
            stored = keys
        }
    }

    private val key1 = "AIzaSyD-test-key-number-one-0001"
    private val key2 = "AIzaSyD-test-key-number-two-0002"

    @Test
    fun addsOnlyNewValidKeysFromMultilineInput() {
        val store = FakeStore(listOf(key1))
        val vm = SettingsViewModel(store)
        val added = vm.addKeys("$key1\n$key2\nshort\n")
        assertEquals(1, added)
        assertEquals(listOf(key1, key2), store.stored)
    }

    @Test
    fun removeKeyDeletesOnlyThatKey() {
        val store = FakeStore(listOf(key1, key2))
        val vm = SettingsViewModel(store)
        vm.removeKey(key1)
        assertEquals(listOf(key2), store.stored)
    }

    @Test
    fun maskShowsOnlyLastFour() {
        assertEquals("…0002", SettingsViewModel.mask(key2))
    }
}
