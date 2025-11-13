package com.alpian.instantpay.infrastructure.persistence.converter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EncryptionAttributeConverter Unit Tests")
class EncryptionAttributeConverterTest {

    private EncryptionAttributeConverter converter;
    private static final String TEST_ENCRYPTION_KEY = "VTgDasP1R776SZNpu+5p+KYyznjZUaGbzBO2Pfs7rAY=";

    @BeforeEach
    void setUp() {
        converter = new EncryptionAttributeConverter();
        ReflectionTestUtils.setField(converter, "encryptionKeyBase64", TEST_ENCRYPTION_KEY);
    }

    @Test
    @DisplayName("Should encrypt account number to byte array")
    void shouldEncryptAccountNumber() {
        String accountNumber = "1234567890";

        byte[] encrypted = converter.convertToDatabaseColumn(accountNumber);

        assertThat(encrypted).isNotNull();
        assertThat(encrypted).hasSizeGreaterThan(0);
        assertThat(encrypted).isNotEqualTo(accountNumber.getBytes());
    }

    @Test
    @DisplayName("Should decrypt byte array back to original account number")
    void shouldDecryptToOriginalAccountNumber() {
        String originalAccountNumber = "1234567890";
        byte[] encrypted = converter.convertToDatabaseColumn(originalAccountNumber);

        String decrypted = converter.convertToEntityAttribute(encrypted);

        assertThat(decrypted).isEqualTo(originalAccountNumber);
    }

    @Test
    @DisplayName("Should handle null values in convertToDatabaseColumn")
    void shouldHandleNullInConvertToDatabaseColumn() {
        byte[] result = converter.convertToDatabaseColumn(null);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should handle null values in convertToEntityAttribute")
    void shouldHandleNullInConvertToEntityAttribute() {
        String result = converter.convertToEntityAttribute(null);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should encrypt and decrypt different account numbers correctly")
    void shouldEncryptAndDecryptDifferentAccountNumbers() {
        String[] accountNumbers = {
                "1234567890",
                "9876543210",
                "5555555555",
                "0000000000"
        };

        for (String accountNumber : accountNumbers) {
            byte[] encrypted = converter.convertToDatabaseColumn(accountNumber);
            String decrypted = converter.convertToEntityAttribute(encrypted);

            assertThat(decrypted).isEqualTo(accountNumber);
            assertThat(encrypted).isNotNull();
        }
    }

    @Test
    @DisplayName("Should produce different ciphertext for same plaintext (due to IV)")
    void shouldProduceDifferentCiphertextForSamePlaintext() {
        String accountNumber = "1234567890";

        byte[] encrypted1 = converter.convertToDatabaseColumn(accountNumber);
        byte[] encrypted2 = converter.convertToDatabaseColumn(accountNumber);

        assertThat(encrypted1).isNotEqualTo(encrypted2);

        assertThat(converter.convertToEntityAttribute(encrypted1)).isEqualTo(accountNumber);
        assertThat(converter.convertToEntityAttribute(encrypted2)).isEqualTo(accountNumber);
    }

    @Test
    @DisplayName("Should throw exception when encryption key is invalid")
    void shouldThrowExceptionWhenEncryptionKeyInvalid() {
        EncryptionAttributeConverter invalidConverter = new EncryptionAttributeConverter();
        ReflectionTestUtils.setField(invalidConverter, "encryptionKeyBase64", "invalid-key");

        assertThatThrownBy(() -> invalidConverter.convertToDatabaseColumn("1234"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Encryption failed");
    }
}
