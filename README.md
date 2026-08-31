# Stock-Order-Payment System

A portfolio backend built with Java 21 and Spring Boot 3.4 to model product, stock, inventory, order, and payment workflows.

> **Work in progress:** Product, stock, inventory history, authentication, and the order-domain foundation are implemented. The order REST API is still in progress; payment integration is planned.

## Current status

| Module | Status | Details |
|---|---|---|
| Authentication | Done | JWT login with `ADMIN` and `USER` roles |
| Product | Done | Create, list, and get by id |
| Stock | Done | Create, query, adjust, stock-in, and stock-out |
| Inventory history | Done | Traceable transaction log per product |
| Order | In progress | Domain model, persistence, DTOs, service logic, stock reservation, and tests |
| Payment | Planned | Provider simulation and asynchronous callback flow |

## Technical highlights

- Versioned REST APIs with DTO validation and centralized exception handling
- Spring Security, JWT authentication, and role-based access control
- PostgreSQL persistence with Spring Data JPA/Hibernate and Flyway migrations
- Transactional stock movements and inventory history
- Order-domain modeling with stock reservation and release/consume operations
- Unit and integration tests with JUnit 5, Mockito, MockMvc, Spring Security Test, and H2
- Docker and Docker Compose for the application and PostgreSQL
- Springdoc OpenAPI documentation

## API overview

### Authentication

| Method | Endpoint | Access |
|---|---|---|
| `POST` | `/api/v1/auth/login` | Public |

### Products

| Method | Endpoint | Roles |
|---|---|---|
| `POST` | `/api/v1/products` | `ADMIN` |
| `GET` | `/api/v1/products` | `USER`, `ADMIN` |
| `GET` | `/api/v1/products/{id}` | `USER`, `ADMIN` |

### Stock and inventory

| Method | Endpoint | Roles |
|---|---|---|
| `POST` | `/api/v1/products/{productId}/stock` | `ADMIN` |
| `GET` | `/api/v1/products/{productId}/stock` | `USER`, `ADMIN` |
| `PATCH` | `/api/v1/products/{productId}/stock` | `ADMIN` |
| `POST` | `/api/v1/products/{productId}/stock/in` | `ADMIN` |
| `POST` | `/api/v1/products/{productId}/stock/out` | `ADMIN` |
| `GET` | `/api/v1/products/{productId}/inventory-transactions` | `USER`, `ADMIN` |

The order REST controller is not yet exposed publicly.

## Tech stack

| Area | Technology |
|---|---|
| Runtime | Java 21 |
| Framework | Spring Boot 3.4 |
| Security | Spring Security, JWT (JJWT) |
| Persistence | Spring Data JPA, Hibernate, Flyway |
| Database | PostgreSQL; H2 for tests |
| API | REST, Bean Validation, Springdoc OpenAPI |
| Build | Maven |
| Containers | Docker, Docker Compose |
| Testing | JUnit 5, Mockito, MockMvc, Spring Security Test |

## Architecture

```text
Controller -> Service -> Repository -> Entity -> PostgreSQL
     |           |
  DTO/@Valid   Business rules, transactions, inventory logging
```

```text
com.denizcan.stockorderpayment
|- config/       OpenAPI and seed data
|- domain/       Product, stock, inventory, user, and order models
|- repository/   Spring Data repositories
|- security/     JWT filter and SecurityFilterChain
|- service/      Product, stock, and order business logic
|- web/          Controllers, DTOs, validation, and exception handling
|- exception/    Domain-specific exceptions
```

## Run locally

Requirements: Java 21, Maven, and PostgreSQL.

```powershell
$env:SPRING_PROFILES_ACTIVE="dev"
$env:DB_URL="jdbc:postgresql://localhost:5432/stock_order_payment"
$env:DB_USERNAME="postgres"
$env:DB_PASSWORD="postgres"
mvn spring-boot:run
```

Swagger UI: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)

### Docker Compose

```bash
docker compose up --build
```

- Application: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- PostgreSQL: `localhost:5432`

## Tests

```bash
mvn test
```

The suite covers service unit tests, controller/authentication integration tests, order persistence, and stock-reservation behavior.

## Roadmap

- [x] Product and stock vertical slices
- [x] Flyway migrations and environment profiles
- [x] Dockerized local run path
- [x] Spring Security, JWT, and RBAC
- [x] Automated unit and integration tests
- [x] Inventory transaction history
- [x] Order domain model, persistence, service logic, and reservation tests
- [ ] Order REST controller and API documentation
- [ ] Payment provider simulation and asynchronous callback
- [ ] Pagination and filtering
- [ ] Optional Redis/Kafka integration

Progress details: [TICKETS.md](TICKETS.md)

## Demo credentials

The application seeds local demo users:

| Username | Password | Role |
|---|---|---|
| `admin` | `admin123` | `ADMIN` |
| `user` | `user123` | `USER` |

These credentials are for local development only and must be changed before any real deployment.

## License

Portfolio and learning project.
