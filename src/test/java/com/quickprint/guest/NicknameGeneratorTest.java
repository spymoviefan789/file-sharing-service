package com.quickprint.guest;

import org.junit.jupiter.api.RepeatedTest;

import static org.assertj.core.api.Assertions.assertThat;

class NicknameGeneratorTest {

    // Run many times: every generated name must fit the nickname slug rules
    // FileService enforces ([a-z0-9-]{1,20}) and end in two digits.
    @RepeatedTest(200)
    void alwaysProducesAValidSlugEndingInTwoDigits() {
        String name = NicknameGenerator.generate();

        assertThat(name).matches("[a-z]+[0-9]{2}");
        assertThat(name.length()).isLessThanOrEqualTo(20);
    }
}
