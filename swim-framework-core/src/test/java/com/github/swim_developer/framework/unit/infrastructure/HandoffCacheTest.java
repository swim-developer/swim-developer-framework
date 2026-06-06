package com.github.swim_developer.framework.unit.infrastructure;

import com.github.swim_developer.framework.infrastructure.out.cache.HandoffCache;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa comportamento crítico de HandoffCache.
 *
 * Garantias de Negócio:
 * 1. Type safety: get() retorna empty se tipo não bate
 * 2. getAndRemove() remove entrada após leitura
 * 3. get() NÃO remove entrada (read-only)
 * 4. Cache aceita qualquer Object, mas retorno é type-safe
 */
@Tag("infrastructure")
class HandoffCacheTest {

    private HandoffCache cache;

    @BeforeEach
    void setUp() {
        cache = new HandoffCache(new SimpleMeterRegistry());
    }

    @Test
    void put_andGet_withCorrectType() {
        // GIVEN: Valor String armazenado
        cache.put("key1", "value1");

        // WHEN: get() com tipo correto
        var result = cache.get("key1", String.class);

        // THEN: Retorna valor
        assertThat(result).contains("value1");
    }

    @Test
    void get_returnsEmptyWhenTypeMismatch() {
        // GIVEN: Valor String armazenado
        cache.put("key1", "value1");

        // WHEN: get() com tipo errado (Integer)
        var result = cache.get("key1", Integer.class);

        // THEN: Retorna empty (type safety)
        assertThat(result).isEmpty();
    }

    @Test
    void get_doesNotRemoveEntry() {
        // GIVEN: Valor armazenado
        cache.put("key1", "value1");

        // WHEN: get() chamado
        cache.get("key1", String.class);

        // THEN: Valor ainda está no cache
        assertThat(cache.get("key1", String.class)).contains("value1");
    }

    @Test
    void getAndRemove_removesEntryAfterRead() {
        // GIVEN: Valor armazenado
        cache.put("key1", "value1");

        // WHEN: getAndRemove() chamado
        var firstRead = cache.getAndRemove("key1", String.class);

        // THEN: Primeira leitura retorna valor
        assertThat(firstRead).contains("value1");

        // AND: Segunda leitura retorna empty (removido)
        assertThat(cache.get("key1", String.class)).isEmpty();
    }

    @Test
    void getAndRemove_returnsEmptyWhenTypeMismatch_andPreservesEntry() {
        // GIVEN: Valor String armazenado
        cache.put("key1", "value1");

        // WHEN: getAndRemove() com tipo errado
        var result = cache.getAndRemove("key1", Integer.class);

        // THEN: Retorna empty
        assertThat(result).isEmpty();

        // AND: Valor é PRESERVADO (não remove quando tipo não bate)
        assertThat(cache.get("key1", String.class)).contains("value1");
    }

    @Test
    void remove_deletesEntry() {
        // GIVEN: Valor armazenado
        cache.put("key1", "value1");

        // WHEN: remove() chamado
        cache.remove("key1");

        // THEN: Valor não existe mais
        assertThat(cache.get("key1", String.class)).isEmpty();
    }

    @Test
    void get_returnsEmptyForNonExistentKey() {
        // GIVEN: Cache vazio
        // WHEN: get() para chave inexistente
        var result = cache.get("non-existent", String.class);

        // THEN: Retorna empty
        assertThat(result).isEmpty();
    }

    @Test
    void getAndRemove_returnsEmptyForNonExistentKey() {
        // GIVEN: Cache vazio
        // WHEN: getAndRemove() para chave inexistente
        var result = cache.getAndRemove("non-existent", String.class);

        // THEN: Retorna empty
        assertThat(result).isEmpty();
    }

    @Test
    void remove_doesNotFailForNonExistentKey() {
        // GIVEN: Cache vazio
        // WHEN: remove() para chave inexistente
        cache.remove("non-existent");

        // THEN: Cache continua vazio — operação silenciosa sem efeito
        assertThat(cache.get("non-existent", String.class)).isEmpty();
    }

    @Test
    void put_supportsComplexTypes() {
        // GIVEN: Objeto complexo
        record TestRecord(String id, int value) {}
        TestRecord original = new TestRecord("id1", 42);

        // WHEN: Armazenado e recuperado
        cache.put("complex", original);
        var result = cache.get("complex", TestRecord.class);

        // THEN: Retorna mesmo objeto
        assertThat(result).contains(original);
    }
}
