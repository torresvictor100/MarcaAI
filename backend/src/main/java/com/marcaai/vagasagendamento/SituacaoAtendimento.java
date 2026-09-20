package com.marcaai.vagasagendamento;

import com.marcaai.encaminhamento.StatusEncaminhamento;

/** Filtro da agenda do especialista: o que ele ainda vai atender e o que já atendeu. */
public enum SituacaoAtendimento {
    A_ATENDER(StatusEncaminhamento.AGENDADO),
    ATENDIDO(StatusEncaminhamento.REALIZADO);

    private final StatusEncaminhamento status;

    SituacaoAtendimento(StatusEncaminhamento status) {
        this.status = status;
    }

    public boolean inclui(StatusEncaminhamento statusEncaminhamento) {
        return status == statusEncaminhamento;
    }
}
