package com.marcaai.triagemia;

import com.marcaai.encaminhamento.TriagemSolicitadaEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Reage ao evento publicado pelo módulo encaminhamento — mantém os dois módulos desacoplados (sem dependência circular). */
@Component
@RequiredArgsConstructor
public class TriagemIAEventListener {

    private final TriagemIAService triagemIAService;

    @EventListener
    public void aoSolicitarTriagem(TriagemSolicitadaEvent event) {
        triagemIAService.analisar(event.encaminhamentoId());
    }
}
