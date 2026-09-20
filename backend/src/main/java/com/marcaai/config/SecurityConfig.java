package com.marcaai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.header.writers.CrossOriginEmbedderPolicyHeaderWriter;
import org.springframework.security.web.header.writers.CrossOriginOpenerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.CrossOriginResourcePolicyHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Clock;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final NaoAutenticadoEntryPoint naoAutenticadoEntryPoint;
    private final AcessoNegadoHandler acessoNegadoHandler;
    private final AcessoMetricas acessoMetricas;
    private final ObjectMapper objectMapper;

    @Value("${marcaai.cors.allowed-origins:http://localhost:5173}")
    private List<String> corsAllowedOrigins;

    @Value("${marcaai.rate-limit.habilitado:true}")
    private boolean rateLimitHabilitado;

    @Value("${marcaai.rate-limit.autenticado-por-minuto:300}")
    private int rateLimitAutenticado;

    @Value("${marcaai.rate-limit.anonimo-por-minuto:60}")
    private int rateLimitAnonimo;

    /** Custo do BCrypt (2^12 rodadas), o mínimo do checklist de produção. Hashes antigos (custo 10) continuam válidos. */
    static final int FORCA_BCRYPT = 12;

    /**
     * Content-Security-Policy (OWASP A05): só recursos da própria origem. {@code data:} nas imagens e estilo
     * inline são exigidos pelo Swagger UI; a API em si só devolve JSON. {@code frame-ancestors 'none'} impede
     * que a página seja embutida em outro site (clickjacking).
     */
    static final String CONTENT_SECURITY_POLICY = "default-src 'self'; img-src 'self' data:; "
            + "style-src 'self' 'unsafe-inline'; script-src 'self'; object-src 'none'; base-uri 'self'; "
            + "form-action 'self'; frame-ancestors 'none'";

    private static final String SECRETARIA = "SECRETARIA";
    private static final String ADMIN = "ADMIN";
    private static final String MEDICO_UBS = "MEDICO_UBS";

    private static final String[] ROTAS_PUBLICAS = {
            "/auth/login",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/actuator/health",
            "/actuator/info"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(ROTAS_PUBLICAS).permitAll()
                        // Papel por rota (ADR-011): quem não tem o papel recebe 403 aqui, antes de o corpo ser lido
                        // e validado — sem isso, um corpo inválido dava 400 e mostrava o formato esperado a quem
                        // nem pode usar a rota. O @PreAuthorize dos controllers continua como segunda camada, e o
                        // acesso por vínculo com o dado (ADR-007) continua nos controllers/ControleAcessoService.
                        .requestMatchers(HttpMethod.POST, "/atendimentos").hasRole(MEDICO_UBS)
                        .requestMatchers(HttpMethod.POST, "/encaminhamentos", "/encaminhamentos/*/documentos").hasRole(MEDICO_UBS)
                        .requestMatchers(HttpMethod.POST, "/encaminhamentos/*/resultado-exame").hasRole("ESPECIALISTA")
                        .requestMatchers(HttpMethod.POST, "/agendamentos/*/confirmar-presenca").hasRole("PACIENTE")
                        .requestMatchers(HttpMethod.POST, "/agendamentos", "/agendamentos/*/cancelar", "/agendamentos/*/remarcar",
                                "/agendamentos/*/antecipar").hasAnyRole(SECRETARIA, ADMIN)
                        .requestMatchers(HttpMethod.POST, "/vagas/lote").hasAnyRole(SECRETARIA, ADMIN)
                        .requestMatchers(HttpMethod.PATCH, "/fila/*/override").hasAnyRole(SECRETARIA, ADMIN)
                        .requestMatchers("/fila/todas", "/fila/relatorio-ia", "/vagas/marcadas", "/painel/**", "/profissionais")
                        .hasAnyRole(SECRETARIA, ADMIN)
                        .requestMatchers(HttpMethod.GET, "/pacientes", "/unidades").hasAnyRole(MEDICO_UBS, SECRETARIA, ADMIN)
                        .requestMatchers("/agenda/**").hasRole("ESPECIALISTA")
                        // Métricas e estado dos circuitos (ADR-011): só o admin; o Prometheus usa credencial própria.
                        .requestMatchers("/actuator/prometheus").access(acessoMetricas)
                        .requestMatchers("/actuator/**").hasRole(ADMIN)
                        .anyRequest().authenticated())
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        // Só vale sob HTTPS (o Spring nem envia em HTTP); fica pronto para quando houver TLS.
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000))
                        .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy",
                                "camera=(), microphone=(), geolocation=(), payment=()"))
                        // Isolamento entre origens (avisos do OWASP ZAP): nenhum site de fora embute ou lê as
                        // respostas no modo no-cors. O front (outra origem) usa CORS, que não é afetado.
                        .crossOriginResourcePolicy(corp -> corp.policy(
                                CrossOriginResourcePolicyHeaderWriter.CrossOriginResourcePolicy.SAME_ORIGIN))
                        .crossOriginOpenerPolicy(coop -> coop.policy(
                                CrossOriginOpenerPolicyHeaderWriter.CrossOriginOpenerPolicy.SAME_ORIGIN))
                        .crossOriginEmbedderPolicy(coep -> coep.policy(
                                CrossOriginEmbedderPolicyHeaderWriter.CrossOriginEmbedderPolicy.REQUIRE_CORP)))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(naoAutenticadoEntryPoint)
                        .accessDeniedHandler(acessoNegadoHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        if (rateLimitHabilitado) {
            http.addFilterAfter(new LimiteRequisicoesFilter(rateLimitAutenticado, rateLimitAnonimo, objectMapper,
                    Clock.systemUTC()), JwtAuthenticationFilter.class);
        }
        return http.build();
    }

    /** Libera o frontend de teste (`frontend/`, ver ADR-006) a chamar a API a partir de outra origem. */
    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsAllowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(FORCA_BCRYPT);
    }
}
