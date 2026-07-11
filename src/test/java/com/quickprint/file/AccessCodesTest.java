package com.quickprint.file;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccessCodesTest {

    @Test
    void acceptsTheCurrentCodeAndRejectsAWrongOne() {
        String secret = AccessCodes.newSecret();
        String code = AccessCodes.currentCode(secret);

        assertThat(AccessCodes.isValid(secret, code)).isTrue();
        assertThat(AccessCodes.isValid(secret, "ZZZZZZ")).isFalse();
        assertThat(AccessCodes.isValid(secret, null)).isFalse();
    }

    @Test
    void normalizesCaseAndWhitespaceBeforeChecking() {
        String secret = AccessCodes.newSecret();
        String code = AccessCodes.currentCode(secret);

        assertThat(AccessCodes.isValid(secret, "  " + code.toLowerCase() + "  ")).isTrue();
    }

    @Test
    void codeIsSixCharsFromTheUnambiguousAlphabet() {
        String code = AccessCodes.currentCode(AccessCodes.newSecret());

        assertThat(code).hasSize(6).matches("[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{6}");
    }

    @Test
    void differentSecretsAlmostAlwaysProduceDifferentCodes() {
        // Not a hard guarantee, but a collision here would be astronomically rare.
        assertThat(AccessCodes.currentCode(AccessCodes.newSecret()))
                .isNotEqualTo(AccessCodes.currentCode(AccessCodes.newSecret()));
    }
}
