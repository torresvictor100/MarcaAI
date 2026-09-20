package com.marcaai.shared;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Cifra o CPF ao gravar e decifra ao ler (ADR-010). Instanciado pelo Spring (bean container do Hibernate),
 * por isso recebe a {@link CriptografiaDados} no construtor.
 */
@Converter
public class CpfConverter implements AttributeConverter<String, String> {

    private final CriptografiaDados criptografia;

    public CpfConverter(CriptografiaDados criptografia) {
        this.criptografia = criptografia;
    }

    @Override
    public String convertToDatabaseColumn(String cpf) {
        return criptografia.cifrar(cpf);
    }

    @Override
    public String convertToEntityAttribute(String valorNoBanco) {
        return criptografia.decifrar(valorNoBanco);
    }
}
