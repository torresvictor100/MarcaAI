package com.marcaai.relatoriofila;

/** Transforma as sugestões (já decididas pelo motor de regras) em texto corrido para a secretaria. */
public interface RedatorRelatorio {
    String redigir(ContextoRelatorio contexto);
}
