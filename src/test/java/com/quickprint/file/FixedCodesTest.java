package com.quickprint.file;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FixedCodesTest {

    @Test
    void generatesSixCharsFromTheUnambiguousAlphabet() {
        assertThat(FixedCodes.generate())
                .hasSize(6)
                .matches("[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{6}");
    }

    @Test
    void matchesNormalizesCaseAndWhitespace() {
        assertThat(FixedCodes.matches("ABC234", "  abc234  ")).isTrue();
        assertThat(FixedCodes.matches("ABC234", "ZZZZZZ")).isFalse();
        assertThat(FixedCodes.matches("ABC234", null)).isFalse();
    }

    @Test
    void twoGeneratedCodesAlmostAlwaysDiffer() {
        assertThat(FixedCodes.generate()).isNotEqualTo(FixedCodes.generate());
    }
}
