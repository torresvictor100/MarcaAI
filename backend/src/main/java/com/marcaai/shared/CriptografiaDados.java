package com.marcaai.shared;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Criptografia de dado pessoal em repouso (LGPD, ADR-010) — hoje, o CPF do paciente.
 *
 * <ul>
 *   <li><b>Cifrar/decifrar:</b> AES-256-GCM (simétrica e autenticada: detecta adulteração), com IV aleatório
 *   de 12 bytes por valor. Gravado como {@code v1:<Base64(IV + texto cifrado + tag)>}.</li>
 *   <li><b>Índice cego:</b> HMAC-SHA256 do CPF só com dígitos. Como o texto cifrado muda a cada gravação,
 *   é esse hash que garante CPF único e permite buscar por CPF sem decifrar a tabela inteira.</li>
 * </ul>
 *
 * <p>As duas chaves são derivadas (HMAC) de uma chave mestra de 32 bytes, {@code MARCAAI_CRIPTO_CHAVE}, que
 * só existe no ambiente. Sem ela a aplicação não sobe. Perder a chave = perder os CPFs cifrados.
 */
@Component
public class CriptografiaDados {

    static final String PREFIXO = "v1:";
    private static final int TAMANHO_CHAVE_BYTES = 32;
    private static final int TAMANHO_IV_BYTES = 12;
    private static final int TAMANHO_TAG_BITS = 128;
    private static final byte[] CONTEXTO = "pacientes.cpf".getBytes(StandardCharsets.UTF_8);
    private static final String COMO_GERAR = " (gere com: scripts/gerar-segredos.sh)";

    private final SecretKeySpec chaveCifra;
    private final SecretKeySpec chaveHash;
    private final SecureRandom aleatorio = new SecureRandom();

    public CriptografiaDados(@Value("${marcaai.cripto.chave:}") String chaveMestraBase64) {
        byte[] mestra = lerChaveMestra(chaveMestraBase64);
        this.chaveCifra = new SecretKeySpec(derivar(mestra, "marcaai-aes-gcm"), "AES");
        this.chaveHash = new SecretKeySpec(derivar(mestra, "marcaai-hmac-indice"), "HmacSHA256");
    }

    public String cifrar(String texto) {
        if (texto == null) {
            return null;
        }
        try {
            byte[] iv = new byte[TAMANHO_IV_BYTES];
            aleatorio.nextBytes(iv);
            Cipher cifra = Cipher.getInstance("AES/GCM/NoPadding");
            cifra.init(Cipher.ENCRYPT_MODE, chaveCifra, new GCMParameterSpec(TAMANHO_TAG_BITS, iv));
            cifra.updateAAD(CONTEXTO);
            byte[] cifrado = cifra.doFinal(texto.getBytes(StandardCharsets.UTF_8));
            return PREFIXO + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + cifrado.length)
                    .put(iv).put(cifrado).array());
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Falha ao cifrar dado pessoal", ex);
        }
    }

    /** Valor sem o prefixo {@code v1:} ainda não foi cifrado (linha anterior à migração) e volta como está. */
    public String decifrar(String valor) {
        if (valor == null || !estaCifrado(valor)) {
            return valor;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(valor.substring(PREFIXO.length()));
            Cipher cifra = Cipher.getInstance("AES/GCM/NoPadding");
            cifra.init(Cipher.DECRYPT_MODE, chaveCifra, new GCMParameterSpec(TAMANHO_TAG_BITS, bytes, 0, TAMANHO_IV_BYTES));
            cifra.updateAAD(CONTEXTO);
            return new String(cifra.doFinal(bytes, TAMANHO_IV_BYTES, bytes.length - TAMANHO_IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            // Chave errada ou valor adulterado: a tag do GCM não confere. Não devolve lixo.
            throw new IllegalStateException("Não foi possível decifrar o dado pessoal (chave errada ou valor adulterado)", ex);
        }
    }

    public static boolean estaCifrado(String valor) {
        return valor != null && valor.startsWith(PREFIXO);
    }

    /** Índice cego do CPF: HMAC dos dígitos, em hexadecimal (64 caracteres). */
    public String indiceCpf(String cpf) {
        if (cpf == null) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(chaveHash);
            return HexFormat.of().formatHex(mac.doFinal(cpf.replaceAll("\\D", "").getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Falha ao calcular o índice do CPF", ex);
        }
    }

    private static byte[] lerChaveMestra(String base64) {
        if (base64 == null || base64.isBlank()) {
            throw new IllegalStateException("MARCAAI_CRIPTO_CHAVE não configurada" + COMO_GERAR);
        }
        byte[] chave;
        try {
            chave = Base64.getDecoder().decode(base64.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("MARCAAI_CRIPTO_CHAVE não está em Base64" + COMO_GERAR, ex);
        }
        if (chave.length != TAMANHO_CHAVE_BYTES) {
            throw new IllegalStateException("MARCAAI_CRIPTO_CHAVE precisa ter exatamente " + TAMANHO_CHAVE_BYTES
                    + " bytes" + COMO_GERAR);
        }
        return chave;
    }

    private static byte[] derivar(byte[] mestra, String finalidade) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(mestra, "HmacSHA256"));
            return mac.doFinal(finalidade.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Falha ao derivar as chaves de criptografia", ex);
        }
    }
}
