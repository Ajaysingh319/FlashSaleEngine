# Flash Sale & Ticket Reservation Engine

## Project Description
A high-concurrency flash sale and ticket reservation engine designed to handle thousands of users attempting to purchase limited inventory simultaneously. The system prevents overselling, duplicate orders, and incorrect payment processing in high-demand scenarios like concert ticket launches, sports events, and flash sales.

## Architecture Overview
The system follows a microservices architecture with six independently deployable services:

1. [API Gateway](file:///c:/Users/hp/Desktop/FlashSaleEngine/api-gateway) (Spring Cloud Gateway) - Request routing, distributed tracing (`X-Correlation-ID`), and JWT security filtering
2. [Auth Service](file:///c:/Users/hp/Desktop/FlashSaleEngine/auth-service) - User registration, authentication, and JWT issuance (HS256)
3. [Catalog Service](file:///c:/Users/hp/Desktop/FlashSaleEngine/catalog-service) - Event and ticket type management (read-only for inventory)
4. [Reservation Service](file:///c:/Users/hp/Desktop/FlashSaleEngine/reservation-service) - Inventory management, reservations, distributed locking (Redis), and atomic Mongo updates
5. [Order Service](file:///c:/Users/hp/Desktop/FlashSaleEngine/order-service) - Order creation, state management, and saga coordination
6. [Payment Service](file:///c:/Users/hp/Desktop/FlashSaleEngine/payment-service) - Payment processing with idempotency and transactional outbox

## Infrastructure Components
- **MongoDB Replica Set (`rs0`)**: 3 nodes (`mongo1:27017`, `mongo2:27018:27017`, `mongo3:27019:27017`) supporting multi-document transactions. Initialized automatically via `mongo-init`, which waits for 1 PRIMARY and 2 SECONDARY nodes before completing.
- **Redis 7**: Distributed locking (`user-purchase:lock:*`, `inventory:lock:*`), inventory display caching, and rate limiting with append-only persistence.
- **Kafka & Zookeeper**: Event-driven communication with dual listeners:
  - Container network (`PLAINTEXT`): `kafka:9092`
  - Host machine (`PLAINTEXT_HOST`): `localhost:29092`
- **Docker Compose**: Canonical configuration maintained at [`infrastructure/docker-compose.yml`](file:///c:/Users/hp/Desktop/FlashSaleEngine/infrastructure/docker-compose.yml).

## Service Ports Allocation
| Service / Component | Container Port | Host Port | Purpose |
|---------------------|----------------|-----------|---------|
| [API Gateway](file:///c:/Users/hp/Desktop/FlashSaleEngine/api-gateway) | 8080 | 8080 | Central entry point & routing |
| [Auth Service](file:///c:/Users/hp/Desktop/FlashSaleEngine/auth-service) | 8081 | 8081 | Authentication & JWT |
| [Catalog Service](file:///c:/Users/hp/Desktop/FlashSaleEngine/catalog-service) | 8082 | 8082 | Events & ticket metadata |
| [Reservation Service](file:///c:/Users/hp/Desktop/FlashSaleEngine/reservation-service) | 8083 | 8083 | High-concurrency reservations & locks |
| [Order Service](file:///c:/Users/hp/Desktop/FlashSaleEngine/order-service) | 8084 | 8084 | Order management & Saga |
| [Payment Service](file:///c:/Users/hp/Desktop/FlashSaleEngine/payment-service) | 8085 | 8085 | Payment processing & outbox |
| MongoDB Primary (`mongo1`) | 27017 | 27017 | Primary DB node |
| MongoDB Secondary (`mongo2`) | 27017 | 27018 | Secondary DB node |
| MongoDB Secondary (`mongo3`) | 27017 | 27019 | Secondary DB node |
| Redis | 6379 | 6379 | Distributed locks & cache |
| Kafka (Internal / Host) | 9092 / 29092 | 9092 / 29092 | Kafka brokers |
| Zookeeper | 2181 | 2181 | Kafka coordination |

## Database Ownership
Each service connects to its dedicated logical database inside replica set `rs0`:
- `auth_db` — Auth Service (`users`)
- `catalog_db` — Catalog Service (`events`, `ticket_types`)
- `reservation_db` — Reservation Service (`inventory`, `reservations`, `outbox_events`)
- `order_db` — Order Service (`orders`, `processed_events`, `outbox_events`)
- `payment_db` — Payment Service (`payments`, `processed_events`, `outbox_events`)

---

## Local Development & Startup

### Prerequisites
- Docker & Docker Compose (Docker Desktop with WSL2 / Linux container engine running)
- Java 17+ (Eclipse Temurin 17 recommended)
- Maven 3.8+

### Canonical Command: Run Full Stack via Docker Compose
All services and infrastructure can be run together using the canonical compose file at `infrastructure/docker-compose.yml`:

```bash
docker compose -f infrastructure/docker-compose.yml up -d --build
```
Or from the `infrastructure` folder:
```bash
cd infrastructure
docker compose up -d --build
```

#### Startup Sequencing
Docker Compose enforces explicit health dependencies:
1. `mongo1`, `mongo2`, `mongo3`, `redis`, `zookeeper` start first.
2. `mongo-init` initiates replica set `rs0` on `mongo1:27017`, verifies election of 1 PRIMARY and 2 SECONDARIES, then completes successfully.
3. `kafka` starts and passes its readiness health check; `kafka-init-topics` provisions the event topics.
4. Core services (`auth-service`, `catalog-service`, `reservation-service`, `payment-service`) start once DB and message broker dependencies are healthy.
5. `order-service` starts once `reservation-service`, `catalog-service`, and `kafka` are healthy.
6. `api-gateway` starts once all upstream backend services pass their Actuator health checks.

#### Health Verification
Check running containers and health status:
```bash
docker compose -f infrastructure/docker-compose.yml ps
```
Inspect logs:
```bash
docker compose -f infrastructure/docker-compose.yml logs -f api-gateway
```
Query Actuator health endpoints:
```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health
curl http://localhost:8084/actuator/health
curl http://localhost:8085/actuator/health
```

#### Stopping the System
```bash
docker compose -f infrastructure/docker-compose.yml down
# Or to clear persistent volumes:
docker compose -f infrastructure/docker-compose.yml down -v
```

---

### Option B: Host-Run Development (Services on Host, Infra in Docker)
To run the infrastructure containers in Docker and run backend services locally on your host machine (IDE or terminal):

1. **Start only the infrastructure containers:**
   ```bash
   docker compose -f infrastructure/docker-compose.yml up -d mongo1 mongo2 mongo3 mongo-init redis zookeeper kafka kafka-init-topics
   ```

2. **MongoDB Host Access Options:**
   - **Recommended**: Map `mongo1` in your host `hosts` file (`C:\Windows\System32\drivers\etc\hosts` or `/etc/hosts`):
     ```
     127.0.0.1 mongo1 mongo2 mongo3
     ```
     This allows MongoDB replica set topology discovery (`rs0`) to resolve identically on both host and inside containers without any environment variable overrides.
   - **Alternative**: Override the connection URI per service using `directConnection=true` to connect directly to the primary on port 27017:
     ```bash
     $env:SPRING_DATA_MONGODB_URI="mongodb://localhost:27017/auth_db?directConnection=true"
     ```

3. **Default Port Resolution on Host:**
   All services default to localhost-friendly values when running outside Docker:
   - Gateway routes default to `http://localhost:8081` through `http://localhost:8085`.
   - Redis defaults to `localhost:6379`.
   - Kafka defaults to `localhost:29092` (connecting via Kafka's `PLAINTEXT_HOST` listener).
   - JWT Secret defaults to the shared 256-bit Base64 key `Zmxhc2gtc2FsZS1lbmdpbmUtc2VjcmV0LWtleS0zMmIh`.

4. **Run any service using Maven:**
   ```bash
   mvn spring-boot:run -f auth-service/pom.xml
   mvn spring-boot:run -f catalog-service/pom.xml
   mvn spring-boot:run -f reservation-service/pom.xml
   mvn spring-boot:run -f order-service/pom.xml
   mvn spring-boot:run -f payment-service/pom.xml
   mvn spring-boot:run -f api-gateway/pom.xml
   ```