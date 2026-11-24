#!/usr/bin/env bash

# ============================================================================
# Remove arquivos e diretórios obsoletos da estrutura anterior do projeto.
# Evita conflitos de pacotes, classes duplicadas e referências antigas.
#
# Exemplo de conflito:
#   "com.itau.ingestor.dto.TransactionMessage cannot be converted to
#    com.itau.ingestor.message.TransactionMessage"
#
# Execução: ./scripts/clean-old-structure.sh
# ============================================================================

set -uo pipefail

# Define a raiz do projeto como diretório de trabalho.
cd "$(dirname "$0")/.." || exit 1

echo "=== [1/3] Removendo pacotes obsoletos do ingestor..."
echo "    Estrutura antiga: dto/, model/ e repository/"
echo "    Estrutura atual: message/ e persistence/"

rm -rf \
    ingestor/src/main/java/com/itau/ingestor/dto \
    ingestor/src/main/java/com/itau/ingestor/model \
    ingestor/src/main/java/com/itau/ingestor/repository

echo ""
echo "=== [2/3] Removendo diretórios de testes em locais incorretos..."

rm -rf \
    api/src/main/test \
    ingestor/src/main/test

echo ""
echo "=== [3/3] Removendo artefatos de compilações anteriores..."

rm -rf \
    ingestor/target \
    api/target \
    db-migrations/target

# Verifica referências remanescentes aos pacotes antigos.
echo ""
echo "==> Verificando referências ao pacote obsoleto 'ingestor.dto'..."

if grep -rn "ingestor\.dto" ingestor/src api/src 2>/dev/null; then
    echo ""
    echo "AVISO: Foram encontradas referências ao pacote antigo."
    echo "Verifique os arquivos listados e atualize os imports necessários."
else
    echo "    Nenhuma referência antiga encontrada."
fi

echo ""
echo "=== Limpeza concluída!"
echo "    Execute os testes unitários com:"
echo ""
echo "    mvn clean test -Dgroups=unit"
