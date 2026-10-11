package net.redngreen.easypos.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {

    private val hasher = PinHasher()

    @Test
    fun `hash and verify roundtrip`() {
        val salt = hasher.generateSalt()
        val hash = hasher.hash("1234", salt)
        assertTrue(hasher.verify("1234", salt, hash))
    }

    @Test
    fun `wrong pin is rejected`() {
        val salt = hasher.generateSalt()
        val hash = hasher.hash("1234", salt)
        assertFalse(hasher.verify("1235", salt, hash))
        assertFalse(hasher.verify("123", salt, hash))
        assertFalse(hasher.verify("12345", salt, hash))
    }

    @Test
    fun `same pin different salts produce different hashes`() {
        val saltA = hasher.generateSalt()
        val saltB = hasher.generateSalt()
        assertNotEquals(saltA, saltB)
        assertNotEquals(hasher.hash("999999", saltA), hasher.hash("999999", saltB))
        // …and each salt verifies against only its own hash.
        assertTrue(hasher.verify("999999", saltA, hasher.hash("999999", saltA)))
        assertFalse(hasher.verify("999999", saltB, hasher.hash("999999", saltA)))
    }

    @Test
    fun `salts are fresh randomness`() {
        val salts = (1..32).map { hasher.generateSalt() }.toSet()
        assertEquals(32, salts.size)
    }

    @Test
    fun `pin format is 4 to 6 digits`() {
        assertTrue(hasher.isValidPinFormat("1234"))
        assertTrue(hasher.isValidPinFormat("123456"))
        assertFalse(hasher.isValidPinFormat("123"))
        assertFalse(hasher.isValidPinFormat("1234567"))
        assertFalse(hasher.isValidPinFormat("12a4"))
        assertFalse(hasher.isValidPinFormat(""))
        assertFalse(hasher.isValidPinFormat("12 4"))
    }
}
