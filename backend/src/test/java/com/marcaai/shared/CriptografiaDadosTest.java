package com.marcaai.shared;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CriptografiaDadosTest {

    private static final String CHAVE = Base64.getEncoder().encodeToString("chave-de-teste-com-32-bytes!!!!!".getBytes());
    private static final String OUTRA_CHAVE = Base64.getEncoder().encodeToString("outra-chave-com-32-bytes-também".getBytes());

    private final CriptografiaDados cripto = new CriptografiaDados(CHAVE);

    @Test
    void cifraEDecifraSemGuardarOCpfEmClaro() {
        String cifrado = cripto.cifrar("12345678901");

        assertThat(cifrado).startsWith("v1:").doesNotContain("12345678901");
        assertThat(cripto.decifrar(cifrado)).isEqualTo("12345678901");
    }

    @Test
    void mesmoCpfGeraTextoCifradoDiferenteACadaVezMasOMesmoIndice() {
        assertThat(cripto.cifrar("12345678901")).isNotEqualTo(cripto.cifrar("12345678901"));
        assertThat(cripto.indiceCpf("12345678901"))
                .isEqualTo(cripto.indiceCpf("123.456.789-01"))
                .hasSize(64)
                .isNotEqualTo(cripto.indiceCpf("12345678902"));
    }

    @Test
    void valorAdulteradoOuCifradoComOutraChaveNaoDecifra() {
        String cifrado = cripto.cifrar("12345678901");
        char ultimo = cifrado.charAt(cifrado.length() - 2);
        String adulterado = cifrado.substring(0, cifrado.length() - 2) + (ultimo == 'A' ? 'B' : 'A') + cifrado.charAt(cifrado.length() - 1);

        assertThatThrownBy(() -> cripto.decifrar(adulterado)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new CriptografiaDados(OUTRA_CHAVE).decifrar(cifrado)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cripto.decifrar("v1:não-é-base64")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void indiceDependeDaChave() {
        assertThat(new CriptografiaDados(OUTRA_CHAVE).indiceCpf("12345678901")).isNotEqualTo(cripto.indiceCpf("12345678901"));
    }

    @Test
    void valorAindaNaoCifradoENuloPassamComoEstao() {
        assertThat(cripto.decifrar("12345678901")).isEqualTo("12345678901");
        assertThat(cripto.decifrar(null)).isNull();
        assertThat(cripto.cifrar(null)).isNull();
        assertThat(cripto.indiceCpf(null)).isNull();
        assertThat(CriptografiaDados.estaCifrado("12345678901")).isFalse();
    }

    @Test
    void naoSobeSemChaveOuComChaveDeTamanhoErrado() {
        assertThatThrownBy(() -> new CriptografiaDados("")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MARCAAI_CRIPTO_CHAVE");
        assertThatThrownBy(() -> new CriptografiaDados(null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new CriptografiaDados("***")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Base64");
        assertThatThrownBy(() -> new CriptografiaDados(Base64.getEncoder().encodeToString(new byte[16])))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("32 bytes");
    }

    @Test
    void converterEListenerDoPacienteUsamACriptografia() {
        CpfConverter converter = new CpfConverter(cripto);
        String noBanco = converter.convertToDatabaseColumn("12345678901");
        assertThat(noBanco).startsWith("v1:");
        assertThat(converter.convertToEntityAttribute(noBanco)).isEqualTo("12345678901");

        Paciente paciente = Paciente.builder().cpf("12345678901").build();
        new PacienteCpfListener(cripto).atualizarIndice(paciente);
        assertThat(paciente.getCpfHash()).isEqualTo(cripto.indiceCpf("12345678901"));
    }
}
