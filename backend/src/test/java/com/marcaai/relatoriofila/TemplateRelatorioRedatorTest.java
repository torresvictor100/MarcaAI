package com.marcaai.relatoriofila;

import com.marcaai.atendimento.ClassificacaoRisco;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateRelatorioRedatorTest {

    private final TemplateRelatorioRedator redator = new TemplateRelatorioRedator();

    @Test
    void semSugestoesDizQueNaoHaPontoDeAtencao() {
        String texto = redator.redigir(new ContextoRelatorio("Cardiologia", 4, 2, 2, 8, List.of()));

        assertThat(texto).contains("Fila de Cardiologia: 4 paciente(s)").contains("Nenhum ponto de atenção");
    }

    @Test
    void listaAsSugestoesComOEncaminhamento() {
        SugestaoFila sugestao = new SugestaoFila(TipoSugestao.SUBIR_NA_FILA, PrioridadeSugestao.ALTA, 30L, "Marta",
                3, ClassificacaoRisco.VERMELHO, "Risco vermelho na posição 3.", null, null);
        SugestaoFila geral = new SugestaoFila(TipoSugestao.FALTA_DE_VAGAS, PrioridadeSugestao.MEDIA, null, null,
                null, null, "Poucas vagas.", null, null);

        String texto = redator.redigir(new ContextoRelatorio("Cardiologia", 4, 3, 1, 1, List.of(sugestao, geral)));

        assertThat(texto).contains("2 ponto(s) de atenção, 1 de prioridade alta")
                .contains("- Encaminhamento #30: Risco vermelho na posição 3.")
                .contains("- Poucas vagas.")
                .contains("não alteram a fila");
    }
}
