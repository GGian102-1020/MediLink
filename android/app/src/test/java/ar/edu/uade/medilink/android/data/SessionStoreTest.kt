package ar.edu.uade.medilink.android.data

import org.junit.Assert.*
import org.junit.Test

class SessionStoreTest {
    private class Memory : SessionPersistence {
        var value: SavedSession? = null
        override fun read() = value
        override fun write(value: SavedSession?) { this.value = value }
    }
    private fun login() = LoginDto("token", "Bearer", 1800,
        PerfilDto(1, 1, "USUARIO", "demo@mail.test", "ACTIVA", "Ana", "Paz", null))

    @Test fun restoresAfterProcessRecreationAndKeepsOriginalExpiry() {
        val memory = Memory()
        SessionStore(memory) { 1_000L }.set(login())
        val restored = SessionStore(memory) { 5_000L }
        assertEquals("token", restored.token)
        assertEquals(1_801_000L, memory.value!!.expiresAt)
        assertNull(SessionStore(memory) { 1_801_000L }.token)
        assertNull(memory.value)
    }
    @Test fun logoutCannotBeRestored() {
        val memory = Memory()
        val store = SessionStore(memory) { 1_000L }
        store.set(login())
        store.clear()
        assertNull(SessionStore(memory) { 2_000L }.login.value)
    }
    @Test fun expiresWhileProcessRemainsAlive() {
        var now = 0L
        val store = SessionStore(Memory()) { now }
        store.set(login())
        now = 1_800_000L
        assertNull(store.token)
        assertNull(store.login.value)
    }
}
