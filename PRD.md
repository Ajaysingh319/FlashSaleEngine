# Product Requirements Document (PRD)

## High-Concurrency Flash Sale & Ticket Reservation Engine

**Version:** 2.0  
**Status:** Development  
**Architecture:** Microservices  
**Frontend:** React + TypeScript  
**Backend:** Java 17 + Spring Boot  
**Primary Database:** MongoDB  
**Cache / Coordination:** Redis  
**Messaging:** Apache Kafka  
**API Gateway:** Spring Cloud Gateway  
**Containerization:** Docker + Docker Compose

---

# 1. Product Overview

## 1.1 Product Name

**High-Concurrency Flash Sale & Ticket Reservation Engine**

## 1.2 Product Summary

The system is a high-demand ticket reservation platform designed to handle thousands of users attempting to purchase limited inventory simultaneously.

The platform simulates scenarios such as:

- Concert ticket launches
- Sports event ticket releases
- Movie/event ticket sales
- Flash-sale inventory
- Limited-quantity product drops

The primary product challenge is:

> How do we allow thousands of users to compete for limited inventory without overselling tickets, creating duplicate orders, or incorrectly processing payments?

The platform uses a microservice architecture in which different business capabilities are independently deployed and communicate using REST APIs and asynchronous Kafka events.

The system must guarantee:

```text
Successful inventory allocation <= available inventory
```

and:

```text
One logical purchase request != multiple successful bookings
```

---

# 2. Problem Statement

During a high-demand sale, thousands of users may request the same ticket category simultaneously.

Example:

```text
100 VIP tickets

           ↓

5,000 users attempt to purchase

           ↓

Reservation Service
```

The system must prevent multiple requests from allocating the same inventory.

It must also handle network retries.

Example:

```text
User
 ↓
Create reservation/order
 ↓
Server processes request
 ↓
Response is lost
 ↓
Client retries
```

The retry must not create another logical order or trigger duplicate payment processing.

The system also needs to handle asynchronous payment processing and failures without permanently consuming inventory.

---

# 3. Goals & Objectives

## 3.1 Product Goals

The platform must:

1. Allow customers to register and authenticate.
2. Allow customers to discover events.
3. Display ticket categories and prices.
4. Display ticket availability.
5. Allow customers to reserve tickets.
6. Prevent inventory overselling.
7. Prevent duplicate purchase operations.
8. Temporarily hold inventory during checkout.
9. Process payments asynchronously.
10. Confirm successful orders.
11. Release inventory when payment fails or reservations expire.
12. Allow customers to view order history.
13. Allow administrators to create and manage events.
14. Allow administrators to monitor inventory and orders.

## 3.2 Engineering Goals

The system should demonstrate:

- Microservice architecture
- Distributed locking
- Atomic inventory operations
- MongoDB transactions
- Idempotency
- Kafka-based asynchronous communication
- Saga-style distributed workflow
- Retry handling
- Dead-letter processing
- Rate limiting
- Horizontal scalability
- Load testing

---

# 4. Target Users

## 4.1 Customer

A customer can:

- Register
- Login
- Browse events
- View event details
- View ticket availability
- Reserve tickets
- Create an order
- Initiate payment
- Monitor payment/order status
- View past orders
- Cancel eligible reservations/orders

---

## 4.2 Administrator

An administrator can:

- Create events
- Modify events
- Cancel events
- Create ticket types
- Configure inventory
- View event statistics
- View orders
- View active reservations
- Monitor payment outcomes

---

# 5. User Personas / Use Cases

## Persona 1 — Normal Customer

```text
Login
 ↓
Browse Events
 ↓
Open Event
 ↓
Select Ticket Type
 ↓
Select Quantity
 ↓
Reserve Tickets
 ↓
Create Order
 ↓
Initiate Payment
 ↓
Payment Processing
 ↓
Order Confirmed
```

---

## Persona 2 — Flash Sale Customer

A customer enters immediately after a sale begins while thousands of other customers are also purchasing.

The user should receive one of:

```text
Reservation Successful
```

or:

```text
Inventory Unavailable
```

The system must never allocate more inventory than exists.

---

## Persona 3 — Administrator

The administrator creates:

```text
Delhi Music Festival
```

with:

```text
VIP       → 500
Premium   → 1,000
Regular   → 5,000
```

and monitors ticket sales.

---

# 6. Features / Functional Requirements

## 6.1 Authentication

The system shall provide:

- Registration
- Login
- Logout
- JWT authentication
- Refresh token
- Current-user profile

Requirements:

- Email must be unique.
- Passwords must not be stored in plaintext.
- Protected operations require authentication.
- Administrative operations require ADMIN role.

---

# 6.2 Event Discovery

Customers can:

- List events
- Search events
- Filter events
- Sort events
- View event details

Event information:

```text
Event Name
Description
Venue
City
Start Time
End Time
Sale Start Time
Sale End Time
Status
```

Possible statuses:

```text
DRAFT
UPCOMING
ON_SALE
SOLD_OUT
COMPLETED
CANCELLED
```

---

# 6.3 Ticket Categories

Each event can have multiple ticket categories:

```text
VIP
Premium
Regular
Economy
```

Each category contains:

```text
Name
Price
Total Quantity
Available Quantity
Reserved Quantity
Sold Quantity
```

Business invariant:

```text
Total =
Available +
Reserved +
Sold
```

---

# 6.4 Inventory

Inventory is owned by the **Reservation Service**.

The backend must guarantee:

```text
availableQuantity >= 0
```

and:

```text
soldQuantity <= totalQuantity
```

Frontend availability is informational only.

The Reservation Service is the final authority for inventory.

---

# 6.5 Ticket Reservation

Customers can reserve tickets for a short period.

Example:

```text
Reservation TTL = 10 minutes
```

Flow:

```text
Customer
 ↓
Reservation API
 ↓
Inventory validation
 ↓
Inventory reservation
 ↓
Reservation created
 ↓
Expiration timestamp returned
```

The reservation response must include:

```text
reservationId
quantity
amount
status
expiresAt
```

---

# 6.6 Reservation Expiration

If a reservation is not completed before expiration:

```text
ACTIVE
 ↓
EXPIRED
 ↓
Inventory Released
```

Expired reservations cannot be used for payment.

---

# 6.7 Order Creation

A customer can create an order from an active reservation.

Order contains:

```text
Order ID
User
Reservation
Event
Ticket Type
Quantity
Amount
Status
Payment Status
Created At
Updated At
```

Initial state:

```text
PENDING_PAYMENT
```

---

# 6.8 Payment

The MVP uses a simulated payment provider.

Possible results:

```text
SUCCESS
FAILED
TIMEOUT
```

Payment must be processed asynchronously.

The API should return quickly with:

```text
PAYMENT_PROCESSING
```

rather than holding the request open until the payment workflow completes.

---

# 6.9 Order Confirmation

Successful payment:

```text
Payment SUCCESS
 ↓
Order CONFIRMED
 ↓
Reservation CONFIRMED
 ↓
Reserved inventory becomes SOLD
```

Failed payment:

```text
Payment FAILED
 ↓
Order PAYMENT_FAILED
 ↓
Reservation released
 ↓
Inventory returned
```

---

# 6.10 Idempotency

Critical write APIs support:

```http
Idempotency-Key: <unique-key>
```

The same logical operation sent multiple times must not produce duplicate results.

Example:

```text
Request 1 → key ABC → order created
Request 2 → key ABC → existing result returned
```

Reusing a key with a different payload must be rejected.

---

# 6.11 Rate Limiting

Rate limits should protect high-value operations.

Examples:

```text
Login
Reservation
Order Creation
Payment Initiation
```

Exceeded limits return:

```http
429 Too Many Requests
```

---

# 6.12 Order History

Customers can view:

```text
Order ID
Event
Ticket Type
Quantity
Amount
Order Status
Payment Status
Created At
```

---

# 6.13 Cancellation

Users can cancel eligible:

- Reservations
- Pre-confirmation orders

When inventory is still reserved, cancellation releases it.

Cancellation must itself be idempotent.

---

# 6.14 Admin Management

Administrators can:

```text
Create Event
Update Event
Cancel Event
Create Ticket Type
Update Ticket Type
View Inventory
View Orders
View Reservations
View Statistics
```

---

# 7. User Flows

## 7.1 Reservation Flow

```text
React
 ↓
API Gateway
 ↓
Reservation Service
 ↓
Redis Distributed Lock
 ↓
MongoDB Atomic Inventory Update
 ↓
Create Reservation
 ↓
Return Reservation
```

---

## 7.2 Order Flow

```text
React
 ↓
API Gateway
 ↓
Order Service
 ↓
Validate Reservation
 ↓
Create Order
 ↓
Order = PENDING_PAYMENT
```

---

## 7.3 Payment Flow

```text
Order Service
 ↓
Kafka
 ↓
Payment Service
 ↓
Mock Payment Provider
 ↓
Kafka
 ↓
Order Service
Reservation Service
```

---

## 7.4 Successful Payment

```text
Payment Service
 ↓
PaymentSucceeded
 ↓
Kafka
 ├─────────────┐
 ▼             ▼
Order Service  Reservation Service
 ↓             ↓
CONFIRMED      Reserved → Sold
```

---

## 7.5 Failed Payment

```text
Payment Service
 ↓
PaymentFailed
 ↓
Kafka
 ├─────────────┐
 ▼             ▼
Order Service  Reservation Service
 ↓             ↓
FAILED         Release Inventory
```

---

## 7.6 Reservation Expiry

```text
Scheduler
 ↓
Reservation Service
 ↓
Find expired ACTIVE reservations
 ↓
Release inventory
 ↓
Mark reservation EXPIRED
 ↓
Publish ReservationExpired event
```

---

# 8. Non-Functional Requirements

## 8.1 Performance

The platform should be designed for:

```text
5,000+ concurrent purchase attempts
```

Target benchmark values:

```text
P50 < 100 ms
P95 < 300 ms
P99 < 500 ms
```

These are test-environment targets.

---

# 8.2 Scalability

Each service should be independently scalable.

Example:

```text
Reservation Service × 3
Order Service × 2
Payment Service × 4
```

A heavy flash-sale event may require more Reservation Service instances without scaling unrelated services.

---

# 8.3 Reliability

The system should survive:

- Backend instance failure
- Redis interruption
- Kafka interruption
- Payment failure
- Duplicate Kafka events
- Network retries
- Temporary downstream failures

---

# 8.4 Security

Implement:

- JWT
- BCrypt
- Role-based authorization
- Rate limiting
- Request validation
- CORS
- Secure headers

---

# 8.5 Data Isolation

Each microservice owns its own data.

No service may directly access another service's MongoDB collections.

Communication must occur through:

```text
REST APIs
or
Kafka events
```

---

# 9. Business Rules

### BR-001 — Inventory

Inventory cannot become negative.

### BR-002 — Purchase Limit

Default:

```text
Maximum 4 tickets/user/event
```

### BR-003 — Reservation TTL

Default:

```text
10 minutes
```

### BR-004 — Reservation Ownership

Only the reservation owner can use the reservation.

### BR-005 — Expired Reservation

Expired reservations cannot be converted into paid orders.

### BR-006 — Sale Window

Purchases are accepted only during:

```text
saleStartTime <= currentTime <= saleEndTime
```

### BR-007 — Order

A reservation can create at most one order.

### BR-008 — Payment

An order becomes CONFIRMED only after successful payment.

### BR-009 — Failed Payment

Failed payment eventually releases reserved inventory.

### BR-010 — Event Cancellation

Cancelled events cannot accept new reservations.

### BR-011 — Idempotency

One idempotency key represents one logical operation.

---

# 10. Edge Cases & Failure Scenarios

## 10.1 Last Ticket

```text
Inventory = 1
Concurrent Requests = 100
```

Expected:

```text
1 successful reservation
99 rejected
```

---

## 10.2 Duplicate HTTP Request

Same idempotency key:

```text
Only one logical operation
```

---

## 10.3 Same Key, Different Payload

Expected:

```text
409 IDEMPOTENCY_KEY_REUSED
```

---

## 10.4 Payment Failure

Expected:

```text
Order → PAYMENT_FAILED
Reservation → CANCELLED
Inventory → RELEASED
```

---

## 10.5 Payment Consumer Receives Duplicate Event

Expected:

```text
State transition happens once
```

---

## 10.6 Kafka Unavailable

Existing transactions must not be lost.

Events are persisted through the service outbox and published later.

---

## 10.7 Service Crash

If an operation has not committed:

```text
Transaction rollback
```

If committed:

```text
Persisted state + outbox event survive restart
```

---

## 10.8 Redis Failure

The Reservation Service should fail safely rather than bypassing its distributed coordination mechanism.

---

# 11. Acceptance Criteria

## Authentication

- [ ] Users can register.
- [ ] Users can login.
- [ ] Duplicate emails are rejected.
- [ ] JWT protection works.
- [ ] Admin endpoints reject customers.

## Catalog

- [ ] Admin can create events.
- [ ] Users can browse events.
- [ ] Users can view ticket categories.
- [ ] Prices are displayed.

## Reservation

- [ ] Available inventory can be reserved.
- [ ] Inventory cannot become negative.
- [ ] Reservation TTL is enforced.
- [ ] Expired reservations release inventory.

## Concurrency

For:

```text
Inventory = 100
Concurrent attempts = 5,000
```

the number of successful allocations must never exceed:

```text
100
```

## Idempotency

- [ ] Duplicate logical requests do not create duplicate operations.
- [ ] Payload mismatch is rejected.

## Payment

- [ ] Payment runs asynchronously.
- [ ] Successful payment confirms order.
- [ ] Failed payment releases inventory.
- [ ] Duplicate payment events do not duplicate state transitions.

## Microservices

- [ ] Each service can run independently.
- [ ] Services do not access each other's databases directly.
- [ ] Kafka events are processed asynchronously.
- [ ] API Gateway routes requests correctly.

---

# 12. Success Metrics

Primary engineering metrics:

```text
Oversold tickets = 0
Duplicate orders = 0
Duplicate successful payments = 0
```

Load target:

```text
5,000+ concurrent purchase attempts
```

Measure:

```text
Requests/sec
P50
P95
P99
Error Rate
Reservation Success Rate
Payment Success Rate
Kafka Lag
MongoDB Latency
Redis Latency
```

---

# 13. Scope / Non-Goals

## MVP Scope

```text
✓ React frontend
✓ API Gateway
✓ Auth Service
✓ Catalog Service
✓ Reservation Service
✓ Order Service
✓ Payment Service
✓ MongoDB
✓ Redis
✓ Kafka
✓ Distributed locking
✓ Idempotency
✓ Reservation expiry
✓ Async payment
✓ Basic admin dashboard
✓ Docker Compose
✓ Concurrency testing
✓ Load testing
```

## Non-Goals

The MVP will not include:

```text
✗ Kubernetes
✗ Multi-region deployment
✗ Service discovery platform
✗ Config Server
✗ Real payment settlement
✗ Advanced seat maps
✗ Native mobile applications
✗ ML/recommendations
✗ Complex fraud detection
```

---

# 14. Dependencies & Constraints

The application depends on:

```text
React
Java 17
Spring Boot
Spring Cloud Gateway
MongoDB
Redis
Kafka
Docker
```

For local development, one MongoDB replica set may host separate databases for each service:

```text
auth_db
catalog_db
reservation_db
order_db
payment_db
```

This preserves logical database ownership without requiring five separate MongoDB installations.

---

# 15. Future Enhancements

Potential future additions:

```text
Virtual Waiting Room
WebSocket/SSE real-time availability
Specific seat selection
Real payment gateway
Notification Service
Email/SMS
Advanced analytics
MongoDB replica scaling
Kubernetes deployment
Multi-region deployment
```

---

# 16. API Catalog

## Authentication

```http
POST /api/v1/auth/register
POST /api/v1/auth/login
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
```

## Users

```http
GET /api/v1/users/me
PUT /api/v1/users/me
```

## Events

```http
GET  /api/v1/events
GET  /api/v1/events/{eventId}
POST /api/v1/events
PUT  /api/v1/events/{eventId}
POST /api/v1/events/{eventId}/cancel
```

## Ticket Types

```http
GET    /api/v1/events/{eventId}/ticket-types
POST   /api/v1/events/{eventId}/ticket-types
PUT    /api/v1/ticket-types/{ticketTypeId}
DELETE /api/v1/ticket-types/{ticketTypeId}
```

## Inventory

```http
GET /api/v1/events/{eventId}/inventory
GET /api/v1/ticket-types/{ticketTypeId}/inventory
```

## Reservations

```http
POST /api/v1/reservations
GET  /api/v1/reservations/{reservationId}
GET  /api/v1/reservations/me
POST /api/v1/reservations/{reservationId}/cancel
```

## Orders

```http
POST /api/v1/orders
GET  /api/v1/orders/{orderId}
GET  /api/v1/orders/me
POST /api/v1/orders/{orderId}/cancel
```

## Payments

```http
POST /api/v1/orders/{orderId}/payment
GET  /api/v1/payments/{paymentId}
```

## Admin

```http
GET /api/v1/admin/orders
GET /api/v1/admin/reservations/active
GET /api/v1/admin/events/{eventId}/statistics
GET /api/v1/admin/statistics
```

---

# 17. High-Level Microservice Architecture

```text
                              ┌────────────────┐
                              │ React Frontend │
                              └───────┬────────┘
                                      │
                                      ▼
                           ┌─────────────────────┐
                           │    API Gateway      │
                           │ Spring Cloud Gateway│
                           └─────────┬───────────┘
                                     │
              ┌──────────────────────┼─────────────────────────┐
              │                      │                         │
              ▼                      ▼                         ▼
       ┌────────────┐        ┌──────────────┐        ┌─────────────────┐
       │ Auth       │        │ Catalog      │        │ Reservation     │
       │ Service    │        │ Service      │        │ Service         │
       └────────────┘        └──────────────┘        └───────┬─────────┘
                                                             │
                                                        ┌────┴────┐
                                                        ▼         ▼
                                                     Redis     MongoDB
                                                             reservation_db

                                      Kafka
                                        │
                           ┌────────────┼────────────┐
                           ▼            ▼            ▼
                     Order Service  Payment      Reservation
                                    Service       Service
                           │            │
                        MongoDB      MongoDB
```

The architecture separates synchronous APIs from asynchronous workflows while keeping inventory ownership centralized in the Reservation Service.