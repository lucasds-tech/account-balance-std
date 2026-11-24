#!/usr/bin/env bash

# ============================================================================
# Configura as filas SQS no LocalStack, incluindo a Dead Letter Queue (DLQ)
# A fila principal pode já ter sido criada pelo message-generator
# Execução: ./scripts/create-queue.sh
# ============================================================================

set -uo pipefail

ENDPOINT="${LOCALSTACK_ENDPOINT:-http://localhost:4566}"
REGION="${AWS_DEFAULT_REGION:-sa-east-1}"

MAIN_QUEUE="transacoes-financeiras-processadas"
DEAD_LETTER_QUEUE="${MAIN_QUEUE}-dlq"

AWS_ACCOUNT_ID="000000000000"
MAX_RETRIES="5"

# Verifica se o AWS CLI está instalada
if ! aws --version >/dev/null 2>&1; then
    echo "Erro: AWS CLI não encontrado."
    echo "Instale em: https://aws.amazon.com/cli/"
    exit 1
fi

# Cria a Dead Letter Queue
echo "=== Configurando Dead Letter Queue: ${DEAD_LETTER_QUEUE}"

DEAD_LETTER_URL=$(aws \
    --endpoint-url "${ENDPOINT}" \
    --region "${REGION}" \
    sqs create-queue \
    --queue-name "${DEAD_LETTER_QUEUE}" \
    --query 'QueueUrl' \
    --output text 2>/dev/null) || echo "    DLQ já existente ou não foi possível criá-la."

# Define o ARN da DLQ e a política de redirecionamento
DEAD_LETTER_ARN="arn:aws:sqs:${REGION}:${AWS_ACCOUNT_ID}:${DEAD_LETTER_QUEUE}"
REDRIVE_POLICY="{\"deadLetterTargetArn\":\"${DEAD_LETTER_ARN}\",\"maxReceiveCount\":\"${MAX_RETRIES}\"}"

# Cria a fila principal com a política de redirecionament
echo "=== Configurando fila principal: ${MAIN_QUEUE}"
echo "    Tentativas máximas antes do redirecionamento: ${MAX_RETRIES}"

aws \
    --endpoint-url "${ENDPOINT}" \
    --region "${REGION}" \
    sqs create-queue \
    --queue-name "${MAIN_QUEUE}" \
    --attributes "{\"RedrivePolicy\":\"${REDRIVE_POLICY}\"}" \
    >/dev/null 2>&1 \
    || echo "    Fila já existente ou configuração pendente de verificação;"

# Exibe as filas disponíveis no ambiente
echo ""
echo "=== Filas registradas no LocalStack:"

aws \
    --endpoint-url "${ENDPOINT}" \
    --region "${REGION}" \
    sqs list-queues

# Exibe um comando para consultar mensagens
echo ""
echo "==> Comando para consultar as mensagens da fila principal:"
echo "aws --endpoint-url ${ENDPOINT} --region ${REGION} sqs receive-message \\"
echo "    --queue-url ${ENDPOINT}/${AWS_ACCOUNT_ID}/${MAIN_QUEUE} \\"
echo "    --max-number-of-messages 10"
