package com.yuri.petbite.device

import org.junit.Assert.*
import org.junit.Test

class ValidationTest {
    @Test fun portionLimits() {
        assertFalse(Validation.portion(4))
        assertTrue(Validation.portion(5))
        assertTrue(Validation.portion(300))
        assertFalse(Validation.portion(301))
    }
    @Test fun timeLimits() {
        assertTrue(Validation.time(0, 0))
        assertTrue(Validation.time(23, 59))
        assertFalse(Validation.time(24, 0))
        assertFalse(Validation.time(12, 60))
        assertFalse(Validation.time(-1, 30))
    }
    @Test fun normalizeAddress() {
        assertEquals("http://192.168.1.100", Validation.address(" http://192.168.1.100/ "))
        assertEquals("http://10.0.2.2:8080", Validation.address("http://10.0.2.2:8080"))
        assertEquals("https://feeder.example", Validation.address("https://feeder.example"))
    }
    @Test fun rejectInvalidAddresses() {
        listOf("", "192.168.1.100", "ftp://example.com", "http://user:pass@example.com",
            "http://example.com/api", "http://example.com?token=abc", "http://example.com#fragment", "http://example.com:99999").forEach {
            assertTrue("Debe rechazar $it", runCatching { Validation.address(it) }.isFailure)
        }
    }
}
