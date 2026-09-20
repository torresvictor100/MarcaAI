package com.marcaai.triagemia;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateJustificativaGeneratorTest {

    private final TemplateJustificativaGenerator generator = new TemplateJustificativaGenerator();

    @Test
    void casoBloqueadoDescreveOsMotivosBloqueantes() {
        ContextoJustificativa contexto = new ContextoJustificativa(
                1L, "Cardiologia", 0.0, Map.of(),
                List.of(new Irregularidade("CID incompatível", Severidade.BLOQUEANTE)),
                true);

        String texto = generator.gerar(contexto);

        assertThat(texto).contains("bloqueado para revisão humana");
        assertThat(texto).contains("CID incompatível");
    }

    @Test
    void casoNormalSemIrregularidadesDescreveOsFatores() {
        ContextoJustificativa contexto = new ContextoJustificativa(
                2L, "Cardiologia", 75.0, Map.of("pesoRisco", 50.0, "bonusUrgencia", 0.0),
                List.of(), false);

        String texto = generator.gerar(contexto);

        assertThat(texto).contains("Score de prioridade 75");
        assertThat(texto).contains("Cardiologia");
        assertThat(texto).doesNotContain("Alertas");
    }

    @Test
    void casoComAlertaNaoBloqueanteApareceComoAlerta() {
        ContextoJustificativa contexto = new ContextoJustificativa(
                3L, "Cardiologia", 50.0, Map.of("pesoRisco", 50.0),
                List.of(new Irregularidade("Encaminhamento duplicado", Severidade.ALERTA)),
                false);

        String texto = generator.gerar(contexto);

        assertThat(texto).contains("Alertas (não bloqueantes)");
        assertThat(texto).contains("Encaminhamento duplicado");
    }
}
