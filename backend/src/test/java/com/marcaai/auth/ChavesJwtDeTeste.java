package com.marcaai.auth;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/** Pares de chaves RSA gerados em memória para os testes — nenhuma chave real fica no repositório. */
public final class ChavesJwtDeTeste {

    public static final ChavesJwtDeTeste PRINCIPAL = gerar();

    public final String privada;
    public final String publica;

    private ChavesJwtDeTeste(String privada, String publica) {
        this.privada = privada;
        this.publica = publica;
    }

    public static ChavesJwtDeTeste gerar() {
        return gerar(2048);
    }

    static ChavesJwtDeTeste gerar(int bits) {
        try {
            KeyPairGenerator gerador = KeyPairGenerator.getInstance("RSA");
            gerador.initialize(bits);
            KeyPair par = gerador.generateKeyPair();
            Base64.Encoder base64 = Base64.getEncoder();
            return new ChavesJwtDeTeste(base64.encodeToString(par.getPrivate().getEncoded()),
                    base64.encodeToString(par.getPublic().getEncoded()));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public JwtService jwtService(long expiracaoMs) {
        return new JwtService(privada, publica, expiracaoMs);
    }
}
