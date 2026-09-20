package com.marcaai.resultadoexame;

import com.marcaai.config.ConflitoException;
import com.marcaai.config.RegraDeNegocioException;
import com.marcaai.encaminhamento.Encaminhamento;
import com.marcaai.encaminhamento.EncaminhamentoService;
import com.marcaai.encaminhamento.StatusEncaminhamento;
import com.marcaai.fila.FilaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ResultadoExameServiceTest {

    private ResultadoExameRepository resultadoExameRepository;
    private EncaminhamentoService encaminhamentoService;
    private FilaService filaService;
    private ResultadoExameService service;

    private final ResultadoExameRequest request = new ResultadoExameRequest("laudo.pdf", LocalDate.of(2026, 9, 24), "ok");

    @BeforeEach
    void setUp() {
        resultadoExameRepository = mock(ResultadoExameRepository.class);
        encaminhamentoService = mock(EncaminhamentoService.class);
        filaService = mock(FilaService.class);
        service = new ResultadoExameService(resultadoExameRepository, encaminhamentoService, filaService);
        when(resultadoExameRepository.findByEncaminhamentoId(any())).thenReturn(Optional.empty());
        when(resultadoExameRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private void encaminhamentoCom(StatusEncaminhamento status) {
        when(encaminhamentoService.buscarPorId(7L)).thenReturn(Encaminhamento.builder().id(7L).status(status).build());
    }

    @Test
    void registraEmEncaminhamentoAgendadoEMarcaComoRealizado() {
        encaminhamentoCom(StatusEncaminhamento.AGENDADO);

        ResultadoExame salvo = service.registrar(7L, request);

        assertThat(salvo.getEncaminhamentoId()).isEqualTo(7L);
        assertThat(salvo.getReferenciaArquivo()).isEqualTo("laudo.pdf");
        verify(encaminhamentoService).atualizarStatus(7L, StatusEncaminhamento.REALIZADO);
        verify(filaService).retirarDaFila(7L);
    }

    @Test
    void recusaEncaminhamentoQueNaoEstaAgendado() {
        encaminhamentoCom(StatusEncaminhamento.NA_FILA);

        assertThatThrownBy(() -> service.registrar(7L, request)).isInstanceOf(ConflitoException.class);
        verify(resultadoExameRepository, never()).save(any());
    }

    @Test
    void naoSobrescreveResultadoJaRegistrado() {
        encaminhamentoCom(StatusEncaminhamento.AGENDADO);
        when(resultadoExameRepository.findByEncaminhamentoId(7L))
                .thenReturn(Optional.of(ResultadoExame.builder().id(1L).encaminhamentoId(7L).build()));

        assertThatThrownBy(() -> service.registrar(7L, request)).isInstanceOf(ConflitoException.class);
        verify(resultadoExameRepository, never()).save(any());
    }
}
