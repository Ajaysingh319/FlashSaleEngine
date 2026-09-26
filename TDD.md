# Technical Design Document (TDD)

## High-Concurrency Flash Sale & Ticket Reservation Engine

**Version:** 2.0  
**Architecture:** Microservices  
**Backend:** Java 17 + Spring Boot 3.x  
**Frontend:** React + TypeScript  
**Gateway:** Spring Cloud Gateway  
**Database:** MongoDB  
**Cache / Distributed Lock:** Redis + Redisson  
**Messaging:** Apache Kafka  
**Build:** Maven  
**Containerization:** Docker + Docker Compose

---

# 1. Technical Objective

The system is composed of independently deployable services responsible for:

```text
Authentication
Catalog
Inventory/Reservation
Orders
Payments
```

An API Gateway provides the common entry point for frontend traffic.

The system must handle:

```text
5,000+ concurrent purchase attempts
```

while ensuring:

```text
No overselling
No duplicate logical orders
No duplicate payment processing
No permanent reservation leaks
```

---

# 2. Service Architecture

The system consists of six deployable applications:

```text
1. API Gateway
2. Auth Service
3. Catalog Service
4. Reservation Service
5. Order Service
6. Payment Service
```

Infrastructure:

```text
MongoDB
Redis
Kafka
```

---

# 3. Service Responsibilities

## 3.1 API Gateway

Technology:

```text
Spring Cloud Gateway
```

Responsibilities:

- Request routing
- Authentication filtering
- CORS
- Rate limiting where appropriate
- Request correlation ID
- Centralized entry point

The Gateway does not contain business logic.

Example:

```text
/api/v1/auth/**          → Auth Service
/api/v1/events/**        → Catalog Service
/api/v1/reservations/**  → Reservation Service
/api/v1/orders/**        → Order Service
/api/v1/payments/**      → Payment Service
/api/v1/admin/**         → Appropriate service
```

---

# 4. Auth Service

Responsibilities:

```text
Registration
Login
JWT
Refresh tokens
User profiles
Role management
```

Database:

```text
auth_db
```

Collection:

```text
users
```

Other services should not directly access `users`.

---

# 5. Catalog Service

Responsibilities:

```text
Events
Ticket types
Prices
Sale windows
Event status
Event browsing
```

Database:

```text
catalog_db
```

Collections:

```text
events
ticket_types
```

The Catalog Service does **not** modify reservation quantities.

Inventory belongs to Reservation Service.

---

# 6. Reservation Service

This is the most concurrency-sensitive service.

Responsibilities:

```text
Inventory
Reservation
Reservation expiry
Inventory release
Distributed locking
Purchase limits
```

Database:

```text
reservation_db
```

Collections:

```text
inventory
reservations
outbox_events
```

Redis responsibilities:

```text
Distributed locks
Rate limiting
Inventory-display cache
```

---

# 7. Order Service

Responsibilities:

```text
Order creation
Order state
Order history
Order cancellation
Payment status representation
```

Database:

```text
order_db
```

Collections:

```text
orders
outbox_events
processed_events
```

---

# 8. Payment Service

Responsibilities:

```text
Payment creation
Payment processing
Mock payment provider
Payment state
Payment transaction ID
Payment retries
```

Database:

```text
payment_db
```

Collections:

```text
payments
processed_events
outbox_events
```

---

# 9. Database Ownership

Each service owns its own logical MongoDB database.

```text
MongoDB
│
├── auth_db
│   └── users
│
├── catalog_db
│   ├── events
│   └── ticket_types
│
├── reservation_db
│   ├── inventory
│   ├── reservations
│   └── outbox_events
│
├── order_db
│   ├── orders
│   ├── processed_events
│   └── outbox_events
│
└── payment_db
    ├── payments
    ├── processed_events
    └── outbox_events
```

For local development these databases can exist inside one MongoDB replica set.

This gives database-per-service logical isolation without excessive local infrastructure.

---

# 10. Data Ownership Rule

A service must never directly query or modify another service's collection.

Bad:

```text
Order Service
     ↓
reservation_db.reservations
```

Correct:

```text
Order Service
     ↓ REST
Reservation Service
```

or:

```text
Order Service
     ↓ Kafka
Reservation-related event
```

This is one of the fundamental microservice boundaries.

---

# 11. Service Communication

There are two communication mechanisms.

## Synchronous

Use REST when an immediate response is required.

Example:

```text
Order Service
     ↓ HTTP
Reservation Service
```

## Asynchronous

Use Kafka when immediate response is not required.

Example:

```text
Order Service
     ↓
Kafka
     ↓
Payment Service
```

Rule:

```text
REST = request/response
Kafka = event-driven asynchronous workflow
```

---

# 12. Communication Matrix

| Producer | Consumer | Mechanism | Purpose |
|---|---|---|---|
| Gateway | All services | REST | External API routing |
| Order | Reservation | REST | Validate reservation |
| Order | Payment | Kafka | Request payment |
| Payment | Order | Kafka | Payment result |
| Payment | Reservation | Kafka | Release/confirm inventory |
| Reservation | Order | Kafka/REST as needed | Reservation state |
| Catalog | Other services | REST | Event metadata when needed |

Services must avoid excessive synchronous chains.

---

# 13. API Gateway Routing

Example routes:

```text
/api/v1/auth/**

    ↓

Auth Service :8081
```

```text
/api/v1/events/**

    ↓

Catalog Service :8082
```

```text
/api/v1/reservations/**

    ↓

Reservation Service :8083
```

```text
/api/v1/orders/**

    ↓

Order Service :8084
```

```text
/api/v1/payments/**

    ↓

Payment Service :8085
```

Gateway:

```text
:8080
```

---

# 14. Local Port Allocation

```text
API Gateway          8080
Auth Service         8081
Catalog Service      8082
Reservation Service  8083
Order Service        8084
Payment Service      8085
```

Infrastructure:

```text
MongoDB
Redis
Kafka
```

---

# 15. Reservation Service Data Model

## Inventory Document

Collection:

```text
inventory
```

Example:

```json
{
  "_id": "ticket_vip_123",
  "eventId": "evt_123",
  "ticketTypeId": "ticket_vip_123",
  "totalQuantity": 500,
  "availableQuantity": 350,
  "reservedQuantity": 100,
  "soldQuantity": 50,
  "updatedAt": "2026-10-01T10:00:00Z"
}
```

Invariant:

```text
totalQuantity =
availableQuantity +
reservedQuantity +
soldQuantity
```

---

# 16. Reservation Document

```json
{
  "_id": "res_123",
  "userId": "usr_123",
  "eventId": "evt_123",
  "ticketTypeId": "ticket_vip_123",
  "quantity": 2,
  "unitPrice": 4999.00,
  "amount": 9998.00,
  "status": "ACTIVE",
  "expiresAt": "2026-10-01T10:10:00Z",
  "createdAt": "2026-10-01T10:00:00Z",
  "updatedAt": "2026-10-01T10:00:00Z"
}
```

Indexes:

```text
userId
eventId
ticketTypeId
status + expiresAt
```

---

# 17. Order Service Data Model

Collection:

```text
orders
```

Example:

```json
{
  "_id": "ord_123",
  "userId": "usr_123",
  "reservationId": "res_123",
  "eventId": "evt_123",
  "eventName": "Delhi Music Festival",
  "ticketTypeId": "ticket_vip_123",
  "ticketTypeName": "VIP",
  "quantity": 2,
  "unitPrice": 4999.00,
  "amount": 9998.00,
  "status": "PENDING_PAYMENT",
  "paymentStatus": "PENDING",
  "createdAt": "2026-10-01T10:01:00Z",
  "updatedAt": "2026-10-01T10:01:00Z"
}
```

Unique index:

```text
reservationId
```

One reservation therefore cannot create two orders.

---

# 18. Payment Service Data Model

Collection:

```text
payments
```

Example:

```json
{
  "_id": "pay_123",
  "orderId": "ord_123",
  "amount": 9998.00,
  "provider": "MOCK",
  "transactionId": "txn_123",
  "status": "SUCCESS",
  "createdAt": "2026-10-01T10:02:00Z",
  "updatedAt": "2026-10-01T10:02:00Z"
}
```

Indexes:

```text
orderId UNIQUE
transactionId UNIQUE
```

---

# 19. Distributed Locking

The Reservation Service uses Redisson.

Lock:

```text
inventory:lock:{ticketTypeId}
```

Example:

```text
inventory:lock:ticket_vip_123
```

Only the Reservation Service needs this lock.

Other services cannot modify inventory.

---

# 20. Why Lock Per Ticket Type?

Avoid:

```text
inventory:global-lock
```

because that serializes all inventory operations.

Instead:

```text
inventory:lock:VIP-A
inventory:lock:VIP-B
inventory:lock:REGULAR-A
```

Independent ticket inventories can therefore process concurrently.

---

# 21. Reservation Concurrency Algorithm

Request:

```json
{
  "eventId": "evt_123",
  "ticketTypeId": "ticket_vip_123",
  "quantity": 2
}
```

Flow:

```text
1. Authenticate user
2. Validate Idempotency-Key
3. Check rate limit
4. Validate sale window
5. Acquire Redis ticket lock
6. Start MongoDB transaction
7. Perform atomic conditional inventory update
8. Create reservation
9. Create outbox event
10. Complete idempotency operation
11. Commit
12. Release Redis lock
13. Return response
```

---

# 22. MongoDB Atomic Inventory Update

The critical inventory operation is:

```javascript
{
  "_id": ticketTypeId,
  "availableQuantity": {
    "$gte": quantity
  }
}
```

with:

```javascript
{
  "$inc": {
    "availableQuantity": -quantity,
    "reservedQuantity": quantity
  }
}
```

This operation must use an atomic MongoDB update.

If no document matches:

```text
INVENTORY_UNAVAILABLE
```

---

# 23. Why Atomic Update Is the Core Protection

Avoid:

```text
find()
 ↓
check quantity
 ↓
modify object
 ↓
save()
```

because concurrent requests can read stale values.

Use:

```text
condition + atomic update
```

instead.

Example:

```text
Inventory = 1

Request A → succeeds
Request B → condition fails
Request C → condition fails
```

Result:

```text
Successful = 1
```

---

# 24. MongoDB Transaction

A reservation operation changes:

```text
inventory
reservation
outbox
idempotency
```

These should be part of one MongoDB transaction within Reservation Service.

```text
BEGIN
 ↓
Inventory update
 ↓
Reservation insert
 ↓
Outbox insert
 ↓
Idempotency update
 ↓
COMMIT
```

---

# 25. Lock + Transaction Relationship

The Reservation Service follows:

```text
Acquire Redis Lock
       ↓
MongoDB Transaction
       ↓
Atomic Inventory Update
       ↓
Reservation
       ↓
Outbox
       ↓
COMMIT
       ↓
Release Redis Lock
```

The lock must not be released until the transactional method has completed.

---

# 26. User/Event Purchase Lock

To enforce the maximum tickets per user/event under concurrent requests, the service may use:

```text
user-purchase:lock:{userId}:{eventId}
```

Recommended lock ordering:

```text
user/event lock
       ↓
ticket-type lock
       ↓
MongoDB transaction
```

Every code path that requires both locks must follow the same order.

---

# 27. Reservation Expiration

A Reservation Service scheduler finds:

```text
status = ACTIVE
expiresAt < currentTime
```

The worker performs:

```text
Acquire ticket lock
 ↓
MongoDB transaction
 ↓
Verify ACTIVE state
 ↓
Release reserved inventory
 ↓
Mark EXPIRED
 ↓
Create ReservationExpired event
 ↓
COMMIT
 ↓
Release lock
```

The operation is idempotent.

If another worker already changed:

```text
ACTIVE → EXPIRED
```

the second worker does nothing.

---

# 28. Kafka Topics

Recommended topics:

```text
order.created
payment.requested
payment.completed
payment.failed
reservation.expired
reservation.cancelled
reservation.confirmed
inventory.released
order.confirmed
```

---

# 29. Kafka Event Envelope

Every event should use a common envelope:

```json
{
  "eventId": "evt-msg-123",
  "eventType": "PAYMENT_SUCCEEDED",
  "timestamp": "2026-10-01T10:02:00Z",
  "aggregateType": "ORDER",
  "aggregateId": "ord_123",
  "payload": {
    "orderId": "ord_123",
    "paymentId": "pay_123",
    "amount": 9998.00
  }
}
```

---

# 30. Partition Keys

Order-related events:

```text
key = orderId
```

Inventory-related events:

```text
key = ticketTypeId
```

This helps preserve ordering for the same aggregate.

---

# 31. Order Creation Flow

Client:

```text
POST /api/v1/orders
```

Order Service:

```text
1. Authenticate user
2. Check Idempotency-Key
3. Call Reservation Service
4. Validate reservation ownership
5. Validate ACTIVE state
6. Validate expiry
7. Create order
8. Save PENDING_PAYMENT state
9. Create OrderCreated outbox event
10. Return order
```

---

# 32. Reservation Validation Between Services

Order Service must not access the Reservation database directly.

Instead:

```text
Order Service
      ↓
GET /internal/v1/reservations/{reservationId}
      ↓
Reservation Service
```

Response:

```json
{
  "reservationId": "res_123",
  "userId": "usr_123",
  "status": "ACTIVE",
  "expiresAt": "2026-10-01T10:10:00Z",
  "quantity": 2,
  "amount": 9998.00
}
```

Internal endpoints should not be exposed directly to the public internet.

---

# 33. Order → Payment Flow

Order Service publishes:

```text
ORDER_CREATED
```

Outbox:

```text
MongoDB
 ↓
Outbox Publisher
 ↓
Kafka
 ↓
payment.requested
```

Payment Service consumes the event.

---

# 34. Payment Service Flow

```text
payment.requested
        ↓
Check event idempotency
        ↓
Create/find payment
        ↓
Process mock payment
        ↓
SUCCESS / FAILURE
        ↓
Create outbox event
        ↓
Publish to Kafka
```

---

# 35. Payment Success Event

Payment Service publishes:

```text
PAYMENT_SUCCEEDED
```

Consumers:

```text
Order Service
Reservation Service
```

Order Service:

```text
PENDING_PAYMENT
      ↓
CONFIRMED
```

Reservation Service:

```text
ACTIVE
      ↓
CONFIRMED

reserved -= quantity
sold += quantity
```

Both state transitions are independent but driven by the same event.

---

# 36. Payment Failure Event

Payment Service publishes:

```text
PAYMENT_FAILED
```

Order Service:

```text
PENDING_PAYMENT
      ↓
PAYMENT_FAILED
```

Reservation Service:

```text
ACTIVE
      ↓
CANCELLED

reserved -= quantity
available += quantity
```

This represents a distributed Saga.

---

# 37. Saga Pattern

The purchase workflow spans several independent services.

There is no single transaction across:

```text
Reservation DB
Order DB
Payment DB
```

Instead:

```text
Reservation
    ↓
Order
    ↓
Payment
    ↓
Payment Result
    ↓
Order + Reservation
```

Failures are compensated through events.

Example:

```text
Reserve Inventory
       ↓
Create Order
       ↓
Payment Failed
       ↓
Release Inventory
```

This is a **choreography-style Saga**.

---

# 38. Why Not a Distributed Database Transaction?

Do not attempt:

```text
Transaction
 ↓
Reservation DB
 ↓
Order DB
 ↓
Payment DB
 ↓
Commit
```

because each service owns its database.

The microservice architecture instead uses:

```text
Local transactions
+
Kafka events
+
Compensating actions
```

---

# 39. Transactional Outbox Per Service

Each service that publishes events gets its own outbox collection.

Example:

```text
reservation_db.outbox_events
order_db.outbox_events
payment_db.outbox_events
```

A service transaction writes:

```text
business state
+
outbox event
```

atomically.

Then an outbox publisher sends the event to Kafka.

---

# 40. Outbox Flow

```text
Local MongoDB Transaction
        │
        ├── Business State
        └── Outbox Event
                ↓
             COMMIT
                ↓
         Outbox Publisher
                ↓
              Kafka
```

This avoids the failure window:

```text
DB commit succeeded
Kafka publish failed
```

because the event remains persisted.

---

# 41. Duplicate Event Handling

An outbox publisher may publish the same event more than once in rare crash windows.

Therefore every consumer must be idempotent.

Each consumer stores:

```text
processed_events
```

Example:

```json
{
  "_id": "event-123",
  "processedAt": "2026-10-01T10:03:00Z"
}
```

If:

```text
event-123
```

arrives again:

```text
Already processed
→ Ignore
```

---

# 42. Payment Idempotency

Unique constraints:

```text
orderId UNIQUE
transactionId UNIQUE
```

and event processing idempotency ensure:

```text
One order
→ one logical payment
```

---

# 43. Order Idempotency

Order creation requires:

```http
Idempotency-Key: <UUID>
```

Order Service maintains:

```text
idempotency_keys
```

with:

```text
(userId + key) UNIQUE
```

Same key + same request:

```text
Return previous result
```

Same key + different request:

```text
409 CONFLICT
```

---

# 44. API Design

## Gateway Public APIs

### Auth

```http
POST /api/v1/auth/register
POST /api/v1/auth/login
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
```

### Users

```http
GET /api/v1/users/me
PUT /api/v1/users/me
```

### Catalog

```http
GET /api/v1/events
GET /api/v1/events/{eventId}
POST /api/v1/events
PUT /api/v1/events/{eventId}
POST /api/v1/events/{eventId}/cancel
```

### Ticket Types

```http
GET    /api/v1/events/{eventId}/ticket-types
POST   /api/v1/events/{eventId}/ticket-types
PUT    /api/v1/ticket-types/{ticketTypeId}
DELETE /api/v1/ticket-types/{ticketTypeId}
```

### Inventory

```http
GET /api/v1/events/{eventId}/inventory
GET /api/v1/ticket-types/{ticketTypeId}/inventory
```

### Reservations

```http
POST /api/v1/reservations
GET  /api/v1/reservations/{reservationId}
GET  /api/v1/reservations/me
POST /api/v1/reservations/{reservationId}/cancel
```

### Orders

```http
POST /api/v1/orders
GET  /api/v1/orders/{orderId}
GET  /api/v1/orders/me
POST /api/v1/orders/{orderId}/cancel
```

### Payments

```http
POST /api/v1/orders/{orderId}/payment
GET  /api/v1/payments/{paymentId}
```

### Admin

```http
GET /api/v1/admin/orders
GET /api/v1/admin/reservations/active
GET /api/v1/admin/events/{eventId}/statistics
GET /api/v1/admin/statistics
```

---

# 45. Internal APIs

Services may expose internal endpoints that are not routed publicly.

Examples:

```http
GET /internal/v1/reservations/{reservationId}
GET /internal/v1/reservations/{reservationId}/validate
```

These should be protected through service authentication or an internal network policy.

---

# 46. Reservation API Example

```http
POST /api/v1/reservations
Authorization: Bearer <JWT>
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
```

Request:

```json
{
  "eventId": "evt_123",
  "ticketTypeId": "ticket_vip_123",
  "quantity": 2
}
```

Response:

```json
{
  "reservationId": "res_123",
  "status": "ACTIVE",
  "quantity": 2,
  "amount": 9998.00,
  "expiresAt": "2026-10-01T10:10:00Z"
}
```

---

# 47. Order API Example

```http
POST /api/v1/orders
Authorization: Bearer <JWT>
Idempotency-Key: <UUID>
```

Request:

```json
{
  "reservationId": "res_123"
}
```

Response:

```json
{
  "orderId": "ord_123",
  "status": "PENDING_PAYMENT",
  "amount": 9998.00
}
```

---

# 48. Payment API Example

```http
POST /api/v1/orders/{orderId}/payment
Authorization: Bearer <JWT>
Idempotency-Key: <UUID>
```

Request:

```json
{
  "paymentMethod": "MOCK_CARD"
}
```

Response:

```json
{
  "paymentId": "pay_123",
  "status": "PROCESSING"
}
```

Payment continues asynchronously after this response.

---

# 49. Standard Error Response

```json
{
  "success": false,
  "error": {
    "code": "INVENTORY_UNAVAILABLE",
    "message": "Requested inventory is no longer available."
  },
  "timestamp": "2026-10-01T10:05:00Z",
  "traceId": "abc123"
}
```

---

# 50. Important Error Codes

```text
INVALID_REQUEST
UNAUTHORIZED
FORBIDDEN
EVENT_NOT_FOUND
EVENT_NOT_ON_SALE
TICKET_TYPE_NOT_FOUND
INVENTORY_UNAVAILABLE
RESERVATION_NOT_FOUND
RESERVATION_EXPIRED
RESERVATION_NOT_OWNED
ORDER_NOT_FOUND
ORDER_ALREADY_EXISTS
PAYMENT_FAILED
PAYMENT_ALREADY_PROCESSED
IDEMPOTENCY_KEY_REUSED
RATE_LIMIT_EXCEEDED
SERVICE_UNAVAILABLE
```

---

# 51. Authentication Architecture

JWT is generated by Auth Service.

Gateway validates or forwards authenticated identity.

JWT:

```json
{
  "sub": "usr_123",
  "role": "CUSTOMER",
  "exp": 123456789
}
```

Services should not trust arbitrary user IDs supplied in request bodies.

User identity comes from the authenticated context.

---

# 52. Redis Usage

Redis is used for:

```text
1. Distributed locking
2. Rate limiting
3. Read caching
```

Example keys:

```text
inventory:lock:{ticketTypeId}

user-purchase:lock:{userId}:{eventId}

rate_limit:{userId}:{endpoint}

event:{eventId}
```

---

# 53. Redis Cache Rule

Cached availability is for display only.

Do not:

```text
React
 ↓
Redis inventory = 2
 ↓
Approve booking
```

Instead:

```text
React
 ↓
Reservation Service
 ↓
MongoDB authoritative inventory
```

---

# 54. Rate Limiting Strategy

Reservation Service can enforce:

```text
10 reservation requests/minute/user
```

Payment Service:

```text
5 payment-initiation requests/minute/user
```

Exact limits can be configured through environment variables.

---

# 55. Service-Level Configuration

Each service should have its own:

```text
application.yml
```

Example:

```text
auth-service/
catalog-service/
reservation-service/
order-service/
payment-service/
api-gateway/
```

Every service gets its own port and configuration.

---

# 56. Repository Structure

Recommended top-level structure:

```text
flash-sale-engine/
│
├── frontend/
│
├── api-gateway/
│
├── auth-service/
│
├── catalog-service/
│
├── reservation-service/
│
├── order-service/
│
├── payment-service/
│
├── infrastructure/
│   └── docker-compose.yml
│
└── README.md
```

Each backend service is a separate Maven/Spring Boot project.

---

# 57. Reservation Service Structure

```text
reservation-service/
├── pom.xml
└── src/main/java/com/flashsale/reservation/
    ├── controller/
    ├── dto/
    ├── document/
    ├── repository/
    ├── service/
    ├── lock/
    ├── scheduler/
    ├── kafka/
    ├── outbox/
    ├── idempotency/
    └── config/
```

---

# 58. Order Service Structure

```text
order-service/
├── pom.xml
└── src/main/java/com/flashsale/order/
    ├── controller/
    ├── dto/
    ├── document/
    ├── repository/
    ├── service/
    ├── kafka/
    ├── outbox/
    ├── idempotency/
    └── config/
```

---

# 59. Payment Service Structure

```text
payment-service/
├── pom.xml
└── src/main/java/com/flashsale/payment/
    ├── controller/
    ├── dto/
    ├── document/
    ├── repository/
    ├── service/
    ├── consumer/
    ├── producer/
    ├── outbox/
    └── config/
```

---

# 60. Docker Compose

Local environment:

```text
api-gateway
auth-service
catalog-service
reservation-service
order-service
payment-service
mongodb
redis
kafka
```

Optional:

```text
prometheus
grafana
```

---

# 61. MongoDB Local Architecture

Run one MongoDB replica set:

```text
MongoDB rs0
```

with logical databases:

```text
auth_db
catalog_db
reservation_db
order_db
payment_db
```

This is suitable for local development.

Future production deployment can use separate MongoDB clusters or deployments per service.

---

# 62. Why a Replica Set?

MongoDB transactions require a transaction-capable deployment.

The local environment therefore uses:

```text
MongoDB Replica Set
```

rather than a simple standalone MongoDB instance.

---

# 63. Horizontal Scaling

Example:

```text
API Gateway × 2

Auth Service × 2

Catalog Service × 2

Reservation Service × 5

Order Service × 3

Payment Service × 4
```

The Reservation Service can scale independently during a flash sale.

Kafka consumers can also scale according to partition count.

---

# 64. Kafka Consumer Scaling

Example:

```text
payment.requested

Partition 0 ─→ Payment Consumer 1
Partition 1 ─→ Payment Consumer 2
Partition 2 ─→ Payment Consumer 3
```

Consumers belong to one consumer group.

More consumers can process more partitions concurrently.

---

# 65. Failure Handling

## Reservation Service Crash

Before transaction commit:

```text
Mongo transaction rolls back
```

After transaction commit:

```text
Reservation exists
Outbox exists
```

so processing can continue.

---

## Order Service Crash

Same principle:

```text
Order state
+
Outbox event
```

are committed together.

---

## Payment Service Crash

Kafka retains the unacknowledged message.

The consumer can process it after restart.

---

## Kafka Failure

Outbox events remain persisted until Kafka becomes available.

---

## Redis Failure

Reservation creation fails safely instead of bypassing distributed coordination.

---

# 66. Consumer Retry Strategy

Transient failures:

```text
Retry
 ↓
Retry
 ↓
Retry
 ↓
DLT
```

Permanent failures should not be retried indefinitely.

---

# 67. Dead Letter Topics

Examples:

```text
payment.requested.DLT
payment.completed.DLT
order.created.DLT
```

Failed messages can later be inspected.

---

# 68. Distributed Tracing

Every request/event should carry:

```text
traceId
```

Example:

```text
React
 ↓
Gateway
 ↓
Reservation Service
 ↓
MongoDB
 ↓
Outbox
 ↓
Kafka
 ↓
Payment Service
```

The same trace ID helps follow the purchase through the system.

---

# 69. Observability

Recommended:

```text
Spring Boot Actuator
Micrometer
Prometheus
Grafana
```

Metrics:

```text
reservation.success.count
reservation.failure.count
inventory.conflict.count
order.created.count
order.confirmed.count
payment.success.count
payment.failure.count
kafka.consumer.lag
redis.lock.latency
mongo.operation.latency
```

---

# 70. Testing Strategy

Testing layers:

```text
Unit Tests
Integration Tests
Contract Tests
Concurrency Tests
Load Tests
End-to-End Tests
```

---

# 71. Integration Testing

Use Testcontainers for:

```text
MongoDB
Redis
Kafka
```

Each service should be tested against the infrastructure it actually depends on.

---

# 72. Service Contract Testing

Important contracts:

```text
Order Service ↔ Reservation Service
```

and event schemas:

```text
ORDER_CREATED
PAYMENT_SUCCEEDED
PAYMENT_FAILED
```

Payload changes must remain backward-compatible where practical.

---

# 73. Concurrency Test

Example:

```text
Inventory = 10
Concurrent users = 1,000
```

Expected:

```text
Successful allocations <= 10
```

Verify:

```text
available >= 0
reserved >= 0
sold >= 0
```

and:

```text
total = available + reserved + sold
```

---

# 74. Idempotency Test

Send:

```text
100 identical reservation requests
same user
same Idempotency-Key
```

Expected:

```text
one logical reservation
```

---

# 75. Duplicate Kafka Event Test

Send the same:

```text
PAYMENT_SUCCEEDED
```

event twice.

Expected:

```text
Order confirmed once
Inventory moved from reserved to sold once
```

---

# 76. Saga Failure Test

Simulate:

```text
Reservation created
 ↓
Order created
 ↓
Payment failed
```

Expected:

```text
Order = PAYMENT_FAILED
Reservation = CANCELLED
Available inventory restored
```

---

# 77. Load Testing

Scenario 1:

```text
Inventory = 1,000
Requests = 1,000
```

Scenario 2:

```text
Inventory = 1,000
Requests = 5,000
```

Scenario 3:

```text
Inventory = 10
Requests = 5,000
```

The third scenario should generate extreme contention.

---

# 78. Performance Measurements

Capture:

```text
RPS
P50
P95
P99
Error Rate
MongoDB latency
Redis latency
Kafka lag
CPU
Memory
```

Most importantly:

```text
Overselling = 0
```

---

# 79. Security Architecture

```text
React
 ↓
API Gateway
 ↓
JWT validation
 ↓
Service
```

Additional controls:

```text
Rate limiting
Input validation
Role-based access
BCrypt
CORS
Secure headers
```

---

# 80. Internal Service Security

Public clients should communicate through:

```text
API Gateway
```

Internal service APIs should not be unnecessarily exposed publicly.

For MVP, internal communication can use:

```text
private Docker network
+
service authentication
```

---

# 81. Service Discovery

Do not introduce a dedicated service registry for the MVP.

Docker Compose service names can be used for local service-to-service communication:

```text
http://reservation-service:8083
http://order-service:8084
http://payment-service:8085
```

This keeps local development manageable.

---

# 82. Configuration Management

Each service owns its own configuration.

Common configuration:

```text
MongoDB URI
Kafka bootstrap servers
Redis host
JWT settings
```

Secrets must be provided through environment variables.

Do not hardcode:

```text
JWT_SECRET
MongoDB passwords
Payment secrets
```

---

# 83. Service Startup Dependencies

Startup order:

```text
MongoDB
Redis
Kafka
   ↓
Auth
Catalog
Reservation
Order
Payment
   ↓
API Gateway
   ↓
React
```

Docker Compose health checks should be used where practical.

---

# 84. Recommended Development Order

## Day 1

```text
Create six projects
Docker
MongoDB
Redis
Kafka
Gateway skeleton
```

## Day 2

```text
Auth Service
Catalog Service
Basic APIs
```

## Day 3

```text
Reservation Service
MongoDB atomic inventory
Redis locking
Reservations
```

## Day 4

```text
Idempotency
Order Service
Reservation → Order communication
```

## Day 5

```text
Kafka
Payment Service
Outbox
Payment success/failure
Saga compensation
```

## Day 6

```text
React
Gateway integration
Order/payment UI
Admin UI
```

## Day 7

```text
Concurrency tests
Load tests
Docker polish
Swagger
README
Architecture diagram
```

---

# 85. What Not to Implement Initially

Do not add these during the MVP:

```text
Kubernetes
Eureka
Config Server
Kubernetes Ingress
Service Mesh
Multi-region
Distributed tracing infrastructure
Complex seat locking
Real payment settlement
Notification microservice
```

Each can be added later.

---

# 86. Final End-to-End Architecture

```text
                              ┌───────────────────┐
                              │   React Client    │
                              └─────────┬─────────┘
                                        │
                                        ▼
                           ┌────────────────────────┐
                           │     API Gateway         │
                           │  Spring Cloud Gateway  │
                           └───────────┬────────────┘
                                       │
             ┌─────────────────────────┼──────────────────────────┐
             │                         │                          │
             ▼                         ▼                          ▼
      ┌─────────────┐         ┌─────────────┐          ┌──────────────────┐
      │ Auth        │         │ Catalog     │          │ Reservation      │
      │ Service     │         │ Service     │          │ Service          │
      └──────┬──────┘         └──────┬──────┘          └────────┬─────────┘
             │                       │                           │
         auth_db                 catalog_db                  Redis
                                                                 │
                                                             reservation_db
                                                                 │
                                                                 │
                              ┌──────────────────────────────────┘
                              │
                              ▼
                            Kafka
                              │
                     ┌────────┴────────┐
                     │                 │
                     ▼                 ▼
              ┌─────────────┐   ┌───────────────┐
              │ Order       │   │ Payment       │
              │ Service     │   │ Service       │
              └──────┬──────┘   └───────┬───────┘
                     │                   │
                  order_db            payment_db
                     │                   │
                     └─────────┬─────────┘
                               │
                              Kafka
                               │
                    ┌──────────┴──────────┐
                    ▼                     ▼
              Reservation Service     Order Service
              inventory transition   state transition
```

---

# 87. Final Reservation Path

The most important technical path is:

```text
5,000 concurrent users
           ↓
API Gateway
           ↓
Reservation Service
           ↓
Rate Limit
           ↓
Idempotency
           ↓
Redis Distributed Lock
           ↓
MongoDB Transaction
           ↓
Atomic Conditional Inventory Update
           ↓
Create Reservation
           ↓
Create Outbox Event
           ↓
COMMIT
           ↓
Release Lock
```

---

# 88. Final Payment Path

```text
Order Service
      ↓
OrderCreated
      ↓
Outbox
      ↓
Kafka
      ↓
Payment Service
      ↓
Mock Payment Provider
      │
      ├──────────────┐
      ▼              ▼
SUCCESS            FAILURE
      │              │
      ▼              ▼
PaymentSucceeded   PaymentFailed
      │              │
      └───────┬──────┘
              ▼
            Kafka
         ┌────┴────┐
         ▼         ▼
   Order Service  Reservation Service
         │         │
         ▼         ▼
       Update    Confirm/Release
        Order      Inventory
```

---

# 89. Final Correctness Model

The system's most important guarantees are:

```text
1. MongoDB atomic inventory update
   → prevents overselling

2. Redis distributed lock
   → coordinates concurrent application instances

3. MongoDB transaction
   → keeps service-local state consistent

4. Idempotency keys
   → prevent duplicate logical requests

5. Unique indexes
   → enforce critical uniqueness

6. Kafka
   → asynchronous inter-service communication

7. Transactional outbox
   → prevents losing committed events

8. Consumer idempotency
   → prevents duplicate event side effects

9. Saga compensation
   → releases inventory after downstream failure
```

---

# 90. Final Architecture Principle

The system follows this separation:

```text
API Gateway
    ↓
Routes requests

Auth Service
    ↓
Owns identity

Catalog Service
    ↓
Owns event/catalog information

Reservation Service
    ↓
Owns inventory + reservations
    ↓
Uses Redis + MongoDB

Order Service
    ↓
Owns orders

Payment Service
    ↓
Owns payments

Kafka
    ↓
Asynchronous communication

MongoDB
    ↓
Persistent source of truth per service

Redis
    ↓
Distributed coordination + caching + rate limiting
```

The core architectural idea is:

> **Each microservice owns its data and business logic, REST is used for immediate request/response interactions, and Kafka is used for asynchronous cross-service workflows.**

The critical flash-sale guarantee remains:

```text
Successful ticket allocation <= actual inventory
```

and the critical reliability guarantee remains:

```text
One logical purchase operation
→ one logical order
→ one logical payment
```