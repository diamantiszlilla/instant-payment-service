# Instant Payment Service

## Quick start
1) Copy .env.example to .env and set your local secrets.
2) Build & run:
   docker compose build
   docker compose up -d
3) API docs: http://localhost:8080/swagger-ui/index.html

## Tests
- Unit/Integration tests: mvn test
- Testcontainers usage for Postgres/Kafka

## Security notes
- No real secrets committed.
- For prod, use KMS/Vault & asymmetric JWT keys (RS256/ES256) with rotation.
- Consider Transactional Outbox + Debezium or Kafka EoS