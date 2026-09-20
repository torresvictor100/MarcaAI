#!/usr/bin/env bash
# Scan de segurança DAST com o OWASP ZAP (curso de Segurança, aula 5; ADR-010).
#
#   scripts/zap-scan.sh
#
# O scan ativo manda ataques de verdade (injeção, valores inválidos, POSTs), então NÃO roda contra o banco de
# demonstração: sobe um ambiente descartável numa rede Docker isolada — Postgres novo + a imagem do backend
# (gerada pelo `docker compose build`), com segredos temporários —, escaneia autenticado e apaga tudo no fim.
#
#   1. API (zap-api-scan, ativo): todas as rotas do OpenAPI, com token de ADMIN no header Authorization.
#   2. Frontend (zap-baseline, passivo): a imagem do nginx do front, também descartável.
#
# Relatórios em relatorios-zap/ (fora do git; HTML para ler, JSON para comparar entre execuções).
# O rate limit fica desligado no ambiente do scan; com ele ligado, o ZAP levaria 429 e testaria quase nada.
set -euo pipefail

raiz=$(cd "$(dirname "$0")/.." && pwd)
saida="$raiz/relatorios-zap"
rede=marcaai-zap
imagem_zap=ghcr.io/zaproxy/zaproxy:stable
mkdir -p "$saida"
chmod 777 "$saida" # o ZAP roda como usuário 'zap' (uid 1000) dentro do container

limpar() {
    docker rm -f zap-backend zap-db zap-frontend >/dev/null 2>&1 || true
    docker network rm "$rede" >/dev/null 2>&1 || true
}
trap limpar EXIT
limpar

for img in marcaai-backend marcaai-frontend; do
    docker image inspect "$img" >/dev/null 2>&1 || { echo "Imagem $img não encontrada: rode 'docker compose build' antes." >&2; exit 1; }
done

echo ">> Subindo ambiente descartável (rede $rede)"
docker network create "$rede" >/dev/null
docker run -d --name zap-db --network "$rede" -e POSTGRES_DB=marcaai -e POSTGRES_USER=postgres \
    -e POSTGRES_PASSWORD=postgres postgres:16-alpine >/dev/null

# Segredos só deste scan: gerados agora e descartados junto com os containers.
eval "$("$raiz/scripts/gerar-segredos.sh" | sed 's/^/export /')"
until docker exec zap-db pg_isready -U postgres >/dev/null 2>&1; do sleep 1; done

docker run -d --name zap-backend --network "$rede" -p 127.0.0.1::8080 \
    -e SPRING_DATASOURCE_URL=jdbc:postgresql://zap-db:5432/marcaai \
    -e SPRING_DATASOURCE_USERNAME=postgres -e SPRING_DATASOURCE_PASSWORD=postgres \
    -e JWT_PRIVATE_KEY -e JWT_PUBLIC_KEY -e MARCAAI_CRIPTO_CHAVE \
    -e MARCAAI_RATE_LIMIT_HABILITADO=false -e ANTHROPIC_API_KEY= -e TZ=America/Sao_Paulo \
    marcaai-backend >/dev/null
docker run -d --name zap-frontend --network "$rede" -e API_ORIGIN=http://zap-backend:8080 marcaai-frontend >/dev/null

porta=$(docker port zap-backend 8080/tcp | head -1 | sed 's/.*://')
echo ">> Aguardando o backend (porta local $porta)"
for _ in $(seq 1 60); do
    curl -sf "http://127.0.0.1:$porta/actuator/health" >/dev/null && break
    sleep 2
done
curl -sf "http://127.0.0.1:$porta/actuator/health" >/dev/null || { docker logs zap-backend | tail -30; exit 1; }

token=$(curl -sf -X POST "http://127.0.0.1:$porta/auth/login" -H 'Content-Type: application/json' \
    -d '{"login":"admin","senha":"Senha123!"}' | sed -E 's/.*"token":"([^"]+)".*/\1/')
[ -n "$token" ] || { echo "Login do admin falhou" >&2; exit 1; }

echo ">> Scan ativo da API (pode levar alguns minutos)"
set +e
# ZAP_AUTH_HEADER_VALUE: o ZAP põe o token em toda requisição, para passar da autenticação e testar as rotas.
docker run --rm --network "$rede" -v "$saida:/zap/wrk:rw" -e ZAP_AUTH_HEADER_VALUE="Bearer $token" \
    "$imagem_zap" zap-api-scan.py -t http://zap-backend:8080/v3/api-docs -f openapi \
    -r zap-api.html -J zap-api.json
codigo_api=$?

echo ">> Scan passivo do frontend"
docker run --rm --network "$rede" -v "$saida:/zap/wrk:rw" "$imagem_zap" zap-baseline.py \
    -t http://zap-frontend:8080 -r zap-frontend.html -J zap-frontend.json
codigo_front=$?
set -e

# Códigos do ZAP: 0 = sem alerta, 1 = alerta de falha (FAIL), 2 = só avisos (WARN), 3 = erro do scan.
echo ">> Resultado: API=$codigo_api frontend=$codigo_front (0 ok, 1 falha, 2 avisos, 3 erro). Relatórios em relatorios-zap/"
[ "$codigo_api" -ne 1 ] && [ "$codigo_api" -ne 3 ] && [ "$codigo_front" -ne 1 ] && [ "$codigo_front" -ne 3 ]
