# MedSupply — Distributed Clinical Order & Central Warehouse RPC Platform

**Authors:** Antoine Fourneyron, Elias Hafsia, and Arthur Smadja (Master 1 Engineering)  
**Repository:** `https://github.com/afon300/medsupply-platform`

MedSupply is a distributed healthcare supply-chain platform engineered for the **Master 1 Distributed & Object-Oriented Software Architecture** course. It models the operational boundary between a **Hospital Procurement Domain** (exposing a stateless RESTful Web API backed by its own relational DBMS) and a **Central Regional Pharmaceutical Warehouse** (exposing a strongly-typed Protocol Buffers / gRPC service backed by an isolated inventory DBMS).

---

## 1. Architectural Topology

```text
+---------------------------+        HTTP/JSON (REST)        +-----------------------------------+
|   medsupply-web-client    | -----------------------------> |      hospital-order-service       |
|  React 18 + TypeScript    | <----------------------------- |  Spring Boot 3 · Stateless REST   |
|  Single-Page App (:5173)  |   Structured DTOs & RFC Errors |  + Bounded-Deadline gRPC Client   |
+---------------------------+                                +-----------------+-----------------+
                                                                               |             |
                                                                   Spring Data |             | HTTP/2 Protobuf
                                                                       JPA     |             | (gRPC :9090)
                                                                               v             v
                                                             +-----------------+---+   +-----+-----------------------------+
                                                             |   Hospital DBMS #1  |   |       warehouse-rpc-service       |
                                                             |  (H2 / PostgreSQL)  |   |   Spring Boot 3 · gRPC Server     |
                                                             | - departments       |   |  - FEFO Multi-Batch Allocation    |
                                                             | - orders & lines    |   |  - Idempotency & Saga Release     |
                                                             +---------------------+   +-----------------+-----------------+
                                                                                                         |
                                                                                             Spring Data | JPA
                                                                                                         v
                                                                                       +-----------------+-----------------+
                                                                                       |         Warehouse DBMS #2         |
                                                                                       |         (H2 / PostgreSQL)         |
                                                                                       | - medical_supply_items            |
                                                                                       | - inventory_batches (@Version)    |
                                                                                       | - stock_reservations              |
                                                                                       +-----------------------------------+
```

---

## 2. Repository Organization & Course Best Practices

Each service and contract is isolated in its own sub-project folder within the Git repository:

| Sub-Project Directory | Role | Technology Stack |
|---|---|---|
| `warehouse-rpc-contract/` | Shared Protocol Buffers (`proto3`) schema & generated gRPC stubs | Protobuf `3.25.5`, `grpc-java 1.63.0` |
| `warehouse-rpc-service/` | Central Pharmaceutical Warehouse gRPC Server + isolated DBMS #2 | Java 21, Spring Boot `3.3.4`, `grpc-server-spring-boot-starter`, JPA |
| `hospital-order-service/` | Stateless Hospital REST API + gRPC Client + isolated DBMS #1 | Java 21, Spring Boot `3.3.4`, Spring Web, Bean Validation, JPA |
| `medsupply-web-client/` | Interactive Clinical Procurement & Telemetry Web Client | React 18, TypeScript 5, Vite 5 |

### Compliance Matrix

- **One folder per service / sub-project**: Enforced via Gradle multi-project (`settings.gradle`) plus an independent frontend workspace (`medsupply-web-client/`).
- **Strict 3-Tier Layered Architecture**: Both backend services enforce clean separation across `web` (or `grpc`), `service`, and `entity` packages, communicating externally exclusively via immutable Java `record` DTOs / Protobuf messages.
- **Inversion of Control (IoC)**: Constructor-based dependency injection (`@Autowired`) over explicit service interfaces (`ProcurementOrderService`, `DepartmentService`, `WarehouseRpcGateway`, `WarehouseInventoryService`).
- **Structured Logging (`SLF4J`)**: `LoggerFactory.getLogger(...)` with per-request `MDC` correlation IDs (`X-Correlation-Id`); **zero** `System.out` / `System.err` statements across the codebase.
- **Global HTTP Exception Handler**: `@RestControllerAdvice` (`GlobalRestExceptionHandler`) maps domain, validation, and upstream gRPC errors (`400`, `404`, `409`, `422`, `503`, `500`) into uniform `ApiErrorResponse` JSON payloads.
- **Stateless Web Service**: Zero HTTP sessions (`tracking-modes: []`, `Cache-Control: no-store`); every request is self-contained, with idempotent retries persisted via `Idempotency-Key` in DBMS #1 and DBMS #2.

---

## 3. Build, Test & Run Instructions

### Option A — Local Execution (Zero External DB Setup, Dual Persistent H2 DBMS)

**Prerequisites**: JDK 21, Node.js 20+.

```bash
# 1. Compile Protobuf stubs, both Spring Boot services, and run all JUnit 5 integration tests
./gradlew clean test

# 2. Launch the full stack (Warehouse gRPC :9090 + Hospital REST :8080 + React SPA :5173)
chmod +x scripts/run-all.sh
./scripts/run-all.sh
```

Or launch each service in its own terminal:

```bash
# Terminal 1 — Central Warehouse gRPC Server (:9090)
./gradlew :warehouse-rpc-service:bootRun

# Terminal 2 — Hospital Procurement REST Web Service (:8080)
./gradlew :hospital-order-service:bootRun

# Terminal 3 — React + TypeScript Frontend (:5173)
cd medsupply-web-client && npm install && npm run dev
```

### Option B — Full Containerized Deployment (Two Isolated PostgreSQL 16 Containers)

```bash
docker compose up --build
```

- React Web Portal: `http://localhost:5173`
- Hospital REST API: `http://localhost:8080/api/v1`
- Warehouse gRPC Server: `localhost:9090`
- Hospital PostgreSQL DBMS: `localhost:5434` (`hospital_db`)
- Warehouse PostgreSQL DBMS: `localhost:5433` (`warehouse_db`)

---

## 4. REST API Quick Verification (`curl`)

```bash
# 1. Fetch live pharmaceutical catalog (REST -> gRPC ListCatalog)
curl -i http://localhost:8080/api/v1/catalog

# 2. Inspect FEFO batch breakdown for Titanium Endovascular Stent Graft Kit
curl -s http://localhost:8080/api/v1/catalog/MED-SUR-003 | jq

# 3. Submit a stateless multi-batch FEFO order with an explicit Idempotency-Key
curl -i -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: DEMO-ORDER-001" \
  -d '{
    "departmentCode": "DEPT-SURG",
    "requestedByClinician": "Dr. Antoine Lefevre, MD",
    "priority": "URGENT",
    "clinicalNotes": "Vascular theater B2",
    "items": [
      { "sku": "MED-SUR-003", "quantity": 10 },
      { "sku": "MED-VAC-001", "quantity": 3 }
    ]
  }'

# 4. Trigger structured HTTP 422 error + automatic Saga compensating gRPC stock rollback
curl -i -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{
    "departmentCode": "DEPT-EMERG",
    "requestedByClinician": "Dr. Julien Garnier, MD",
    "priority": "URGENT",
    "clinicalNotes": "Exceeds remaining quarterly budget",
    "items": [{ "sku": "MED-SUR-003", "quantity": 5 }]
  }'
```
