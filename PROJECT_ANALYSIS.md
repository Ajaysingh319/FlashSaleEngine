# Flash Sale & Ticket Reservation Engine - System Analysis

## 1. Understanding of the Complete System

### Core Purpose
The system is a high-concurrency flash sale and ticket reservation engine designed to handle thousands of users attempting to purchase limited inventory simultaneously. It prevents overselling, duplicate orders, and incorrect payment processing in high-demand scenarios like concert ticket launches, sports events, and flash sales.

### Key Architectural Principles
- **Microservices Architecture**: 6 independently deployable services
- **Database Per Service**: MongoDB with logical database isolation
- **Distributed Coordination**: Redis for locking and rate limiting
- **Asynchronous Communication**: Apache Kafka for inter-service workflows
- **API Gateway**: Spring Cloud Gateway for request routing and cross-cutting concerns
- **Frontend**: React + TypeScript
- **Backend**: Java 17 + Spring Boot

### Service Responsibilities

#### 1. Auth Service
- Registration, login, JWT token generation
- Refresh tokens and user profile management
- Role-based access control (CUSTOMER/ADMIN)
- Owns: auth_db.users collection

#### 2. Catalog Service
- Event management (creation, updates, cancellation)
- Ticket type configuration (VIP, Premium, Regular, etc.)
- Price and sale window management
- Event browsing and filtering capabilities
- Owns: catalog_db.events, catalog_db.ticket_types collections
- **Important**: Does NOT modify reservation quantities - inventory belongs to Reservation Service

#### 3. Reservation Service (Most Critical)
- Inventory management and tracking
- Ticket reservation with TTL (time-to-live)
- Reservation expiry and inventory release
- Distributed locking using Redis Redisson
- Purchase limit enforcement (default: 4 tickets/user/event)
- Owns: reservation_db.inventory, reservation_db.reservations, reservation_db.outbox_events collections
- Uses MongoDB transactions for atomic operations

#### 4. Order Service
- Order creation from valid reservations
- Order state management (PENDING_PAYMENT, CONFIRMED, PAYMENT_FAILED, etc.)
- Order history and retrieval
- Order cancellation functionality
- Owns: order_db.orders, order_db.outbox_events, order_db.processed_events collections

#### 5. Payment Service
- Payment processing with mock provider
- Payment state tracking
- Payment retries and failure handling
- Owns: payment_db.payments, payment_db.outbox_events, payment_db.processed_events collections

#### 6. API Gateway
- Request routing to appropriate services
- Authentication filtering and JWT validation
- CORS handling
- Rate limiting where appropriate
- Request correlation ID for tracing
- No business logic - purely infrastructure

### Critical Concurrency Mechanisms

#### Redis Distributed Locking
- Per ticket-type locks: `inventory:lock:{ticketTypeId}`
- Optional user/event locks for purchase limits: `user-purchase:lock:{userId}:{eventId}`
- Lock ordering: user/event lock → ticket-type lock → MongoDB transaction
- Prevents race conditions across multiple service instances

#### MongoDB Atomic Inventory Update
- Core protection against overselling:
  ```javascript
  // Find condition
  { "_id": ticketTypeId, "availableQuantity": { "$gte": quantity } }
  
  // Update operation
  { 
    "$inc": { 
      "availableQuantity": -quantity,
      "reservedQuantity": quantity 
    } 
  }
  ```
- If condition fails (insufficient inventory), update does not occur
- Guarantees: Successful allocations ≤ actual inventory

#### MongoDB Transactions
- Reservation Service uses transactions for:
  1. Inventory update (atomic conditional)
  2. Reservation document creation
  3. Outbox event creation
  4. Idempotency key recording
- All-or-nothing guarantee for service-local operations

#### Idempotency Support
- Critical APIs support `Idempotency-Key` header
- Same key + same request → returns previous result
- Same key + different request → 409 CONFLICT
- Prevents duplicate operations from retries

#### Transactional Outbox Pattern
- Each service has outbox collection in its own database
- Business state + outbox event saved in same MongoDB transaction
- Outbox publisher sends events to Kafka
- Prevents lost events when DB commit succeeds but Kafka publish fails

#### Saga Pattern (Choreography-Based)
- Purchase workflow spans multiple services without distributed transactions
- Events drive state transitions:
  - OrderCreated → Payment requested
  - PaymentSucceeded → Order confirmed + Inventory moved to sold
  - PaymentFailed → Order failed + Inventory released
- Compensating actions handle failures (e.g., payment failure releases inventory)

### Data Flow for Ticket Purchase

1. **Reservation Request**
   - Client → API Gateway → Reservation Service
   - Rate limit check → Idempotency validation → Redis lock acquisition
   - MongoDB transaction: 
     - Atomic inventory update (conditional)
     - Reservation document creation
     - Outbox event creation (ReservationCreated)
   - Commit transaction → Release lock → Return reservation

2. **Order Creation**
   - Order Service consumes ReservationCreated event
   - Validates reservation ownership and status
   - Creates order (PENDING_PAYMENT)
   - Publishes OrderCreated event to Kafka

3. **Payment Processing**
   - Payment Service consumes OrderCreated event
   - Processes mock payment (SUCCESS/FAILED/TIMEOUT)
   - Publishes PaymentSucceeded or PaymentFailed event

4. **State Updates**
   - Order Service: Updates order status based on payment result
   - Reservation Service: 
     - PaymentSucceeded: Moves reserved → sold inventory
     - PaymentFailed: Releases reserved → available inventory
     - Reservation expired: Releases reserved → available inventory

### Key System Guarantees

1. **No Overselling**: Successful ticket allocation ≤ actual inventory
2. **No Duplicate Orders**: One logical request ≠ multiple successful bookings
3. **Inventory Consistency**: total = available + reserved + sold always holds
4. **Expired Reservations**: Inventory released when TTL expires
5. **Failed Payment Recovery**: Inventory released on payment failure
6. **Duplicate Event Safety**: Idempotent consumers prevent duplicate side effects
7. **Failure Resilience**: System survives service crashes, network partitions, etc.

### Non-Functional Requirements

- **Performance**: Target 5,000+ concurrent purchase attempts
  - P50 < 100ms, P95 < 300ms, P99 < 500ms
- **Scalability**: Independent horizontal scaling of services
  - Reservation Service can scale independently during flash sales
- **Reliability**: Survives backend failures, Redis/Kafka interruptions, payment failures
- **Security**: JWT, BCrypt, role-based auth, rate limiting, input validation, CORS, secure headers
- **Observability**: Metrics for reservation success/failure, inventory conflicts, Kafka lag, DB/Redis latency

### Infrastructure Components
- MongoDB (replica set for transactions)
- Redis (distributed locking, rate limiting, caching)
- Kafka (asynchronous messaging)
- Docker Compose (local development orchestration)
- Spring Boot Actuator, Micrometer, Prometheus, Grafana (monitoring)

## 2. Final Project Folder Structure

```
flash-sale-engine/
│
├── frontend/                     # React + TypeScript application
│   ├── public/
│   ├── src/
│   │   ├── components/
│   │   ├── pages/
│   │   ├── services/
│   │   ├── hooks/
│   │   ├── utils/
│   │   ├── context/
│   │   ├── assets/
│   │   ├── App.tsx
│   │   └── index.tsx
│   ├── package.json
│   ├── tsconfig.json
│   └── README.md
│
├── api-gateway/                  # Spring Cloud Gateway
│   ├── src/
│   │   └── main/
│   │       ├── java/com/flashsale/gateway/
│   │       │   ├── config/
│   │       │   └── filter/
│   │       └── resources/
│   │       │   └── application.yml
│   └── pom.xml
│
├── auth-service/                 # Authentication service
│   ├── src/
│   │   └── main/
│   │       ├── java/com/flashsale/auth/
│   │       │   ├── controller/
│   │       │   │   ├── AuthController.java
│   │       │   │   ├── UserController.java
│   │       │   │   └── AdminController.java
│   │       │   ├── dto/
│   │       │   │   ├── RegisterRequest.java
│   │       │   │   ├── LoginRequest.java
│   │       │   │   ├── RefreshTokenRequest.java
│   │       │   │   └── UserResponse.java
│   │       │   ├── model/
│   │       │   │   └── User.java
│   │       │   ├── repository/
│   │       │   │   └── UserRepository.java
│   │       │   ├── service/
│   │       │   │   ├── AuthService.java
│   │       │   │   ├── UserService.java
│   │       │   │   ├── JwtService.java
│   │       │   │   └── PasswordEncoderService.java
│   │       │   ├── config/
│   │       │   │   ├── SecurityConfig.java
│   │       │   │   └── JwtConfig.java
│   │       │   └── exception/
│   │       │       ├── GlobalExceptionHandler.java
│   │       │       └── AuthException.java
│   │       └── resources/
│   │       │   └── application.yml
│   └── pom.xml
│
├── catalog-service/              # Catalog service
│   ├── src/
│   │   └── main/
│   │       ├── java/com/flashsale/catalog/
│   │       │   ├── controller/
│   │       │   │   ├── EventController.java
│   │       │   │   └── TicketTypeController.java
│   │       │   ├── dto/
│   │       │   │   ├── EventRequest.java
│   │       │   │   ├── EventResponse.java
│   │       │   │   ├── TicketTypeRequest.java
│   │       │   │   └── TicketTypeResponse.java
│   │       │   ├── model/
│   │       │   │   ├── Event.java
│   │       │   │   └── TicketType.java
│   │       │   ├── repository/
│   │       │   │   ├── EventRepository.java
│   │       │   │   └── TicketTypeRepository.java
│   │       │   ├── service/
│   │       │   │   ├── EventService.java
│   │       │   │   └── TicketTypeService.java
│   │       │   └── config/
│   │       │       └── MongoConfig.java
│   │       └── resources/
│   │       │   └── application.yml
│   └── pom.xml
│
├── reservation-service/          # Most critical service
│   ├── src/
│   │   └── main/
│   │       ├── java/com/flashsale/reservation/
│   │       │   ├── controller/
│   │       │   │   └── ReservationController.java
│   │       │   ├── dto/
│   │       │   │   ├── ReservationRequest.java
│   │       │   │   ├── ReservationResponse.java
│   │       │   │   └── InventoryResponse.java
│   │       │   ├── model/
│   │       │   │   ├── Inventory.java
│   │       │   │   ├── Reservation.java
│   │       │   │   └── OutboxEvent.java
│   │       │   ├── repository/
│   │       │   │   ├── InventoryRepository.java
│   │       │   │   ├── ReservationRepository.java
│   │       │   │   └── OutboxEventRepository.java
│   │       │   ├── service/
│   │       │   │   ├── InventoryService.java
│   │       │   │   ├── ReservationService.java
│   │       │   │   ├── IdempotencyService.java
│   │       │   │   └── LockService.java
│   │       │   ├── lock/
│   │       │   │   ├── RedissonLock.java
│   │       │   │   └── DistributedLockManager.java
│   │       │   ├── scheduler/
│   │       │   │   └── ReservationExpirationScheduler.java
│   │       │   ├── kafka/
│   │       │   │   ├── producer/
│   │       │   │   │   └── ReservationEventProducer.java
│   │       │   │   └── consumer/
│   │       │   │       ├── OrderCreatedConsumer.java
│   │       │   │       ├── PaymentSucceededConsumer.java
│   │       │   │       └── PaymentFailedConsumer.java
│   │       │   ├── outbox/
│   │       │   │   ├── OutboxEventPublisher.java
│   │       │   │   └── OutboxEventListener.java
│   │       │   ├── idempotency/
│   │       │   │   ├── IdempotencyRepository.java
│   │       │   │   └── IdempotencyService.java
│   │       │   └── config/
│   │       │       ├── MongoConfig.java
│   │       │       ├── RedisConfig.java
│   │       │       └── KafkaConfig.java
│   │       └── resources/
│   │       │   └── application.yml
│   └── pom.xml
│
├── order-service/                # Order service
│   ├── src/
│   │   └── main/
│   │       ├── java/com/flashsale/order/
│   │       │   ├── controller/
│   │       │   │   └── OrderController.java
│   │       │   ├── dto/
│   │       │   │   ├── OrderRequest.java
│   │       │   │   ├── OrderResponse.java
│   │       │   │   └── PaymentRequest.java
│   │       │   ├── model/
│   │       │   │   ├── Order.java
│   │       │   │   ├── OutboxEvent.java
│   │       │   │   └── ProcessedEvent.java
│   │       │   ├── repository/
│   │       │   │   ├── OrderRepository.java
│   │       │   │   ├── OutboxEventRepository.java
│   │       │   │   └── ProcessedEventRepository.java
│   │       │   ├── service/
│   │       │   │   ├── OrderService.java
│   │       │   │   ├── IdempotencyService.java
│   │       │   │   └── SagaService.java
│   │       │   ├── kafka/
│   │       │   │   ├── producer/
│   │       │   │   │   └── OrderEventProducer.java
│   │       │   │   └── consumer/
│   │       │   │       ├── ReservationCreatedConsumer.java
│   │       │   │       ├── PaymentSucceededConsumer.java
│   │       │   │       └── PaymentFailedConsumer.java
│   │       │   ├── outbox/
│   │       │   │   ├── OutboxEventPublisher.java
│   │       │   │   └── OutboxEventListener.java
│   │       │   ├── idempotency/
│   │       │   │   ├── IdempotencyRepository.java
│   │       │   │   └── IdempotencyService.java
│   │       │   └── config/
│   │       │       ├── MongoConfig.java
│   │       │       └── KafkaConfig.java
│   │       └── resources/
│   │       │   └── application.yml
│   └── pom.xml
│
├── payment-service/              # Payment service
│   ├── src/
│   │   └── main/
│   │       ├── java/com/flashsale/payment/
│   │       │   ├── controller/
│   │       │   │   └── PaymentController.java
│   │       │   ├── dto/
│   │       │   │   ├── PaymentRequest.java
│   │       │   │   ├── PaymentResponse.java
│   │       │   │   └── PaymentResult.java
│   │       │   ├── model/
│   │       │   │   ├── Payment.java
│   │       │   │   ├── OutboxEvent.java
│   │       │   │   └── ProcessedEvent.java
│   │       │   ├── repository/
│   │       │   │   ├── PaymentRepository.java
│   │       │   │   ├── OutboxEventRepository.java
│   │       │   │   └── ProcessedEventRepository.java
│   │       │   ├── service/
│   │       │   │   ├── PaymentService.java
│   │       │   │   ├── IdempotencyService.java
│   │       │   │   └── MockPaymentProvider.java
│   │       │   ├── consumer/
│   │       │   │   └── OrderCreatedConsumer.java
│   │       │   ├── producer/
│   │       │   │   ├── PaymentEventProducer.java
│   │       │   │   └── PaymentResultProducer.java
│   │       │   ├── outbox/
│   │       │   │   ├── OutboxEventPublisher.java
│   │       │   │   └── OutboxEventListener.java
│   │       │   ├── idempotency/
│   │       │   │   ├── IdempotencyRepository.java
│   │       │   │   └── IdempotencyService.java
│   │       │   └── config/
│   │       │       ├── MongoConfig.java
│   │       │       └── KafkaConfig.java
│   │       └── resources/
│   │       │   └── application.yml
│   └── pom.xml
│
├── infrastructure/               # Docker and infrastructure configs
│   ├── docker-compose.yml
│   ├── Dockerfile (template for services)
│   ├── init-scripts/
│   │   ├── mongo-init.js
│   │   └── kafka-topics.sh
│   └── README.md
│
└── README.md                     # Project overview and setup instructions
```

## 3. Dependencies Between Services

### Direct Dependencies (Synchronous - REST)
- **API Gateway** → All services (routes requests)
- **Order Service** → Reservation Service (validates reservations before order creation)
- **Services** → Auth Service (indirect - via JWT validation at gateway or service level)

### Asynchronous Dependencies (Event-Driven - Kafka)
- **Reservation Service** → Order Service (ReservationCreated event)
- **Order Service** → Payment Service (OrderCreated event)
- **Payment Service** → Order Service (PaymentSucceeded/PaymentFailed events)
- **Payment Service** → Reservation Service (PaymentSucceeded/PaymentFailed events)
- **Reservation Service** → Order Service (ReservationExpired event - for cleanup)
- **Reservation Service** → Order Service (ReservationCancelled event - for cleanup)

### Data Store Dependencies
- **Auth Service** → auth_db (MongoDB)
- **Catalog Service** → catalog_db (MongoDB)
- **Reservation Service** → reservation_db (MongoDB) + Redis
- **Order Service** → order_db (MongoDB)
- **Payment Service** → payment_db (MongoDB)

### Infrastructure Dependencies
All services depend on:
- MongoDB (for data persistence)
- Redis (for distributed locking and rate limiting - Reservation Service primarily)
- Kafka (for event streaming)

### Dependency Summary
```
API Gateway
    ↓
┌─────────────┬─────────────┬─────────────┬─────────────┐
↓             ↓             ↓             ↓
Auth Service Catalog Service Reservation Service Order Service
                                                    ↓
                                            Payment Service
                                                    ↓
                                      ┌─────────────┴─────────────┐
                                      ↓                           ↓
                              Order Service              Reservation Service
                                      (Events)                 (Events)
```

## 4. Recommended Implementation Order

Based on the documents (Section 84: Recommended Development Order) and dependency analysis:

### Phase 1: Foundation (Days 1-2)
1. **Setup Infrastructure**
   - Create project structure
   - Configure Docker Compose with MongoDB (replica set), Redis, Kafka
   - Create base Dockerfiles for services

2. **API Gateway**
   - Implement basic routing
   - Add CORS, basic filtering
   - Set up port 8080

3. **Auth Service**
   - User entity and repository
   - Registration and login endpoints
   - JWT token generation and validation
   - Password encryption (BCrypt)
   - Basic user profile management

4. **Catalog Service**
   - Event and TicketType entities
   - CRUD operations for events and ticket types
   - Basic querying capabilities (list, filter, search)

### Phase 2: Core Reservation Logic (Day 3)
5. **Reservation Service - Core**
   - Inventory entity and repository
   - Reservation entity and repository
   - MongoDB atomic inventory update implementation
   - Basic reservation creation (without concurrency protection)
   - Reservation TTL and expiry logic

### Phase 3: Concurrency & Reliability (Day 4)
6. **Reservation Service - Concurrency**
   - Redis distributed locking implementation (Redisson)
   - Idempotency key support
   - Rate limiting implementation
   - Purchase limit enforcement
   - MongoDB transactions for reservation operations
   - Outbox pattern implementation

### Phase 4: Order & Payment Integration (Day 5)
7. **Order Service**
   - Order entity and repository
   - Order creation from reservation
   - Idempotency support
   - Outbox pattern for OrderCreated events
   - Reservation validation via REST call to Reservation Service

8. **Payment Service**
   - Payment entity and repository
   - Mock payment provider implementation
   - Idempotency support
   - Outbox pattern for payment events
   - Kafka consumers for OrderCreated events
   - Kafka producers for PaymentSucceeded/PaymentFailed events

### Phase 5: Event-Driven Workflows & UI (Day 6)
9. **Event Consumers**
   - Reservation Service: PaymentSucceeded/PaymentFailed consumers (inventory updates)
   - Order Service: PaymentSucceeded/PaymentFailed consumers (order status updates)
   - Order Service: ReservationCreated consumer (order creation)
   - Reservation Service: ReservationExpired consumer (inventory release)

10. **Frontend Integration**
    - Basic UI for event browsing
    - Reservation flow UI
    - Order creation and payment initiation UI
    - Order history view
    - API Gateway integration

### Phase 6: Testing, Monitoring & Polish (Day 7)
11. **Testing**
    - Unit tests for all services
    - Integration tests with Testcontainers
    - Contract tests between services
    - Concurrency tests (simulating high load)
    - Load testing scenarios

12. **Monitoring & Observability**
    - Spring Boot Actuator endpoints
    - Metrics collection (Micrometer)
    - Health checks for all services
    - Basic logging configuration

13. **Operational Excellence**
    - API documentation (Swagger/OpenAPI)
    - README with setup and deployment instructions
    - Architecture diagrams
    - Docker Compose optimization
    - Configuration externalization (environment variables)

## 5. What We Should Build First and Why

### First: Infrastructure and API Gateway

**Reasoning:**
1. **Foundation**: Everything else depends on having a working development environment
2. **Early Validation**: Allows testing service-to-service communication early
3. **Standardization**: Establishes common patterns (Docker, ports, config)
4. **Gateway First**: Enables testing of individual services through a unified entry point

Specifically, we should build:
1. **Docker Compose setup** with MongoDB replica set (required for transactions), Redis, and Kafka
2. **API Gateway** with basic routing capability
3. **Service template** that can be copied for each microservice

This approach provides immediate value because:
- Developers can start building services immediately against a working infrastructure
- Services can be tested independently through the gateway
- Establishes DevOps foundation early (containerization, networking)
- Prevents integration issues later by establishing communication patterns upfront

### Why Not Start with Business Logic First?
Starting with business logic (like Auth or Reservation service) without infrastructure leads to:
- Difficulty testing service interactions
- Inconsistent setup across team members
- Delayed discovery of infrastructure-related issues
- Re-work when adding Docker/Kafka/Redis dependencies later

## 6. Step-by-Step Development Plan

### Week 1: Foundation & Infrastructure

**Day 1: Environment Setup**
- Initialize git repository
- Create project directory structure
- Set up Docker Compose with:
  - MongoDB replica set (3 nodes for transactions)
  - Redis (single node for MVP)
  - Kafka (single node with Zookeeper for MVP)
  - Template Dockerfile for Java services
- Create basic README with setup instructions

**Day 2: API Gateway**
- Create api-gateway/ directory
- Implement Spring Cloud Gateway basic routing
- Configure routes to placeholder services (will implement later)
- Add CORS configuration
- Set up port 8080
- Create Dockerfile and add to docker-compose.yml
- Test basic routing to mock endpoints

**Day 3: Auth Service - Part 1**
- Create auth-service/ directory
- Implement User entity (email, password hash, role)
- Create UserRepository (Spring Data MongoDB)
- Implement password encryption service (BCrypt)
- Create basic exception handling
- Set up MongoDB connection
- Create Dockerfile and add to docker-compose.yml

**Day 4: Auth Service - Part 2**
- Implement registration endpoint (/api/v1/auth/register)
- Implement login endpoint (/api/v1/auth/login)
- Create JWT service (token generation and validation)
- Implement refresh token endpoint
- Add basic controller validation
- Test endpoints with Postman/curl
- Add Swagger/OpenAPI documentation

**Day 5: Catalog Service - Part 1**
- Create catalog-service/ directory
- Implement Event and TicketType entities
- Create repositories for both
- Implement basic CRUD operations
- Set up MongoDB connection
- Create Dockerfile and add to docker-compose.yml

**Day 6: Catalog Service - Part 2**
- Implement event listing, filtering, search
- Implement ticket type management
- Add validation and error handling
- Test endpoints
- Add Swagger documentation

**Day 7: Integration Testing Day 1**
- Start all services via docker-compose
- Test end-to-end flow:
  - Register user via Auth Service
  - Login and get JWT
  - Create event via Catalog Service
  - Create ticket types
  - Browse events
- Fix any integration issues
- Update documentation

### Week 2: Core Reservation Logic

**Day 8: Reservation Service - Part 1**
- Create reservation-service/ directory
- Implement Inventory and Reservation entities
- Create repositories for both
- Implement basic reservation creation (without concurrency)
- Create MongoDB connection
- Create Dockerfile and add to docker-compose.yml

**Day 9: Reservation Service - Concurrency Foundation**
- Add Redis dependency
- Implement Redisson-based distributed locking
- Create lock service abstraction
- Implement basic lock acquisition/release
- Test locking mechanism with concurrent threads

**Day 10: Reservation Service - Atomic Operations**
- Implement MongoDB atomic inventory update
- Create inventory service with conditional update method
- Test atomic update under concurrent scenarios
- Verify inventory never goes negative

**Day 11: Reservation Service - Transactions & Outbox**
- Implement MongoDB transaction manager
- Create outbox event entity and repository
- Implement outbox publisher pattern
- Combine inventory update, reservation creation, and outbox in single transaction
- Test transaction rollback scenarios

**Day 12: Reservation Service - Idempotency & Rate Limiting**
- Implement idempotency key storage and validation
- Add rate limiting using Redis
- Implement purchase limit checking (4 tickets/user/event)
- Test idempotency with duplicate requests
- Test rate limiting behavior

**Day 13: Reservation Service - Expiry & Scheduler**
- Implement reservation expiration logic
- Create scheduler to find and process expired reservations
- Implement inventory release on expiry
- Make expiry processing idempotent
- Test expiration scenarios

**Day 14: Integration Testing Day 2**
- Test reservation flow end-to-end:
  - Authenticate user
  - Create reservation
  - Verify inventory updates correctly
  - Test concurrent reservation attempts
  - Test idempotency
  - Test expiry and inventory release
- Fix any issues

### Week 3: Order & Payment Services

**Day 15: Order Service - Part 1**
- Create order-service/ directory
- Implement Order entity
- Create OrderRepository
- Implement basic order creation (placeholder for reservation validation)
- Create MongoDB connection
- Create Dockerfile and add to docker-compose.yml

**Day 16: Order Service - Part 2**
- Implement order creation from reservation ID
- Add reservation validation via REST call to Reservation Service
- Implement idempotency support
- Implement outbox pattern for OrderCreated events
- Add order status management
- Test order creation flow

**Day 17: Payment Service - Part 1**
- Create payment-service/ directory
- Implement Payment entity
- Create PaymentRepository
- Implement mock payment provider (configurable success/failure/timeout)
- Create MongoDB connection
- Create Dockerfile and add to docker-compose.yml

**Day 18: Payment Service - Part 2**
- Implement payment processing endpoint
- Add idempotency support
- Implement outbox pattern for payment events
- Create Kafka consumer for OrderCreated events
- Create Kafka producers for PaymentSucceeded/PaymentFailed events
- Test payment processing flow

**Day 19: Event-Driven Workflows - Reservation Service**
- Implement PaymentSucceeded consumer in Reservation Service:
  - Update inventory: reserved → sold
  - Update reservation status to CONFIRMED
- Implement PaymentFailed consumer in Reservation Service:
  - Update inventory: reserved → available
  - Update reservation status to CANCELLED
- Make both consumers idempotent
- Test with various payment outcomes

**Day 20: Event-Driven Workflows - Order Service**
- Implement PaymentSucceeded consumer in Order Service:
  - Update order status to CONFIRMED
- Implement PaymentFailed consumer in Order Service:
  - Update order status to PAYMENT_FAILED
- Make both consumers idempotent
- Test order status updates

**Day 21: Integration Testing Day 3**
- Test complete purchase flow:
  - Browse events → Select tickets → Create reservation
  - Create order from reservation
  - Initiate payment
  - Process payment (success/failure)
  - Verify state updates in all services
  - Verify inventory consistency
  - Test idempotency at each stage
  - Test failure scenarios and compensation

### Week 4: Frontend, Testing & Polish

**Day 22: Frontend Setup**
- Create frontend/ directory
- Set up React + TypeScript project
- Configure basic routing
- Create service layer for API communication
- Set up state management (Context API or Redux)
- Create basic layout components

**Day 23: Frontend - Event Browsing**
- Implement event listing page
- Implement event detail page
- Implement ticket type selection
- Connect to Catalog Service APIs
- Add loading and error states

**Day 24: Frontend - Reservation Flow**
- Implement reservation creation form
- Connect to Reservation Service APIs
- Add idempotency key generation
- Handle reservation response (including expiration time)
- Add validation and error handling

**Day 25: Frontend - Order & Payment**
- Implement order creation from reservation
- Connect to Order Service APIs
- Implement payment initiation
- Connect to Payment Service APIs
- Add payment status polling
- Show success/failure messages

**Day 26: Frontend - Order History**
- Implement order history page
- Connect to Order Service APIs
- Display past orders with status
- Add cancellation capability for eligible orders

**Day 27: Testing & Quality Assurance**
- Write unit tests for all service components
- Write integration tests using Testcontainers
- Test concurrency scenarios with multiple simultaneous users
- Test failure scenarios (service crashes, network partitions)
- Verify no overselling under high load
- Verify idempotency works correctly
- Verify saga compensation works

**Day 28: Monitoring, Documentation & Release**
- Add Spring Boot Actuator to all services
- Implement health check endpoints
- Add basic metrics (reservation success/failure, inventory conflicts, etc.)
- Create comprehensive README with:
  - Architecture overview
  - Setup instructions
  - API documentation
  - Running instructions
  - Testing instructions
- Create architecture diagrams
- Add Swagger/OpenAPI to all services
- Final demonstration of system capabilities

This plan follows the recommended order from the documents while ensuring we build a working foundation first, then incrementally add complexity, testing at each stage to ensure we don't accumulate technical debt. The focus is on building a working system that can be demonstrated and tested at the end of each week, rather than trying to build everything perfectly before testing.