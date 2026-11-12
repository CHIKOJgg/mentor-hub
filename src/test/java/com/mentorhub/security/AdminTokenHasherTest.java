package com.mentorhub.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdminTokenHasherTest {
    @Test
    void hashesTokensWithSha256AndNormalizesOuterWhitespace() {
        String expected = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

        assertThat(AdminTokenHasher.hash("abc")).isEqualTo(expected);
        assertThat(AdminTokenHasher.hash("  abc  ")).isEqualTo(expected);
        assertThat(AdminTokenHasher.hash(" ")).isEmpty();
    }
}
