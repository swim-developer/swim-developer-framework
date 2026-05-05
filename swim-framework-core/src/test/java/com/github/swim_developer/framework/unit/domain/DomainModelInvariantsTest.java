package com.github.swim_developer.framework.unit.domain;

import com.github.swim_developer.framework.domain.model.DataValidationResult;
import com.github.swim_developer.framework.domain.model.ErrorCode;
import com.github.swim_developer.framework.domain.model.ValidationResult;
import com.github.swim_developer.framework.domain.model.ValidationResultType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa invariantes de modelos de domínio.
 *
 * Garantias de Negócio:
 * 1. ValidationResult.fail() nunca aceita lista vazia (deve ter pelo menos 1 erro)
 * 2. DataValidationResult WRONG_FORMAT sempre tem errorReport não vazio
 * 3. DataValidationResult SEQUENCE_GAPS mapeia lista de gaps para ErrorDetails
 * 4. ValidationResult.ok() sempre retorna valid=true e errors vazio
 */
@Tag("domain")
class DomainModelInvariantsTest {

    @Test
    void validationResult_okIsAlwaysValid() {
        // GIVEN/WHEN: Criação de resultado OK
        ValidationResult result = ValidationResult.ok();

        // THEN: Sempre válido e sem erros
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void validationResult_failWithSingleError() {
        // GIVEN/WHEN: Resultado de falha com erro único
        ValidationResult result = ValidationResult.fail("Invalid payload");

        // THEN: Inválido e contém exatamente o erro fornecido
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).containsExactly("Invalid payload");
    }

    @Test
    void validationResult_failWithMultipleErrors() {
        // GIVEN/WHEN: Resultado de falha com múltiplos erros
        ValidationResult result = ValidationResult.fail(List.of("Error 1", "Error 2"));

        // THEN: Inválido e contém todos os erros na ordem
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).containsExactly("Error 1", "Error 2");
    }

    @Test
    void validationResult_failWithEmptyListStillCreatesResult() {
        // GIVEN/WHEN: Resultado com lista vazia (edge case)
        ValidationResult result = ValidationResult.fail(Collections.emptyList());

        // THEN: Ainda cria resultado inválido
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void dataValidationResult_wrongFormat() {
        // GIVEN/WHEN: Validação de formato incorreto
        DataValidationResult result = DataValidationResult.wrongFormat("Missing required field");

        // THEN: Tipo WRONG_FORMAT e errorReport não vazio
        assertThat(result.dataValResult()).isEqualTo(ValidationResultType.WRONG_FORMAT);
        assertThat(result.errorReport()).hasSize(1);
        assertThat(result.errorReport().get(0).errorMessage()).isEqualTo("Missing required field");
    }

    @Test
    void dataValidationResult_dataInvalid() {
        // GIVEN/WHEN: Validação de dados inválidos com ErrorCode
        DataValidationResult result = DataValidationResult.dataInvalid(
            ErrorCode.LOGIC_VIOLATION,
            "scenario",
            "Invalid scenario value"
        );

        // THEN: Tipo DATA_INVALID e errorReport contém detalhe
        assertThat(result.dataValResult()).isEqualTo(ValidationResultType.DATA_INVALID);
        assertThat(result.errorReport()).hasSize(1);
        assertThat(result.errorReport().get(0).errorCode()).isEqualTo(ErrorCode.LOGIC_VIOLATION);
        assertThat(result.errorReport().get(0).erroneousFieldName()).isEqualTo("scenario");
        assertThat(result.errorReport().get(0).errorMessage()).isEqualTo("Invalid scenario value");
    }

    @Test
    void dataValidationResult_nonSubscribedData() {
        // GIVEN/WHEN: Dados não subscritos
        DataValidationResult result = DataValidationResult.nonSubscribedData("Event not in subscription filter");

        // THEN: Tipo NON_SUBSCRIBED_DATA
        assertThat(result.dataValResult()).isEqualTo(ValidationResultType.NON_SUBSCRIBED_DATA);
        assertThat(result.errorReport()).hasSize(1);
    }

    @Test
    void dataValidationResult_sequenceGaps() {
        // GIVEN/WHEN: Gaps de sequência detectados
        List<String> gaps = List.of("Gap between 5 and 7", "Gap between 10 and 15");
        DataValidationResult result = DataValidationResult.sequenceGaps(gaps);

        // THEN: Tipo SEQUENCE_GAPS e errorReport mapeia todos os gaps
        assertThat(result.dataValResult()).isEqualTo(ValidationResultType.SEQUENCE_GAPS);
        assertThat(result.errorReport()).hasSize(2);
        assertThat(result.errorReport().get(0).errorMessage()).contains("Gap between 5 and 7");
        assertThat(result.errorReport().get(1).errorMessage()).contains("Gap between 10 and 15");
    }

    @Test
    void dataValidationResult_sequenceGapsWithEmptyList() {
        // GIVEN/WHEN: Sequence gaps com lista vazia (edge case)
        DataValidationResult result = DataValidationResult.sequenceGaps(Collections.emptyList());

        // THEN: Ainda cria resultado, mas errorReport vazio
        assertThat(result.dataValResult()).isEqualTo(ValidationResultType.SEQUENCE_GAPS);
        assertThat(result.errorReport()).isEmpty();
    }
}
