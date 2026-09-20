package com.marcaai.config;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Registro dos eventos de segurança (OWASP A09 — falhas de log e monitoramento), num logger próprio
 * ({@code marcaai.seguranca}) para poder ser filtrado ou enviado a outro destino sem o resto da aplicação.
 *
 * <p>Formato fixo {@code evento=... login=... papel=... metodo=... rota=... ip=...}. Nunca registra senha,
 * token, CPF nem dado clínico. Todo valor vindo de quem chamou passa por {@link #limpar}, para que uma
 * quebra de linha no login não forje uma linha falsa no log (log injection).
 */
public final class LogSeguranca {

    private static final Logger log = LoggerFactory.getLogger("marcaai.seguranca");
    private static final int TAMANHO_MAXIMO = 80;

    private LogSeguranca() {
    }

    public static void loginOk(String login) {
        log.info("evento=LOGIN_OK login={} ip={}", limpar(login), ipAtual());
    }

    public static void loginFalhou(String login) {
        log.warn("evento=LOGIN_FALHOU login={} ip={}", limpar(login), ipAtual());
    }

    public static void loginBloqueado(String login) {
        log.warn("evento=LOGIN_BLOQUEADO login={} ip={}", limpar(login), ipAtual());
    }

    public static void naoAutenticado(HttpServletRequest request) {
        log.warn("evento=NAO_AUTENTICADO metodo={} rota={} ip={}",
                request.getMethod(), limpar(request.getRequestURI()), request.getRemoteAddr());
    }

    public static void tokenInvalido(HttpServletRequest request, String motivo) {
        log.warn("evento=TOKEN_INVALIDO motivo={} metodo={} rota={} ip={}",
                limpar(motivo), request.getMethod(), limpar(request.getRequestURI()), request.getRemoteAddr());
    }

    public static void acessoNegado(HttpServletRequest request) {
        MarcaAiPrincipal principal = principalOuNulo();
        log.warn("evento=ACESSO_NEGADO login={} papel={} metodo={} rota={} ip={}",
                principal == null ? "-" : limpar(principal.login()), principal == null ? "-" : principal.papel(),
                request.getMethod(), limpar(request.getRequestURI()), request.getRemoteAddr());
    }

    public static void limiteExcedido(HttpServletRequest request, String quem) {
        log.warn("evento=LIMITE_EXCEDIDO quem={} metodo={} rota={} ip={}",
                limpar(quem), request.getMethod(), limpar(request.getRequestURI()), request.getRemoteAddr());
    }

    /** Tira quebras de linha e caracteres de controle e corta textos longos. */
    static String limpar(String valor) {
        if (valor == null) {
            return "-";
        }
        String limpo = valor.replaceAll("\\p{Cntrl}", "_");
        return limpo.length() > TAMANHO_MAXIMO ? limpo.substring(0, TAMANHO_MAXIMO) + "…" : limpo;
    }

    private static String ipAtual() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes atributos) {
            return atributos.getRequest().getRemoteAddr();
        }
        return "-";
    }

    private static MarcaAiPrincipal principalOuNulo() {
        try {
            return AuthContext.atual();
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
