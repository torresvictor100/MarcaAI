package com.marcaai.painel;

import com.marcaai.atendimento.AtendimentoService;
import com.marcaai.encaminhamento.EncaminhamentoService;
import com.marcaai.encaminhamento.TipoEncaminhamento;
import com.marcaai.vagasagendamento.AgendamentoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PainelService {

    private final AtendimentoService atendimentoService;
    private final EncaminhamentoService encaminhamentoService;
    private final AgendamentoService agendamentoService;

    public PainelResumoResponse resumo() {
        LocalDate hoje = LocalDate.now();
        long atendidosHoje = atendimentoService.contarAtendimentosNoPeriodo(hoje.atStartOfDay(), hoje.plusDays(1).atStartOfDay());
        long encaminhamentos = encaminhamentoService.contarTotal();
        long examesSolicitados = encaminhamentoService.contarPorTipo(TipoEncaminhamento.EXAME);
        long consultasAgendadas = agendamentoService.contarAgendamentos();
        long vagasDisponiveis = agendamentoService.contarVagasDisponiveis();
        return new PainelResumoResponse(atendidosHoje, encaminhamentos, examesSolicitados, consultasAgendadas, vagasDisponiveis);
    }

    public Map<String, Long> demandaPorEspecialidade() {
        return encaminhamentoService.demandaPorEspecialidade();
    }
}
