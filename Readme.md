# FicMart Payment Gateway

A payment gateway for **FicMart**, a fictional e-commerce platform, built with **Java** and **Spring Boot**.

The gateway integrates with a mock external bank API and models a realistic payment lifecycle, including state transitions, idempotency, retry handling, failure classification, and automated testing.

## Features

* Payment authorization
* Payment capture
* Payment void
* Payment refund
* Payment state machine with strict transition rules
* Idempotency protection against duplicate operations
* Resilience4j retries for transient bank failures
* Exponential backoff between retry attempts
* Exception hierarchy with HTTP status mapping
* PostgreSQL persistence
* Flyway database migrations
* Mockito unit tests
* WireMock integration tests
* Docker-based PostgreSQL environment

## Architecture

The application follows a layered architecture:

```text
                         FicMart
                            │
                            ▼
                   PaymentController
                            │
                            ▼
                     PaymentService
                      │           │
                      │           └──────────────┐
                      ▼                          ▼
              PaymentRepository          IdempotencyService
                      │                          │
                      ▼                          ▼
                  PostgreSQL              Idempotency Records
                      │
                      │
                      ▼
                  BankClient
                   (interface)
                      │
                      ▼
                 BankClientHttp
                      │
                      ▼
                   RestClient
                      │
                      ▼
                 Mock Bank API
```

### Main components

**PaymentController**

Handles HTTP requests and is responsible for:

* Request validation
* Extracting path variables
* Extracting the `Idempotency-Key` header
* Calling the payment service
* Returning HTTP responses

**PaymentService**

Contains the core payment business logic.

It handles:

* Payment creation
* Payment state transitions
* Idempotency checks
* Bank operations
* Updating payment state
* Building payment responses

**BankClient**

An interface that abstracts communication with the external bank.

The application depends on this abstraction rather than directly depending on the HTTP implementation. This makes the business logic easier to test and allows the bank integration implementation to be replaced independently.

**BankClientHttp**

The HTTP implementation of `BankClient`.

It uses Spring's `RestClient` to communicate with the external bank API and classifies bank failures into transient and permanent failures.

**IdempotencyService**

Handles storing and retrieving idempotency records so that repeated requests can return previously generated responses rather than processing the same operation again.

**PaymentRepository**

Uses Spring Data JPA to persist payment information in PostgreSQL.

## Payment Lifecycle

Payments follow a strict state machine.

```text
                 ┌──────────────┐
                 │    PENDING   │
                 └──────┬───────┘
                        │
                        ▼
                 ┌──────────────┐
                 │  AUTHORIZED  │
                 └──────┬───────┘
                    ┌───┴────┐
                    │        │
                    ▼        ▼
             ┌──────────┐  ┌──────────┐
             │ CAPTURED │  │  VOIDED  │
             └────┬─────┘  └──────────┘
                  │
                  ▼
             ┌──────────┐
             │ REFUNDED │
             └──────────┘
```

The `Payment` domain object exposes `canTransitionTo()` to ensure that invalid state transitions cannot be performed.

For example:

* `PENDING → AUTHORIZED` is valid
* `AUTHORIZED → CAPTURED` is valid
* `AUTHORIZED → VOIDED` is valid
* `CAPTURED → REFUNDED` is valid
* `CAPTURED → VOIDED` is invalid
* `AUTHORIZED → REFUNDED` is invalid

## Idempotency

Every payment operation requires an `Idempotency-Key`.

Example:

```http
POST /payments/authorize
Idempotency-Key: abc-123
```

The key is stored with:

* `idempotency_key`
* `payment_id`
* `operation`
* `response_body`
* `created_at`

When the same key is received again, the previously stored response can be returned instead of processing the operation again.

This protects against duplicate payment operations when clients retry requests because of network failures or timeouts.

The implementation also considers concurrency and partial-failure scenarios. In a production system, stronger database-level concurrency controls would be required to make concurrent requests using the same key fully safe.

See [`Tradeoffs.md`](Tradeoffs.md) for the design decisions and limitations in more detail.

## Failure Handling

The bank integration distinguishes between transient and permanent failures.

### Transient failures

Examples:

* HTTP 5xx responses
* Network failures
* Temporary bank unavailability

These are represented by `BankTransientException` and are eligible for retry.

Resilience4j is configured to retry these failures using exponential backoff.

```text
Bank request
     │
     ▼
   500
     │
     ▼
 wait
     │
     ▼
 retry
     │
     ▼
   500
     │
     ▼
 wait longer
     │
     ▼
 retry
     │
     ▼
   success
```

### Permanent failures

Examples:

* Invalid payment request
* Invalid payment state
* Other bank-side business rejections

These are represented by `BankRejectedException` and are not retried because repeating the same request is unlikely to resolve the problem.

## Partial Failures

Communication with an external bank introduces distributed-system failure scenarios.

For example:

```text
PaymentService
      │
      ▼
Bank authorization
      │
      ▼
BANK SUCCESS
      │
      X
Application crashes
```

The bank may have successfully authorized the payment while the application fails before persisting the updated payment state.

Idempotency helps make retries safer, but it does not turn the database and bank operation into a single atomic transaction.

This is one of the main limitations documented in [`Tradeoffs.md`](Tradeoffs.md).

## API Endpoints

### Authorize

```http
POST /payments/authorize
```

Request body:

```json
{
  "amount": 5000,
  "orderId": "ORDER-123",
  "customerId": "CUSTOMER-123",
  "currency": "NGN",
  "cardDetails": {
    "cardNumber": "4111111111111111",
    "expiryDate": "12/28",
    "cvv": "123"
  }
}
```

Header:

```http
Idempotency-Key: abc-123
```

### Capture

```http
POST /payments/{paymentId}/capture
```

Header:

```http
Idempotency-Key: capture-123
```

### Void

```http
POST /payments/{paymentId}/void
```

Header:

```http
Idempotency-Key: void-123
```

### Refund

```http
POST /payments/{paymentId}/refund
```

Header:

```http
Idempotency-Key: refund-123
```

### Get payment

```http
GET /payments/{paymentId}
```

### Get payment by order

```http
GET /payments/order/{orderId}
```

### Get payments by customer

```http
GET /payments/customer/{customerId}
```

## Validation

The authorization request uses Jakarta Bean Validation.

Examples include:

* `@NotNull`
* `@NotBlank`
* `@Positive`
* `@Valid`

Invalid requests are handled by the global exception handler and return:

```text
400 Bad Request
```

with useful validation error information.

## Exception Handling

The application uses a common `PaymentGatewayException` hierarchy.

Examples include:

```text
PaymentGatewayException
├── PaymentNotFoundException
├── InvalidStateTransitionException
├── BankRejectedException
└── BankTransientException
```

`GlobalExceptionHandler` maps these exceptions to appropriate HTTP responses.

For example:

```text
PaymentNotFoundException
        ↓
404 Not Found

InvalidStateTransitionException
        ↓
409 Conflict

Validation failure
        ↓
400 Bad Request
```

## Testing

The project uses two main testing approaches.

### Unit Tests

`PaymentService` is tested using **JUnit 5 and Mockito**.

External dependencies are mocked:

```text
PaymentService
   │
   ├── mocked PaymentRepository
   ├── mocked BankClient
   └── mocked IdempotencyService
```

Tests cover scenarios including:

* Successful payment operations
* Cached idempotent responses
* Payment not found
* Invalid state transitions
* Bank failures
* Correct interaction with dependencies

### Integration Tests

`BankClientHttp` is tested using **WireMock**.

WireMock provides a fake HTTP bank server so tests do not depend on the real mock bank being available.

The integration tests verify scenarios such as:

```text
Bank → 500
      ↓
Resilience4j retry
      ↓
Bank → success
      ↓
successful response
```

## Tech Stack

| Technology      | Purpose                     |
| --------------- | --------------------------- |
| Java            | Application language        |
| Spring Boot     | Application framework       |
| Spring Web      | REST API                    |
| Spring Data JPA | Database persistence        |
| Hibernate       | ORM                         |
| PostgreSQL      | Relational database         |
| Flyway          | Database migrations         |
| RestClient      | External HTTP communication |
| Resilience4j    | Retry and resilience        |
| JUnit 5         | Testing                     |
| Mockito         | Unit testing                |
| WireMock        | HTTP integration testing    |
| Docker          | Local infrastructure        |

## Running Locally

### Prerequisites

Install:

* Java
* Maven
* Docker
* Docker Compose

### Start PostgreSQL

From the project root:

```bash
docker compose up -d
```

Verify the container is running:

```bash
docker compose ps
```

### Start the application

Using Maven:

```bash
mvn spring-boot:run
```

Or run the Spring Boot application directly from IntelliJ.

The application will connect to PostgreSQL and run the configured Flyway migrations.

## Running Tests

Run the complete test suite with:

```bash
mvn test
```

The test suite includes both Mockito unit tests and WireMock integration tests.

## Project Structure

```text
src/
├── main/
│   ├── java/
│   │   └── com/etoro/payment_gateway_app/
│   │       ├── client/
│   │       ├── config/
│   │       ├── controller/
│   │       ├── dto/
│   │       ├── exceptions/
│   │       ├── model/
│   │       ├── repository/
│   │       └── service/
│   │
│   └── resources/
│       └── application.yml
│
└── test/
    └── java/
        └── com/etoro/payment_gateway_app/
```

## Design Tradeoffs

The major design decisions, limitations, and production considerations are documented separately in:

**[Tradeoffs.md](Tradeoffs.md)**

Topics include:

* Layered architecture
* Synchronous bank communication
* Payment state management
* Retry and exponential backoff
* Partial failures
* Idempotency and concurrency
* Observability
* Security and payment data
* Secrets management
* Circuit breakers
* Timeout configuration

## Production Considerations

This project is simplified for demonstration.

A production payment gateway would require additional measures including:

* PCI-DSS-compliant payment data handling
* Card tokenization
* Encryption and strict access controls
* Strong database concurrency guarantees
* Distributed tracing and metrics
* Structured logging
* Secrets management
* Circuit breakers
* Carefully tuned timeout and retry policies
* Stronger operational monitoring and alerting

## 
