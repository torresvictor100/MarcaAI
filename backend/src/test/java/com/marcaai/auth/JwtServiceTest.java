package com.marcaai.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.IncorrectClaimException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Test;

import java.security.KeyFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final ChavesJwtDeTeste CHAVES = ChavesJwtDeTeste.PRINCIPAL;

    @Test
    void tokenAssinadoComAPrivadaEValidadoComAPublicaTrazEmissorUsuarioEPapel() {
        JwtService jwt = CHAVES.jwtService(60_000L);

        Claims claims = jwt.validarEExtrairClaims(jwt.gerarToken(7L, "bruno.ubs", Papel.MEDICO_UBS));

        assertThat(claims.getIssuer()).isEqualTo(JwtService.EMISSOR);
        assertThat(claims.getSubject()).isEqualTo("bruno.ubs");
        assertThat(claims.get("usuarioId", String.class)).isEqualTo("7");
        assertThat(claims.get("papel", String.class)).isEqualTo("MEDICO_UBS");
    }

    @Test
    void cabecalhoDoTokenDizRs256() {
        String token = CHAVES.jwtService(60_000L).gerarToken(1L, "admin", Papel.ADMIN);
        String cabecalho = new String(Base64.getUrlDecoder().decode(token.split("\\.")[0]));

        assertThat(cabecalho).contains("\"alg\":\"RS256\"");
    }

    @Test
    void recusaTokenAssinadoPorOutraChave() {
        String forjado = ChavesJwtDeTeste.gerar().jwtService(60_000L).gerarToken(1L, "admin", Papel.ADMIN);

        assertThatThrownBy(() -> CHAVES.jwtService(60_000L).validarEExtrairClaims(forjado))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    void recusaTokenDeOutroEmissorMesmoComAssinaturaValida() throws Exception {
        var privada = KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(CHAVES.privada)));
        String deOutroEmissor = Jwts.builder().issuer("outro-sistema").subject("admin")
                .claim("usuarioId", "1").claim("papel", "ADMIN").signWith(privada, Jwts.SIG.RS256).compact();

        assertThatThrownBy(() -> CHAVES.jwtService(60_000L).validarEExtrairClaims(deOutroEmissor))
                .isInstanceOf(IncorrectClaimException.class);
    }

    @Test
    void recusaTokenSemAssinatura() {
        String semAssinatura = Jwts.builder().issuer(JwtService.EMISSOR).subject("admin").claim("papel", "ADMIN").compact();

        assertThatThrownBy(() -> CHAVES.jwtService(60_000L).validarEExtrairClaims(semAssinatura))
                .isInstanceOf(io.jsonwebtoken.UnsupportedJwtException.class);
        assertThatThrownBy(() -> CHAVES.jwtService(60_000L).validarEExtrairClaims("a.b.c"))
                .isInstanceOf(MalformedJwtException.class);
    }

    @Test
    void aceitaAsChavesEmPemComQuebraDeLinhaLiteral() {
        String pemPrivada = "-----BEGIN PRIVATE KEY-----\\n" + CHAVES.privada + "\\n-----END PRIVATE KEY-----";
        String pemPublica = "-----BEGIN PUBLIC KEY-----\n" + CHAVES.publica + "\n-----END PUBLIC KEY-----\n";

        JwtService jwt = new JwtService(pemPrivada, pemPublica, 60_000L);

        assertThat(jwt.validarEExtrairClaims(jwt.gerarToken(1L, "admin", Papel.ADMIN)).getSubject()).isEqualTo("admin");
    }

    @Test
    void naoSobeSemChaveComChaveInvalidaOuComParQueNaoCombina() {
        assertThatThrownBy(() -> new JwtService("", CHAVES.publica, 1L))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("JWT_PRIVATE_KEY");
        assertThatThrownBy(() -> new JwtService(CHAVES.privada, null, 1L))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("JWT_PUBLIC_KEY");
        assertThatThrownBy(() -> new JwtService("não é base64!", CHAVES.publica, 1L))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtService(Base64.getEncoder().encodeToString("lixo".getBytes()), CHAVES.publica, 1L))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("PKCS#8");
        assertThatThrownBy(() -> new JwtService(CHAVES.privada, Base64.getEncoder().encodeToString("lixo".getBytes()), 1L))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("X.509");
        assertThatThrownBy(() -> new JwtService(CHAVES.privada, ChavesJwtDeTeste.gerar().publica, 1L))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("não é o par");
    }

    @Test
    void recusaChavePkcs1ComOrientacaoDeConversao() {
        assertThatThrownBy(() -> new JwtService("-----BEGIN RSA PRIVATE KEY-----\nabc\n-----END RSA PRIVATE KEY-----",
                CHAVES.publica, 1L))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("PKCS#1");
    }

    @Test
    void recusaChaveMenorQue2048Bits() {
        ChavesJwtDeTeste fraca = ChavesJwtDeTeste.gerar(1024);

        assertThatThrownBy(() -> new JwtService(fraca.privada, fraca.publica, 1L))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("2048");
    }
}
