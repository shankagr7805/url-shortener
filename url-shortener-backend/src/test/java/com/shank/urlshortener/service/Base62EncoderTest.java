package com.shank.urlshortener.service;

import com.shank.urlshortener.util.Base62Encoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Base62EncoderTest {

    private Base62Encoder encoder;

    @BeforeEach
    void setUp() {
        encoder = new Base62Encoder();
    }

    @Test
    void encode_shouldReturnMinLength6() {
        String code = encoder.encode(1L);
        assertTrue(code.length() >= 6, "Short code should be at least 6 characters");
    }

    @Test
    void encode_shouldProduceUniqueCodesForDifferentIds() {
        String code1 = encoder.encode(1L);
        String code2 = encoder.encode(2L);
        assertNotEquals(code1, code2, "Different IDs should produce different codes");
    }

    @Test
    void encode_shouldThrowForNonPositiveId() {
        assertThrows(IllegalArgumentException.class, () -> encoder.encode(0L));
        assertThrows(IllegalArgumentException.class, () -> encoder.encode(-1L));
    }

    @Test
    void encode_shouldOnlyContainBase62Characters() {
        for (long id = 1; id <= 1000; id++) {
            String code = encoder.encode(id);
            assertTrue(code.matches("[0-9a-zA-Z]+"),
                "Code should only contain alphanumeric chars: " + code);
        }
    }

    @Test
    void encode_shouldHandleLargeIds() {
        // 100 million URLs
        String code = encoder.encode(100_000_000L);
        assertNotNull(code);
        assertTrue(code.length() >= 6);
    }

    @Test
    void decode_shouldReverseEncode() {
        long originalId = 12345L;
        String code = encoder.encode(originalId);
        long decodedId = encoder.decode(code);
        assertEquals(originalId, decodedId, "Decode should reverse encode");
    }
}
