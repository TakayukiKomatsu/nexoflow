package com.srm.creditengine.assignor.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TaxIdTest {

    @Test
    void normalizesTaxIdWithoutChangingAlphanumericContent() {
        assertThat(TaxId.normalize("12.345-AB")).isEqualTo("12345AB");
    }

    @Test
    void stripsAllNonAlphanumericCharacters() {
        assertThat(TaxId.normalize("00.000.000/0001-91")).isEqualTo("00000000000191");
    }

    @Test
    void uppercasesLetters() {
        assertThat(TaxId.normalize("abc123")).isEqualTo("ABC123");
    }

    @Test
    void rejectsBlankRawValue() {
        assertThatThrownBy(() -> TaxId.normalize(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAllPunctuationThatNormalizesToBlank() {
        assertThatThrownBy(() -> TaxId.normalize("...---///"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
