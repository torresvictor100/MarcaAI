package com.marcaai.fila;

import com.marcaai.atendimento.Atendimento;
import com.marcaai.atendimento.AtendimentoService;
import com.marcaai.encaminhamento.Encaminhamento;
import com.marcaai.encaminhamento.EncaminhamentoService;
import com.marcaai.encaminhamento.StatusEncaminhamento;
import com.marcaai.shared.Paciente;
import com.marcaai.shared.PacienteRepository;
import com.marcaai.vagasagendamento.AgendamentoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Acrescenta aos itens da fila o que a secretaria precisa para triar de olho: nome do paciente, situação
 * do encaminhamento (na fila / agendado) e classificação de risco do atendimento de origem. Busca pelos
 * serviços dos módulos donos de cada dado, nunca direto no banco deles.
 */
@Service
@RequiredArgsConstructor
public class FilaDetalhesService {

    private final EncaminhamentoService encaminhamentoService;
    private final AtendimentoService atendimentoService;
    private final PacienteRepository pacienteRepository;
    private final AgendamentoService agendamentoService;

    public List<FilaItemResponse> detalhar(List<FilaItemResponse> itens) {
        return itens.stream().map(this::detalhar).toList();
    }

    private FilaItemResponse detalhar(FilaItemResponse item) {
        Encaminhamento encaminhamento = encaminhamentoService.buscarPorId(item.encaminhamentoId());
        Atendimento atendimento = atendimentoService.buscarPorId(encaminhamento.getAtendimentoId());
        String pacienteNome = pacienteRepository.findById(atendimento.getPacienteId()).map(Paciente::getNome).orElse(null);
        LocalDateTime presencaConfirmadaEm = encaminhamento.getStatus() == StatusEncaminhamento.AGENDADO
                ? agendamentoService.presencaConfirmadaEm(encaminhamento.getId()).orElse(null)
                : null;
        return item.comDetalhes(pacienteNome, encaminhamento.getStatus(), atendimento.getClassificacaoRisco(), presencaConfirmadaEm);
    }
}
