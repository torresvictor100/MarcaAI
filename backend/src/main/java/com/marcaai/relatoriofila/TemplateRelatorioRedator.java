package com.marcaai.relatoriofila;

import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/** Texto determinístico usado quando a IA não está disponível — o relatório nunca deixa de ser gerado. */
@Component
public class TemplateRelatorioRedator implements RedatorRelatorio {

    @Override
    public String redigir(ContextoRelatorio contexto) {
        String cabecalho = "Fila de %s: %d paciente(s) — %d aguardando agendamento, %d agendado(s); %d vaga(s) livre(s)."
                .formatted(contexto.especialidadeOuExame(), contexto.totalNaFila(), contexto.aguardandoAgendamento(),
                        contexto.agendados(), contexto.vagasLivres());
        if (contexto.sugestoes().isEmpty()) {
            return cabecalho + "\n\nNenhum ponto de atenção encontrado pelas regras de priorização neste momento.";
        }
        long altas = contexto.sugestoes().stream().filter(s -> s.prioridade() == PrioridadeSugestao.ALTA).count();
        String itens = contexto.sugestoes().stream()
                .map(s -> "- " + (s.encaminhamentoId() == null ? "" : "Encaminhamento #" + s.encaminhamentoId() + ": ")
                        + s.motivo())
                .collect(Collectors.joining("\n"));
        return cabecalho + "\n\n%d ponto(s) de atenção, %d de prioridade alta:\n%s\n\nAs sugestões não alteram a fila: "
                .formatted(contexto.sugestoes().size(), altas, itens)
                + "cabe à secretaria decidir pelo ajuste manual ou pela antecipação.";
    }
}
