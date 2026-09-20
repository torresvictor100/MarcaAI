package com.marcaai.relatoriofila;

public enum TipoSugestao {
    /** Risco alto aguardando atrás de pacientes de risco menor. */
    SUBIR_NA_FILA,
    /** Agendado com risco alto para daqui a muitos dias, havendo vaga livre antes. */
    ANTECIPAR,
    /** Aguardando agendamento há muito tempo desde o atendimento. */
    ESPERA_LONGA,
    /** Marcado como urgente pelo médico e ainda sem agendamento. */
    URGENTE_SEM_VAGA,
    /** Mais pacientes aguardando do que vagas livres na especialidade. */
    FALTA_DE_VAGAS
}
