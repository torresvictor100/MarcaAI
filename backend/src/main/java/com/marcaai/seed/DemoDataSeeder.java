package com.marcaai.seed;

import com.marcaai.atendimento.AtendimentoService;
import com.marcaai.auth.Papel;
import com.marcaai.config.MarcaAiPrincipal;
import com.marcaai.encaminhamento.DocumentoRequest;
import com.marcaai.encaminhamento.Encaminhamento;
import com.marcaai.encaminhamento.EncaminhamentoService;
import com.marcaai.encaminhamento.StatusEncaminhamento;
import com.marcaai.fila.FilaItemResponse;
import com.marcaai.fila.FilaService;
import com.marcaai.fila.OverrideRequest;
import com.marcaai.resultadoexame.ResultadoExameRequest;
import com.marcaai.resultadoexame.ResultadoExameService;
import com.marcaai.vagasagendamento.AgendamentoRequest;
import com.marcaai.vagasagendamento.AgendamentoService;
import com.marcaai.vagasagendamento.StatusAgendamento;
import com.marcaai.vagasagendamento.VagaHorario;
import com.marcaai.vagasagendamento.VagaMarcadaResponse;
import com.marcaai.config.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Automatiza, na subida da aplicação, exatamente o que a demonstração manual do README fazia via curl:
 * reanexa um documento já exigido nos encaminhamentos de massa (V015) que ainda estão {@code EM_ANALISE}
 * para disparar o {@code TriagemIAService} de verdade (nenhum score/irregularidade é calculado aqui —
 * ver ADR-004), depois confirma agendamento para o tanto que couber nas vagas de cada especialidade e
 * registra resultado de exame para alguns casos de Hemograma Completo já agendados.
 *
 * Depois de agendar, aplica um ajuste manual (override) de exemplo em uma das especialidades ainda com
 * fila, pra a tela da secretaria não nascer com a coluna de ajuste manual sempre vazia.
 *
 * Também confirma a presença de metade dos pacientes agendados (um sim, um não), como o próprio paciente
 * faria em {@code POST /agendamentos/{id}/confirmar-presenca}, para as telas mostrarem os dois estados.
 *
 * <p>Idempotente por construção: cada passo consulta o estado atual antes de agir (só reprocessa
 * encaminhamentos ainda {@code EM_ANALISE}; só agenda até a cota total de agendamentos da especialidade
 * ser atingida, contando os que já existem; só registra resultado em quem ainda não tem um; só ajusta
 * manualmente se a especialidade ainda não tiver nenhum item ajustado), então rodar de novo num banco
 * já semeado não duplica nada.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "marcaai.seed.demo-automatico", havingValue = "true", matchIfMissing = true)
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final String LOGIN_SISTEMA = "sistema-seed";
    private static final String TIPO_DOCUMENTO_RETRIGGER = "GUIA_ENCAMINHAMENTO";

    private final EncaminhamentoService encaminhamentoService;
    private final FilaService filaService;
    private final AgendamentoService agendamentoService;
    private final ResultadoExameService resultadoExameService;
    private final AtendimentoService atendimentoService;

    /** Prefixo das notas dos atendimentos da massa da V015 — a única que o seeder agenda e ajusta sozinho. */
    static final String PREFIXO_MASSA_V015 = "Atendimento de demonstração (massa de dados)";

    @Override
    public void run(ApplicationArguments args) {
        autenticarComoSistema();
        try {
            int reprocessados = reprocessarTriagemPendente();
            if (reprocessados > 0) {
                log.info("DemoDataSeeder: triagem real disparada para {} encaminhamento(s) de demonstração.", reprocessados);
            }

            agendarComVagasLimitadas("Ortopedia", 10);
            agendarComVagasLimitadas("Dermatologia", 10);
            List<Long> hemogramaAgendados = agendarComVagasLimitadas("Hemograma Completo", 10);
            agendarComVagasLimitadas("Cardiologia", 6);

            registrarResultadosDeExemplo(hemogramaAgendados);

            for (String especialidade : List.of("Ortopedia", "Dermatologia", "Hemograma Completo", "Cardiologia")) {
                confirmarPresencasDeExemplo(especialidade);
            }

            // Cardiologia é a especialidade usada pelos 3 cenários curados do Postman (e pelo teste de
            // integração) — evitada aqui de propósito pra não colidir com um override aplicado ao vivo
            // durante essas demonstrações.
            aplicarAjusteManualDeExemplo("Dermatologia",
                    "Paciente internado com piora do quadro; secretaria priorizou a pedido da equipe médica.");
            aplicarAjusteManualDeExemplo("Ortopedia",
                    "Paciente idoso com mobilidade bastante reduzida; priorizado por vulnerabilidade.");
        } catch (Exception ex) {
            log.warn("DemoDataSeeder: falha ao popular dados de demonstração automaticamente — "
                    + "o sistema continua funcional, só sem essa massa extra já processada.", ex);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void autenticarComoSistema() {
        MarcaAiPrincipal principal = new MarcaAiPrincipal(0L, LOGIN_SISTEMA, Papel.ADMIN);
        var autenticacao = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(autenticacao);
    }

    private int reprocessarTriagemPendente() {
        List<Encaminhamento> pendentes = encaminhamentoService.listarPorStatus(StatusEncaminhamento.EM_ANALISE);
        int sucesso = 0;
        for (Encaminhamento encaminhamento : pendentes) {
            try {
                encaminhamentoService.adicionarDocumento(encaminhamento.getId(), new DocumentoRequest(
                        TIPO_DOCUMENTO_RETRIGGER, "demo/seed-automatico.pdf", LocalDate.now(), LocalDate.now().plusYears(1)));
                sucesso++;
            } catch (Exception ex) {
                log.warn("DemoDataSeeder: falha ao rodar a triagem automática do encaminhamento {}", encaminhamento.getId(), ex);
            }
        }
        return sucesso;
    }

    /**
     * Agenda, na ordem de prioridade da fila, até a especialidade somar {@code cotaTotal} agendamentos (contando os
     * que já existem, inclusive cancelados). A cota é total, não por execução: sem isso, cada nova subida do backend
     * agendava mais {@code cotaTotal} e consumia as vagas livres que a demonstração precisa manter; e contar os
     * cancelados evita reagendar sozinho quem a secretaria cancelou.
     * As cotas das chamadas em run() estão espelhadas em V017__vagas_demo_8_por_especialidade.sql — mudar uma
     * exige mudar a outra.
     */
    private List<Long> agendarComVagasLimitadas(String especialidadeOuExame, int cotaTotal) {
        long jaAgendados = agendamentoService.contarAgendamentosPorEspecialidade(especialidadeOuExame);
        long maxNovosAgendamentos = cotaTotal - jaAgendados;
        if (maxNovosAgendamentos <= 0) {
            return List.of();
        }
        List<FilaItemResponse> fila = filaService.listarPorEspecialidade(especialidadeOuExame);
        List<VagaHorario> vagasLivres = new ArrayList<>(agendamentoService.listarDisponiveisPorEspecialidade(especialidadeOuExame));
        List<Long> agendados = new ArrayList<>();

        for (FilaItemResponse itemDaFila : fila) {
            if (agendados.size() >= maxNovosAgendamentos || vagasLivres.isEmpty()) {
                break;
            }
            Long encaminhamentoId = itemDaFila.encaminhamentoId();
            // Só a massa da V015: os cenários de risco (V022) e os casos do Postman ficam aguardando de propósito.
            if (!ehMassaV015(encaminhamentoId) || jaTemAgendamento(encaminhamentoId)) {
                continue;
            }
            VagaHorario vaga = vagasLivres.remove(0);
            try {
                agendamentoService.criar(new AgendamentoRequest(encaminhamentoId, vaga.getId()));
                agendados.add(encaminhamentoId);
            } catch (Exception ex) {
                log.warn("DemoDataSeeder: falha ao agendar automaticamente o encaminhamento {}", encaminhamentoId, ex);
            }
        }
        if (!agendados.isEmpty()) {
            log.info("DemoDataSeeder: {} agendamento(s) automático(s) confirmado(s) para {}.", agendados.size(), especialidadeOuExame);
        }
        return agendados;
    }

    private boolean ehMassaV015(Long encaminhamentoId) {
        Long atendimentoId = encaminhamentoService.buscarPorId(encaminhamentoId).getAtendimentoId();
        String notas = atendimentoService.buscarPorId(atendimentoId).getNotas();
        return notas != null && notas.startsWith(PREFIXO_MASSA_V015);
    }

    private boolean jaTemAgendamento(Long encaminhamentoId) {
        try {
            agendamentoService.buscarPorEncaminhamento(encaminhamentoId);
            return true;
        } catch (RecursoNaoEncontradoException ex) {
            return false;
        }
    }

    /** Marca alguns dos exames de Hemograma Completo já agendados como realizados, pra mostrar o estado final também. */
    private void registrarResultadosDeExemplo(List<Long> hemogramaAgendados) {
        int limite = Math.min(3, hemogramaAgendados.size());
        for (int i = 0; i < limite; i++) {
            Long encaminhamentoId = hemogramaAgendados.get(i);
            if (jaTemResultado(encaminhamentoId)) {
                continue;
            }
            try {
                resultadoExameService.registrar(encaminhamentoId, new ResultadoExameRequest(
                        "demo/resultado-" + encaminhamentoId + ".pdf",
                        LocalDate.now().minusDays(1),
                        "Resultado dentro da normalidade (dado de demonstração gerado automaticamente)."));
            } catch (Exception ex) {
                log.warn("DemoDataSeeder: falha ao registrar resultado de exame automático do encaminhamento {}", encaminhamentoId, ex);
            }
        }
    }

    /**
     * Confirma a presença de um sim, um não dos agendamentos futuros da massa V015 ainda por atender, na ordem
     * da data. Idempotente: se algum da especialidade já tem presença confirmada, não faz nada (respeita quem
     * não confirmou e remarcações feitas depois, que zeram a confirmação).
     */
    private void confirmarPresencasDeExemplo(String especialidadeOuExame) {
        List<VagaMarcadaResponse> marcadas = agendamentoService
                .listarMarcadasPorEspecialidade(especialidadeOuExame, LocalDate.now(), null).stream()
                .filter(m -> m.agendamentoId() != null && m.statusAgendamento() == StatusAgendamento.CONFIRMADO)
                .filter(m -> ehMassaV015(m.encaminhamentoId()))
                .filter(m -> encaminhamentoService.buscarPorId(m.encaminhamentoId()).getStatus() == StatusEncaminhamento.AGENDADO)
                .toList();
        if (marcadas.stream().anyMatch(m -> m.presencaConfirmadaEm() != null)) {
            return;
        }
        int confirmadas = 0;
        for (int i = 0; i < marcadas.size(); i += 2) {
            try {
                agendamentoService.confirmarPresenca(marcadas.get(i).agendamentoId());
                confirmadas++;
            } catch (Exception ex) {
                log.warn("DemoDataSeeder: falha ao confirmar presença de exemplo no agendamento {}", marcadas.get(i).agendamentoId(), ex);
            }
        }
        if (confirmadas > 0) {
            log.info("DemoDataSeeder: presença confirmada em {} de {} agendamento(s) de {}.", confirmadas, marcadas.size(), especialidadeOuExame);
        }
    }

    /**
     * Aplica um ajuste manual de exemplo (a mesma ação que a secretaria faria em {@code PATCH /fila/{itemId}/override})
     * num item que ainda está na fila e não está na 1ª posição, pra o efeito ficar visível na demonstração.
     * Idempotente: se a especialidade já tem algum item ajustado, não faz nada de novo.
     */
    private void aplicarAjusteManualDeExemplo(String especialidadeOuExame, String justificativa) {
        List<FilaItemResponse> fila = filaService.listarPorEspecialidade(especialidadeOuExame);
        boolean jaTemAjuste = fila.stream().anyMatch(FilaItemResponse::overrideManual);
        if (jaTemAjuste) {
            return;
        }
        fila.stream()
                .filter(item -> item.posicao() > 1)
                .filter(item -> ehMassaV015(item.encaminhamentoId()))
                .findFirst()
                .ifPresent(item -> {
                    try {
                        filaService.aplicarOverride(item.itemId(), new OverrideRequest(1, justificativa));
                        log.info("DemoDataSeeder: ajuste manual de exemplo aplicado no item {} ({}).", item.itemId(), especialidadeOuExame);
                    } catch (Exception ex) {
                        log.warn("DemoDataSeeder: falha ao aplicar ajuste manual de exemplo em {}", especialidadeOuExame, ex);
                    }
                });
    }

    private boolean jaTemResultado(Long encaminhamentoId) {
        try {
            resultadoExameService.buscarPorEncaminhamento(encaminhamentoId);
            return true;
        } catch (RecursoNaoEncontradoException ex) {
            return false;
        }
    }
}
