# Flash Sale & Ticket Reservation Engine

## Project Description
A high-concurrency flash sale and ticket reservation engine designed to handle thousands of users attempting to purchase limited inventory simultaneously. The system prevents overselling, duplicate orders, and incorrect payment processing in high-demand scenarios like concert ticket launches, sports events, and flash sales.

## Architecture Overview
The system follows a microservices architecture with six independently deployable services:

1. **API Gateway** (Spring Cloud Gateway) - Request routing and cross-cutting concerns
2. **Auth Service** - User registration, authentication, JWT management
3. **Catalog Service** - Event and ticket type management (read-only for inventory)
4. **Reservation Service** - Inventory management, reservations, distributed locking (most critical for concurrency)
5. **Order Service** - Order creation, state management, order history
6. **Payment Service** - Payment processing with mock provider

## Infrastructure Components
- **MongoDB** (Replica Set) - Transaction-capable database per service
- **Redis** - Distributed locking, rate limiting, caching
- **Kafka** - Asynchronous inter-service communication
- **Docker Compose** - Local development orchestration

## Local Development Prerequisites
- Docker and Docker Compose
- Java 17+
- Maven 3.8+
- 8GB+ RAM recommended

## How to Start the Infrastructure
```bash
# Navigate to infrastructure directory
cd infrastructure

# Start all services
docker-compose up -d

# Verify services are running
docker-compose ps
```

## How to Stop the Infrastructure
```bash
# Navigate to infrastructure directory
cd infrastructure

# Stop and remove containers
docker-compose down

# To also remove volumes (data will be lost)
docker-compose down -v
```

## Service Ports
- API Gateway: 8080
- Auth Service: 8081
- Catalog Service: 8082
- Reservation Service: 8083
- Order Service: 8084
- Payment Service: 8085
- MongoDB: 27017-27019 (replica set)
- Redis: 6379
- Kafka: 9092
- Zookeeper: 2181

## Database Configuration
Each service owns its own logical MongoDB database:
- auth_service → auth_db
- catalog_service → catalog_db
- reservation_service → reservation_db
- order_service → order_db
- payment_service → payment_db

MongoDB is configured as a 3-node replica set to support transactions.

## Next Steps
After starting the infrastructure, proceed with implementing:
1. Auth Service (registration, login, JWT)
2. Catalog Service (event and ticket type CRUD)
3. Reservation Service (inventory, locking, transactions)
4. Order Service (order creation, validation)
5. Payment Service (mock payment processing)
6. Frontend (React + TypeScript)

Each service can be run independently using:
```bash
./mvnw spring-boot:run
```
or
```bash
mvn spring-boot:run
```