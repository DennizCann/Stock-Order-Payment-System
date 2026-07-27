# Stock–Order–Payment System

> **Work in progress** — Portfolio-quality Spring Boot backend for stock tracking, evolving toward order/payment flows.  
> Focus: technologies commonly requested in Java Backend job postings (Security, JWT, Docker, Flyway, tests).

---

## Project vision

Not a toy CRUD app. Target business flow:

```
Product catalog → Stock management → Inventory history → (next) Order → Payment → Async callback
```

---

## Current features

| Module | Status | Description |
|--------|--------|-------------|
| **Auth** | Done | JWT login, `ADMIN` / `USER` roles |
| **Product** | Done | Create / list / get by id |
| **Stock** | Done | Create / get / set quantity / stock-in / stock-out |
| **Inventory history** | Done | Transaction log per product |
| **Order** | Planned | — |
| **Payment** | Planned | — |

### API overview

**Auth**

| Method | Endpoint | Auth |
|--------|----------|------|
| `POST` | `/api/v1/auth/login` | Public |

**Products**

| Method | Endpoint | Roles |
|--------|----------|-------|
| `POST` | `/api/v1/products` | `ADMIN` |
| `GET` | `/api/v1/products` | `USER`, `ADMIN` |
| `GET` | `/api/v1/products/{id}` | `USER`, `ADMIN` |

**Stock**

| Method | Endpoint | Roles |
|--------|----------|-------|
| `POST` | `/api/v1/products/{productId}/stock` | `ADMIN` |
| `GET` | `/api/v1/products/{productId}/stock` | `USER`, `ADMIN` |
| `PATCH` | `/api/v1/products/{productId}/stock` | `ADMIN` |
| `POST` | `/api/v1/products/{productId}/stock/in` | `ADMIN` |
| `POST` | `/api/v1/products/{productId}/stock/out` | `ADMIN` |

**Inventory**

| Method | Endpoint | Roles |
|--------|----------|-------|
| `GET` | `/api/v1/products/{productId}/inventory-transactions` | `USER`, `ADMIN` |

**Errors:** `400` validation, `401` bad credentials, `403` forbidden, `404` not found, `409` conflict (duplicate SKU/stock, insufficient stock).

---

## Roadmap

- [x] Spring Boot + PostgreSQL
- [x] Product + Stock vertical slices
- [x] Flyway migrations + env profiles
- [x] Docker + Docker Compose
- [x] Spring Security + JWT + RBAC
- [x] Unit + integration tests
- [x] Inventory transaction history
- [ ] Order + stock reservation
- [ ] Payment provider simulation + async callback
- [ ] Pagination / filtering, Redis/Kafka (optional)

Progress tracker: [`TICKETS.md`](TICKETS.md)

---

## Tech stack

| Layer | Technology |
|--------|-----------|
| Runtime | Java 21 |
| Framework | Spring Boot 3.4 |
| Security | Spring Security, JWT (JJWT) |
| Persistence | Spring Data JPA, Hibernate, Flyway |
| Database | PostgreSQL (H2 for tests) |
| API | REST, Bean Validation, Springdoc OpenAPI |
| Build | Maven |
| Containers | Docker, Docker Compose |
| Tests | JUnit 5, Mockito, MockMvc, Spring Security Test |

---

## Architecture

```
Controller → Service → Repository → Entity → PostgreSQL
     ↑           ↑
  DTO/@Valid  Business rules + inventory logging
```

```
com.denizcan.stockorderpayment
├── config/          # OpenAPI, seed data
├── domain/          # Product, Stock, User, Inventory
├── repository/
├── security/        # JWT filter, SecurityFilterChain
├── service/
├── web/             # Controllers, DTOs, exception handler
└── exception/
```

---

## Default users (seeded on startup)

| Username | Password | Role |
|----------|----------|------|
| `admin` | `admin123` | `ADMIN` |
| `user` | `user123` | `USER` |

Change these before any real deployment.

---

## Run locally (Maven + PostgreSQL)

1. Create DB:

```sql
CREATE DATABASE stock_order_payment;
```

2. Configure env (optional; defaults work for local demo):

```bash
# Windows PowerShell examples
$env:SPRING_PROFILES_ACTIVE="dev"
$env:DB_URL="jdbc:postgresql://localhost:5432/stock_order_payment"
$env:DB_USERNAME="postgres"
$env:DB_PASSWORD="postgres"
```

3. Start:

```bash
mvn spring-boot:run
```

If you already had Hibernate-created tables, either drop them or rely on Flyway `baseline-on-migrate` (enabled in `dev`).

4. Swagger: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)

5. Login in Swagger → Authorize with `Bearer <token>` → call product/stock APIs.

---

## Run with Docker Compose

```bash
docker compose up --build
```

- App: `http://localhost:8080`
- Swagger: `http://localhost:8080/swagger-ui/index.html`
- DB: PostgreSQL on `localhost:5432`

---

## Example requests

```bash
# Login
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"admin\",\"password\":\"admin123\"}"

# Create product (use accessToken from login)
curl -X POST http://localhost:8080/api/v1/products \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"Laptop\",\"sku\":\"LAP-001\",\"price\":29999.99}"

# Create stock
curl -X POST http://localhost:8080/api/v1/products/1/stock \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d "{\"quantity\":100}"

# Stock in / out
curl -X POST http://localhost:8080/api/v1/products/1/stock/in \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d "{\"quantity\":10,\"note\":\"Purchase\"}"
```

---

## Tests

```bash
mvn test
```

Covers service unit tests (Mockito) and controller/auth integration tests (`@SpringBootTest` + MockMvc, `test` profile with H2).

---

## Configuration profiles

| Profile | Purpose |
|---------|---------|
| `dev` (default) | PostgreSQL + Flyway + SQL logging |
| `test` | In-memory H2, Flyway off, `ddl-auto: create-drop` |
| `prod` | Env-based DB credentials, quieter logging |

JWT secret: `app.jwt.secret` / `JWT_SECRET` (must be long enough for HS256).

---

## License

Portfolio / learning project.
