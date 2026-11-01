#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

# Auto-detect JDK 21 if JAVA_HOME is not explicitly pointing to Java 21
if [[ -z "${JAVA_HOME:-}" ]] || ! "$JAVA_HOME/bin/java" -version 2>&1 | grep -q '"21\.'; then
  GRADLE_JDK21="$(find "$HOME/.gradle/jdks" -maxdepth 3 -name "Home" 2>/dev/null | grep "21" | head -n 1 || true)"
  if [[ -n "$GRADLE_JDK21" ]]; then
    export JAVA_HOME="$GRADLE_JDK21"
  fi
fi

echo "[MedSupply] Building executable Spring Boot JARs..."
./gradlew :warehouse-rpc-service:bootJar :hospital-order-service:bootJar -x test

cleanup() {
  echo "[MedSupply] Shutting down services..."
  kill "${WAREHOUSE_PID:-}" "${HOSPITAL_PID:-}" "${FRONTEND_PID:-}" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

echo "[MedSupply] Starting warehouse-rpc-service (gRPC :9090, DBMS #2)..."
"$JAVA_HOME/bin/java" -jar warehouse-rpc-service/build/libs/warehouse-rpc-service-1.0.0.jar &
WAREHOUSE_PID=$!
sleep 4

echo "[MedSupply] Starting hospital-order-service (REST :8080, DBMS #1)..."
"$JAVA_HOME/bin/java" -jar hospital-order-service/build/libs/hospital-order-service-1.0.0.jar &
HOSPITAL_PID=$!
sleep 5

echo "[MedSupply] Starting medsupply-web-client (Vite React :5173)..."
(cd medsupply-web-client && npm run dev) &
FRONTEND_PID=$!

echo "[MedSupply] Platform live at http://localhost:5173 (REST API: http://localhost:8080/api/v1/metrics/summary)"
wait
