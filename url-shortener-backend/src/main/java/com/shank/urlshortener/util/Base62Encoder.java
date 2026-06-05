package com.shank.urlshortener.util;

import org.springframework.stereotype.Component;

/**
 * Base62 encoder — converts a numeric ID to a short alphanumeric code.
 *
 * Alphabet: 0-9, a-z, A-Z  (62 characters)
 * With 6 characters: 62^6 = 56 billion unique codes
 * With 7 characters: 62^7 = 3.5 trillion unique codes
 *
 * Example: ID 1000 → "G8"
 *
 * This is the same approach used by Bitly, TinyURL, etc.
 */
@Component
public class Base62Encoder {

    private static final String ALPHABET =
        "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int BASE = ALPHABET.length(); // 62
    private static final int MIN_LENGTH = 6;

    /**
     * Encode a numeric ID to a Base62 short code.
     * Pads with '0' to ensure minimum length of 6.
     */
    public String encode(long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("ID must be positive, got: " + id);
        }

        StringBuilder sb = new StringBuilder();
        long num = id;

        while (num > 0) {
            sb.append(ALPHABET.charAt((int)(num % BASE)));
            num /= BASE;
        }

        // Pad to minimum length
        while (sb.length() < MIN_LENGTH) {
            sb.append('0');
        }

        // Reverse for correct order
        return sb.reverse().toString();
    }

    /**
     * Decode a Base62 short code back to numeric ID.
     */
    public long decode(String code) {
        long result = 0;
        for (char c : code.toCharArray()) {
            result = result * BASE + ALPHABET.indexOf(c);
        }
        return result;
    }
}
