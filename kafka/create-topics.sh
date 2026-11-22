#!/usr/bin/env bash
# =============================================================================
# Crea (o reajusta) los topicos Kafka de ClassFlow. Es idempotente y replicable:
#   - Lo ejecuta el contenedor kafka-init de docker-compose.kafka.yml al levantar el cluster.
#   - Se puede volver a correr en cualquier momento:
#       docker compose -f docker-compose.kafka.yml run --rm kafka-init
#
# Cada flujo de datos tiene parametros distintos segun su uso:
#
# classflow.academic.grades        Estado de cada calificacion (key = gradeId).
#                                  cleanup.policy=compact,delete: conserva la ultima version de
#                                  cada nota (un consumidor nuevo puede reconstruir el estado) y
#                                  ademas descarta segmentos con mas de 180 dias.
# classflow.assistance.attendance  Bitacora de eventos de asistencia (key = studentId, garantiza
#                                  orden por estudiante). cleanup.policy=delete, 30 dias y tope de
#                                  1 GiB por particion: es un flujo de eventos, no de estado.
# *.DLT                            Mensajes que fallaron tras los reintentos. Mismas particiones que
#                                  el topico de origen (el recoverer conserva la particion) y
#                                  14 dias de retencion para analizarlos y reprocesarlos.
#
# Todos usan 3 particiones (paralelismo de hasta 3 consumidores por grupo), factor de
# replicacion 3 y min.insync.replicas=2 (con acks=all se tolera la caida de un broker).
# =============================================================================
set -euo pipefail

BOOTSTRAP="${BOOTSTRAP_SERVERS:-kafka-1:29092,kafka-2:29092,kafka-3:29092}"
DAY_MS=86400000

# nombre | particiones | replicas | configuraciones separadas por ';'
TOPICS=(
  "classflow.academic.grades|3|3|cleanup.policy=compact,delete;retention.ms=$((180 * DAY_MS));min.insync.replicas=2;min.compaction.lag.ms=3600000;segment.ms=$((7 * DAY_MS));max.message.bytes=1048576"
  "classflow.assistance.attendance|3|3|cleanup.policy=delete;retention.ms=$((30 * DAY_MS));retention.bytes=1073741824;min.insync.replicas=2;segment.ms=${DAY_MS};max.message.bytes=1048576"
  "classflow.academic.grades.DLT|3|3|cleanup.policy=delete;retention.ms=$((14 * DAY_MS));min.insync.replicas=2"
  "classflow.assistance.attendance.DLT|3|3|cleanup.policy=delete;retention.ms=$((14 * DAY_MS));min.insync.replicas=2"
)

echo "Esperando a que el cluster responda en ${BOOTSTRAP}..."
until kafka-topics --bootstrap-server "$BOOTSTRAP" --list > /dev/null 2>&1; do
  sleep 2
done

for entry in "${TOPICS[@]}"; do
  IFS='|' read -r name partitions replicas configs <<< "$entry"
  IFS=';' read -r -a pairs <<< "$configs"

  create_args=()   # --config clave=valor (kafka-topics acepta listas con coma tal cual)
  alter_items=()   # clave=valor para kafka-configs (las listas van entre corchetes)
  for kv in "${pairs[@]}"; do
    create_args+=(--config "$kv")
    key="${kv%%=*}"
    value="${kv#*=}"
    if [[ "$value" == *,* ]]; then
      alter_items+=("${key}=[${value}]")
    else
      alter_items+=("$kv")
    fi
  done
  alter_config=$(IFS=','; echo "${alter_items[*]}")

  echo "-> ${name} (particiones=${partitions}, replicas=${replicas})"
  kafka-topics --bootstrap-server "$BOOTSTRAP" --create --if-not-exists \
    --topic "$name" --partitions "$partitions" --replication-factor "$replicas" \
    "${create_args[@]}"

  # Si el topico ya existia, reaplica la configuracion para que quede alineada con este script.
  kafka-configs --bootstrap-server "$BOOTSTRAP" --alter \
    --entity-type topics --entity-name "$name" --add-config "$alter_config" > /dev/null

  # Las particiones solo pueden aumentar: se ajusta si el topico tiene menos de las declaradas.
  current=$(kafka-topics --bootstrap-server "$BOOTSTRAP" --describe --topic "$name" \
    | grep -c "Partition: " || true)
  if (( current < partitions )); then
    kafka-topics --bootstrap-server "$BOOTSTRAP" --alter --topic "$name" --partitions "$partitions"
  fi
done

echo
echo "Topicos de ClassFlow:"
kafka-topics --bootstrap-server "$BOOTSTRAP" --describe --exclude-internal | grep -E "^Topic:" || true
echo "kafka-init terminado."
