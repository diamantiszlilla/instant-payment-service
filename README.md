# Instant Payment Service

This is a REST API service created for processing instant payments with Java 21, Spring Boot 3.5.7, and Kafka.

This project stresses transactional integrity, strong concurrency handling, and a fault-tolerant asynchronous architecture.

## Technology Stack

- **Core**: Java 21, Spring Boot 3.5.7, Maven
- **Database**: PostgreSQL, Spring Data JPA, Flyway
- **Messaging**: Kafka (via Spring Kafka)
- **Security**: Spring Security, JWT
- **Mapping**: MapStruct (type-safe object mapping)
- **Testing**: JUnit 5, Mockito, Testcontainers
- **Containerization**: Docker & Docker Compose

## Key Features

In this project, several critical features for a financial system are implemented:

- **Pessimistic Locking (FOR UPDATE)**: The service applies a `PESSIMISTIC_WRITE` lock on account rows during a transaction to avoid race conditions and maintain the integrity of data while serving multiple concurrent payment requests.
- **Idempotent API**: The `/api/payments` endpoint expects an `Idempotency-Key` header to prevent duplicate transaction processing.
- **Transactional Outbox Pattern**: Ensures "at-least-once" message delivery. Kafka messages are stored in an `outbox_events` table on the same database transaction as the payment, ensuring notifications are only sent after successful payment commits. A separate `@Scheduled` poller processes these events asynchronously.
- **Security & Authorization**: API is secured with JWT authentication. Service performs authorization checks to ensure that the user can only send money from accounts that are owned by them.
- **PII Encryption**: Account numbers are encrypted at rest in the database via a custom JPA `AttributeConverter` with AES encryption.

## Getting Started (Docker)

This project is fully containerized and includes a development data seeder.

1. **Prerequisites**
   - Java 21
   - Maven 3.8+
   - Docker & Docker Compose

2. **Clone the Repository**
   ```bash
   git clone <repository-url>
   cd instant-payment-service
   ```

3. **Environment Variables Setup**

   Set the following environment variables, or update `application-docker.properties`:
   - `JWT_SECRET`: Secret key for JWT token generation. Should have a minimum of 32 characters
   - `PII_ENCRYPTION_KEY`: Base64-encoded 32-byte key for account number encryption

4. **Build and Run**
   ```bash
   docker compose up --build -d
   ```
   The application will be available at http://localhost:8080.

   **Note:** Swagger UI is available at http://localhost:8080/swagger-ui/index.html for interactive API documentation.

## API Usage

The service starts in a `dev` profile and automatically creates 2 test users with accounts.

- User 1: `user` / `password123`
- User 2: `recipient` / `password123`

These defaults are controlled by the `dev.seed.*` properties. You can customize the seeded data by updating `application-dev.properties` or `application-docker.properties`, or by overriding the corresponding environment variables (e.g., `DEV_SEED_SENDER_USERNAME`, `DEV_SEED_SENDER_INITIAL_BALANCE`, `DEV_SEED_ACCOUNT_CURRENCY`).

### Step 1: Get a JWT Token

Authenticate by calling the `/auth/login` endpoint to obtain your JWT token.

**Request:**
```json
{
  "username": "user",
  "password": "password123"
}
```

**Response:**
```json
{
  "token": "eweJh..."
}
```

### Step 2: Make a Payment

Make a request to `/api/payments` with your authentication token to process a payment.

**Headers:**
- `Authorization: Bearer <your-token>`
- `Idempotency-Key: <generate-a-uuid>` (e.g., `f47ac10b-58cc-4372-a567-0e02b2c3d479`)

**Request:**
```json
{
  "senderAccountId": "f1d2d3e4-5678-90ab-cdef-123456789abc",
  "recipientAccountId": "a1b2c3d4-5678-90ef-ghij-987654321xyz",
  "amount": 100.50,
  "currency": "USD"
}
```

**Note:** If running in either `dev` or `docker` profile then the application logs will show account IDs upon startup. These would be messages like:
```
DEV MODE: Sender account available - accountId=..., accountNumber=..., balance=... USD
DEV MODE: Recipient account available - accountId=..., accountNumber=..., balance=... USD
```

The `senderAccountId` has to belong to the authenticated user.

**Response (200 OK):**
```json
{
  "transactionId": "f3er2..",
  "status": "COMPLETED",
  "senderAccountId": "r234...",
  "recipientAccountId": "m5n2...",
  "amount": 100.50,
  "currency": "USD",
  "createdAt": "..."
}
```

## Running Tests

The test suite is designed for fast feedback, including unit tests, controller slices, and repository tests - all powered by Testcontainers.

To run all tests locally:
```bash
mvn clean test
```
