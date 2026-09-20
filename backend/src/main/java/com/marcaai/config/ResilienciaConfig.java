package com.marcaai.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.EntryRemovedEvent;
import io.github.resilience4j.core.registry.EntryReplacedEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registra no log toda mudança de estado dos Circuit Breakers (ADR-010), para saber quando a IA saiu e voltou. */
@Configuration
public class ResilienciaConfig {

    private static final Logger log = LoggerFactory.getLogger("marcaai.resiliencia");

    @Bean
    public RegistryEventConsumer<CircuitBreaker> logDeTransicaoDoCircuito() {
        return new RegistryEventConsumer<>() {
            @Override
            public void onEntryAddedEvent(EntryAddedEvent<CircuitBreaker> evento) {
                CircuitBreaker circuito = evento.getAddedEntry();
                circuito.getEventPublisher().onStateTransition(transicao -> log.warn(
                        "evento=CIRCUITO_{} circuito={} transicao={}", transicao.getStateTransition().getToState(),
                        circuito.getName(), transicao.getStateTransition()));
            }

            @Override
            public void onEntryRemovedEvent(EntryRemovedEvent<CircuitBreaker> evento) {
                // nada a fazer
            }

            @Override
            public void onEntryReplacedEvent(EntryReplacedEvent<CircuitBreaker> evento) {
                // nada a fazer
            }
        };
    }
}
