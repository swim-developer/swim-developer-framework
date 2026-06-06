package com.github.swim_developer.framework.unit.application;

import com.github.swim_developer.framework.application.service.AbstractSubscriptionHashCalculator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa comportamento de AbstractSubscriptionHashCalculator.
 *
 * Garantias de Negócio:
 * 1. SHA-256 é determinístico (mesmo input = mesmo hash)
 * 2. Hash tem 64 caracteres hexadecimais
 * 3. sortedListToString() ordena alfabeticamente, lowercase, remove nulls/blanks
 * 4. nullSafe() converte null para string vazia, aplica lowercase + trim
 */
@Tag("application")
class SubscriptionHashCalculatorTest {

    private final TestHashCalculator calculator = new TestHashCalculator();

    @Test
    void sha256_isDeterministic() {
        // GIVEN: Mesmo input
        String input = "test-payload";

        // WHEN: Hash calculado duas vezes
        String hash1 = calculator.exposeSha256(input);
        String hash2 = calculator.exposeSha256(input);

        // THEN: Resultado idêntico
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void sha256_produces64CharHexString() {
        // GIVEN/WHEN: Hash calculado
        String hash = calculator.exposeSha256("any-value");

        // THEN: 64 caracteres hexadecimais
        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    void sha256_knownValueMatchesExpected() {
        // GIVEN: Valor conhecido
        String input = "hello";

        // WHEN: Hash calculado
        String hash = calculator.exposeSha256(input);

        // THEN: Hash conhecido do SHA-256("hello")
        assertThat(hash).isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
    }

    @Test
    void sha256_differentInputsProduceDifferentHashes() {
        // GIVEN: Inputs diferentes
        String hash1 = calculator.exposeSha256("input1");
        String hash2 = calculator.exposeSha256("input2");

        // THEN: Hashes diferentes
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void sortedListToString_sortsAlphabetically() {
        // GIVEN: Lista desordenada
        List<String> list = Arrays.asList("Charlie", "Alpha", "Bravo");

        // WHEN: Conversão para string
        String result = calculator.exposeSortedListToString(list);

        // THEN: Ordenado alfabeticamente, lowercase, separado por vírgula
        assertThat(result).isEqualTo("alpha,bravo,charlie");
    }

    @Test
    void sortedListToString_filtersNullsAndBlanks() {
        // GIVEN: Lista com nulls, blanks e valores válidos
        List<String> list = Arrays.asList("Zulu", null, "  ", "Alpha", "", "Bravo");

        // WHEN: Conversão para string
        String result = calculator.exposeSortedListToString(list);

        // THEN: Apenas valores não-null e não-blank, ordenados
        assertThat(result).isEqualTo("alpha,bravo,zulu");
    }

    @Test
    void sortedListToString_returnsEmptyForNullList() {
        // GIVEN/WHEN: Lista null
        String result = calculator.exposeSortedListToString(null);

        // THEN: Retorna string vazia
        assertThat(result).isEmpty();
    }

    @Test
    void sortedListToString_returnsEmptyForEmptyList() {
        // GIVEN/WHEN: Lista vazia
        String result = calculator.exposeSortedListToString(Collections.emptyList());

        // THEN: Retorna string vazia
        assertThat(result).isEmpty();
    }

    @Test
    void sortedListToString_appliesToLowerCase() {
        // GIVEN: Lista com uppercase
        List<String> list = Arrays.asList("ZULU", "ALPHA", "BRAVO");

        // WHEN: Conversão
        String result = calculator.exposeSortedListToString(list);

        // THEN: Tudo lowercase
        assertThat(result).isEqualTo("alpha,bravo,zulu");
    }

    @Test
    void nullSafe_convertsNullToEmpty() {
        // GIVEN/WHEN: Null input
        String result = calculator.exposeNullSafe(null);

        // THEN: String vazia
        assertThat(result).isEmpty();
    }

    @Test
    void nullSafe_trimsWhitespace() {
        // GIVEN: String com espaços
        String input = "  EBBR  ";

        // WHEN: nullSafe aplicado
        String result = calculator.exposeNullSafe(input);

        // THEN: Trimmed e lowercase
        assertThat(result).isEqualTo("ebbr");
    }

    @Test
    void nullSafe_appliesToLowerCase() {
        // GIVEN: String uppercase
        String input = "AMSTERDAM";

        // WHEN: nullSafe aplicado
        String result = calculator.exposeNullSafe(input);

        // THEN: Lowercase
        assertThat(result).isEqualTo("amsterdam");
    }

    @Test
    void nullSafe_handlesEmptyString() {
        // GIVEN: String vazia
        String input = "";

        // WHEN: nullSafe aplicado
        String result = calculator.exposeNullSafe(input);

        // THEN: String vazia
        assertThat(result).isEmpty();
    }

    @Test
    void calculateHash_isDeterministicForSameInputs() {
        // GIVEN: Mesmos inputs
        String hash1 = calculator.calculateHash("payload1", "user123");
        String hash2 = calculator.calculateHash("payload1", "user123");

        // THEN: Mesmo hash
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void calculateHash_producesDifferentHashesForDifferentUsers() {
        // GIVEN: Mesmo payload, usuários diferentes
        String hash1 = calculator.calculateHash("payload", "user1");
        String hash2 = calculator.calculateHash("payload", "user2");

        // THEN: Hashes diferentes
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void calculateHash_producesDifferentHashesForDifferentPayloads() {
        // GIVEN: Mesmo usuário, payloads diferentes
        String hash1 = calculator.calculateHash("payload1", "user");
        String hash2 = calculator.calculateHash("payload2", "user");

        // THEN: Hashes diferentes
        assertThat(hash1).isNotEqualTo(hash2);
    }

    /**
     * Implementação de teste que expõe métodos protected.
     */
    private static class TestHashCalculator extends AbstractSubscriptionHashCalculator<String> {
        @Override
        public String calculateHash(String request, String userId) {
            return sha256(nullSafe(request) + "|" + nullSafe(userId));
        }

        String exposeSha256(String data) {
            return sha256(data);
        }

        String exposeSortedListToString(List<String> list) {
            return sortedListToString(list);
        }

        String exposeNullSafe(String value) {
            return nullSafe(value);
        }
    }
}
