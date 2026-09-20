package com.marcaai.resultadoexame;

import com.marcaai.config.ConflitoException;
import com.marcaai.encaminhamento.Encaminhamento;
import com.marcaai.encaminhamento.EncaminhamentoService;
import com.marcaai.encaminhamento.StatusEncaminhamento;
import com.marcaai.fila.FilaService;
import com.marcaai.config.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ResultadoExameService {

    private final ResultadoExameRepository resultadoExameRepository;
    private final EncaminhamentoService encaminhamentoService;
    private final FilaService filaService;

    /**
     * Registra o desfecho de um encaminhamento já agendado. Não sobrescreve: um resultado registrado é
     * definitivo, e corrigir exigiria trilha de auditoria própria (dado de saúde, LGPD).
     */
    @Transactional
    public ResultadoExame registrar(Long encaminhamentoId, ResultadoExameRequest request) {
        Encaminhamento encaminhamento = encaminhamentoService.buscarPorId(encaminhamentoId);
        if (resultadoExameRepository.findByEncaminhamentoId(encaminhamentoId).isPresent()) {
            throw new ConflitoException("Já existe resultado registrado para o encaminhamento " + encaminhamentoId);
        }
        if (encaminhamento.getStatus() != StatusEncaminhamento.AGENDADO) {
            throw new ConflitoException("Só é possível registrar resultado de encaminhamento agendado (status atual: "
                    + encaminhamento.getStatus() + ")");
        }

        ResultadoExame salvo = resultadoExameRepository.save(ResultadoExame.builder()
                .encaminhamentoId(encaminhamentoId)
                .referenciaArquivo(request.referenciaArquivo())
                .dataResultado(request.dataResultado())
                .observacoes(request.observacoes())
                .build());

        encaminhamentoService.atualizarStatus(encaminhamentoId, StatusEncaminhamento.REALIZADO);
        // A fila vale até o atendimento: quem foi atendido sai dela.
        filaService.retirarDaFila(encaminhamentoId);
        return salvo;
    }

    public ResultadoExame buscarPorEncaminhamento(Long encaminhamentoId) {
        return resultadoExameRepository.findByEncaminhamentoId(encaminhamentoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Ainda não há resultado registrado para o encaminhamento " + encaminhamentoId));
    }
}
