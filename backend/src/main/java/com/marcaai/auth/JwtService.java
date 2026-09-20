package com.marcaai.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;

/**
 * Emite e valida o JWT com par de chaves RSA (RS256, ADR-010): o token é assinado com a chave privada e
 * conferido com a pública — quem só precisa validar não tem como emitir. O {@code iss} é conferido na
 * validação; token de outro emissor é recusado.
 *
 * <p>As chaves vêm do ambiente ({@code JWT_PRIVATE_KEY}/{@code JWT_PUBLIC_KEY}), em PEM ou só o Base64 do
 * conteúdo (gerar com {@code scripts/gerar-segredos.sh}). Sem elas, chave menor que 2048 bits ou par que
 * não combina, a aplicação não sobe.
 */
@Service
public class JwtService {

    public static final String EMISSOR = "marcaai-backend";
    static final int TAMANHO_MINIMO_CHAVE_BITS = 2048;
    private static final String COMO_GERAR = " (gere o par com: scripts/gerar-segredos.sh)";

    private final PrivateKey chavePrivada;
    private final PublicKey chavePublica;
    private final long expirationMs;

    public JwtService(
            @Value("${marcaai.jwt.private-key}") String chavePrivada,
            @Value("${marcaai.jwt.public-key}") String chavePublica,
            @Value("${marcaai.jwt.expiration-ms}") long expirationMs) {
        this.chavePrivada = lerChavePrivada(chavePrivada);
        this.chavePublica = lerChavePublica(chavePublica);
        this.expirationMs = expirationMs;
        conferirPar(this.chavePrivada, this.chavePublica);
    }

    public String gerarToken(Long usuarioId, String login, Papel papel) {
        Date agora = new Date();
        Date expiracao = new Date(agora.getTime() + expirationMs);
        return Jwts.builder()
                .issuer(EMISSOR)
                .subject(login)
                .claim("usuarioId", String.valueOf(usuarioId))
                .claim("papel", papel.name())
                .issuedAt(agora)
                .expiration(expiracao)
                .signWith(chavePrivada, Jwts.SIG.RS256)
                .compact();
    }

    public Claims validarEExtrairClaims(String token) {
        return Jwts.parser()
                .verifyWith(chavePublica)
                .requireIssuer(EMISSOR)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    static PrivateKey lerChavePrivada(String texto) {
        if (texto != null && texto.contains("BEGIN RSA PRIVATE KEY")) {
            throw new IllegalStateException("JWT_PRIVATE_KEY está no formato PKCS#1; converta para PKCS#8 com: "
                    + "openssl pkcs8 -topk8 -nocrypt -in chave.pem" + COMO_GERAR);
        }
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decodificar(texto, "JWT_PRIVATE_KEY")));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("JWT_PRIVATE_KEY não é uma chave privada RSA válida (PKCS#8)" + COMO_GERAR, ex);
        }
    }

    static PublicKey lerChavePublica(String texto) {
        try {
            PublicKey chave = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decodificar(texto, "JWT_PUBLIC_KEY")));
            if (((RSAPublicKey) chave).getModulus().bitLength() < TAMANHO_MINIMO_CHAVE_BITS) {
                throw new IllegalStateException("A chave RSA do JWT precisa ter pelo menos " + TAMANHO_MINIMO_CHAVE_BITS
                        + " bits" + COMO_GERAR);
            }
            return chave;
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("JWT_PUBLIC_KEY não é uma chave pública RSA válida (X.509)" + COMO_GERAR, ex);
        }
    }

    /** Aceita PEM completo, PEM com "\n" literal (comum em .env) ou só o Base64. */
    private static byte[] decodificar(String texto, String variavel) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalStateException(variavel + " não configurada" + COMO_GERAR);
        }
        String base64 = texto.replace("\\n", "")
                .replaceAll("-----(BEGIN|END) [A-Z ]+-----", "")
                .replaceAll("\\s", "");
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(variavel + " não está em Base64/PEM" + COMO_GERAR, ex);
        }
    }

    /** Assina e confere um texto de teste: pega na subida um JWT_PUBLIC_KEY que não é o par da privada. */
    private static void conferirPar(PrivateKey privada, PublicKey publica) {
        try {
            byte[] amostra = "marcaai".getBytes(StandardCharsets.UTF_8);
            Signature assinador = Signature.getInstance("SHA256withRSA");
            assinador.initSign(privada);
            assinador.update(amostra);
            byte[] assinatura = assinador.sign();
            Signature verificador = Signature.getInstance("SHA256withRSA");
            verificador.initVerify(publica);
            verificador.update(amostra);
            if (!verificador.verify(assinatura)) {
                throw new IllegalStateException("JWT_PUBLIC_KEY não é o par de JWT_PRIVATE_KEY" + COMO_GERAR);
            }
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Não foi possível conferir o par de chaves do JWT" + COMO_GERAR, ex);
        }
    }
}
