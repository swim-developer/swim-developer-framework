package com.github.swim_developer.framework.unit.infrastructure;

import com.github.swim_developer.framework.infrastructure.util.CompressionUtil;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa comportamento de CompressionUtil.
 *
 * Garantias de Negócio:
 * 1. compress() + decompress() = roundtrip perfeito (dados originais)
 * 2. shouldCompress() retorna true apenas para strings > 500 caracteres
 * 3. Compressão reduz tamanho significativamente para dados repetitivos
 * 4. Null safety: null e empty string retornam empty byte array / empty string
 */
@Tag("infrastructure")
class CompressionUtilTest {

    @Test
    void compress_andDecompress_roundtrip() throws IOException {
        // GIVEN: String original
        String original = "This is a test payload that will be compressed and decompressed";

        // WHEN: Compress e depois decompress
        byte[] compressed = CompressionUtil.compress(original);
        String decompressed = CompressionUtil.decompress(compressed);

        // THEN: Resultado idêntico ao original
        assertThat(decompressed).isEqualTo(original);
    }

    @Test
    void compress_reducesSize_forRepetitiveData() throws IOException {
        // GIVEN: String repetitiva (comprime bem)
        String original = "a".repeat(1000);

        // WHEN: Comprimido
        byte[] compressed = CompressionUtil.compress(original);

        // THEN: Tamanho reduzido significativamente
        assertThat(compressed).hasSizeLessThan(original.length());
    }

    @Test
    void compress_handlesNullInput() throws IOException {
        // GIVEN/WHEN: Null input
        byte[] result = CompressionUtil.compress(null);

        // THEN: Retorna byte array vazio
        assertThat(result).isEmpty();
    }

    @Test
    void compress_handlesEmptyString() throws IOException {
        // GIVEN/WHEN: Empty string
        byte[] result = CompressionUtil.compress("");

        // THEN: Retorna byte array vazio
        assertThat(result).isEmpty();
    }

    @Test
    void decompress_handlesNullInput() throws IOException {
        // GIVEN/WHEN: Null input
        String result = CompressionUtil.decompress(null);

        // THEN: Retorna string vazia
        assertThat(result).isEmpty();
    }

    @Test
    void decompress_handlesEmptyByteArray() throws IOException {
        // GIVEN/WHEN: Empty byte array
        String result = CompressionUtil.decompress(new byte[0]);

        // THEN: Retorna string vazia
        assertThat(result).isEmpty();
    }

    @Test
    void roundtrip_withLargePayload() throws IOException {
        // GIVEN: Payload grande (> 1KB)
        String original = "Large payload: " + "x".repeat(5000);

        // WHEN: Roundtrip
        byte[] compressed = CompressionUtil.compress(original);
        String decompressed = CompressionUtil.decompress(compressed);

        // THEN: Dados preservados
        assertThat(decompressed).isEqualTo(original);
    }

    @Test
    void roundtrip_withSpecialCharacters() throws IOException {
        // GIVEN: Payload com caracteres especiais e UTF-8
        String original = "Special: €, £, ñ, 中文, 🚀, \n\t\r";

        // WHEN: Roundtrip
        byte[] compressed = CompressionUtil.compress(original);
        String decompressed = CompressionUtil.decompress(compressed);

        // THEN: Caracteres especiais preservados
        assertThat(decompressed).isEqualTo(original);
    }

    @Test
    void shouldCompress_returnsFalse_forShortStrings() {
        // GIVEN: String curta (< 500 chars)
        String shortString = "short";

        // WHEN/THEN: Não deve comprimir
        assertThat(CompressionUtil.shouldCompress(shortString)).isFalse();
    }

    @Test
    void shouldCompress_returnsFalse_atThreshold() {
        // GIVEN: String exatamente no threshold (500 chars)
        String atThreshold = "a".repeat(500);

        // WHEN/THEN: Não deve comprimir (> 500, não >= 500)
        assertThat(CompressionUtil.shouldCompress(atThreshold)).isFalse();
    }

    @Test
    void shouldCompress_returnsTrue_aboveThreshold() {
        // GIVEN: String acima do threshold (501 chars)
        String aboveThreshold = "a".repeat(501);

        // WHEN/THEN: Deve comprimir
        assertThat(CompressionUtil.shouldCompress(aboveThreshold)).isTrue();
    }

    @Test
    void shouldCompress_returnsFalse_forNull() {
        // GIVEN/WHEN/THEN: Null não deve comprimir
        assertThat(CompressionUtil.shouldCompress(null)).isFalse();
    }

    @Test
    void shouldCompress_returnsFalse_forEmptyString() {
        // GIVEN/WHEN/THEN: Empty string não deve comprimir
        assertThat(CompressionUtil.shouldCompress("")).isFalse();
    }

    @Test
    void roundtrip_withXmlPayload() throws IOException {
        // GIVEN: Payload XML (caso de uso real - AIXM messages)
        String original = """
                <?xml version="1.0"?>
                <message:AIXMBasicMessage xmlns:message="http://www.aixm.aero/schema/5.1.1/message">
                    <message:hasMember>
                        <event:Event gml:id="evt-001">
                            <event:scenario>RWY.CLS</event:scenario>
                        </event:Event>
                    </message:hasMember>
                </message:AIXMBasicMessage>
                """;

        // WHEN: Roundtrip
        byte[] compressed = CompressionUtil.compress(original);
        String decompressed = CompressionUtil.decompress(compressed);

        // THEN: XML preservado
        assertThat(decompressed).isEqualTo(original);
    }
}
