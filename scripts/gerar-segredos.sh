#!/usr/bin/env bash
# Gera os segredos do backend (ADR-010) e imprime as linhas prontas para o .env:
#   - JWT_PRIVATE_KEY / JWT_PUBLIC_KEY: par RSA de 2048 bits que assina e valida o JWT (Base64 do PEM, uma linha);
#   - MARCAAI_CRIPTO_CHAVE: chave mestra AES de 32 bytes que cifra o CPF no banco;
#   - MARCAAI_METRICAS_TOKEN / GRAFANA_SENHA_ADMIN: senha de coleta do Prometheus e do admin do Grafana (ADR-011).
#
#   scripts/gerar-segredos.sh >> .env
#
# Só no .env (nunca versionado) ou no gerenciador de segredos. ATENÇÃO: trocar a MARCAAI_CRIPTO_CHAVE de um
# banco que já tem dados torna os CPFs gravados ilegíveis — ela só é gerada uma vez por banco.
set -euo pipefail

command -v openssl >/dev/null || { echo "openssl não encontrado" >&2; exit 1; }

dir=$(mktemp -d)
trap 'rm -rf "$dir"' EXIT

# genpkey já gera a privada em PKCS#8, o formato que o Java lê sem biblioteca extra.
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$dir/privada.pem" 2>/dev/null
openssl rsa -in "$dir/privada.pem" -pubout -out "$dir/publica.pem" 2>/dev/null

corpo() { grep -v -- '-----' "$1" | tr -d '\n'; }

echo "JWT_PRIVATE_KEY=$(corpo "$dir/privada.pem")"
echo "JWT_PUBLIC_KEY=$(corpo "$dir/publica.pem")"
echo "MARCAAI_CRIPTO_CHAVE=$(openssl rand -base64 32)"
echo "MARCAAI_METRICAS_TOKEN=$(openssl rand -hex 24)"
echo "GRAFANA_SENHA_ADMIN=$(openssl rand -hex 12)"
