# Stock-Order-Payment System

A portfolio backend built with Java 21 and Spring Boot 3.4 to model product, stock, inventory, order, and payment workflows.

> **Work in progress:** The central operations panel, order REST API, safe stock reservations,
> channel-order idempotency, and five independently running sales-channel simulators are implemented.
> Shipment/cargo simulation and payment completion are planned. See [the local demo guide](docs/DEMO.md).

## Current status

| Module | Status | Details |
|---|---|---|
| Authentication | Done | JWT login with `ADMIN` and `USER` roles |
| Product | Done | Create, list, and get by id |
| Stock | Done | Create, query, adjust, stock-in, and stock-out |
| Inventory history | Done | Traceable transaction log per product |
| Order | Done for initial flow | Authenticated create/read, owner isolation, concurrent stock reservations |
| Channel integration | Demo implemented | SKU-based import, duplicate detection, versioned stock snapshots, manual synchronization |
| Operations panel | Initial version | Product/stock entry, channel synchronization, aggregate order list |
| External simulators | Demo implemented | Separate Trendyol, Hepsiburada, n11, webstore and branch processes |
| Cargo | Planned | Separate carrier simulator, shipment creation and tracking |
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

### Orders and operations

| Method | Endpoint | Access |
|---|---|---|
| POST | `/api/v1/orders` | USER, ADMIN; identity is taken from authentication |
| GET | `/api/v1/orders` | Own orders |
| GET | `/api/v1/orders/{id}` | Own order |
| GET | `/api/v1/operations/orders` | ADMIN; all channels |
| POST | `/api/v1/operations/channels/{source}/orders` | ADMIN; idempotent import by source and external id |
| POST | `/api/v1/operations/channels/import` | ADMIN; import pending orders over HTTP |
| POST | `/api/v1/operations/channels/sync-stock` | ADMIN; send versioned stock snapshots |

Public order requests contain `items` with `productId` and `quantity`; user ids are
not accepted as an ownership authority. Channel requests contain `externalOrderId`
and items with `sku` and `quantity`. Current external line prices use the central
catalog. External provider-specific adapters are outside the demo's current scope.

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
- [x] Order REST controller and API documentation
- [x] Pessimistic stock locking and consistent lock ordering
- [x] Idempotent channel import and payload conflict detection
- [x] Central operations panel and five sales-channel simulator applications
- [x] Versioned stock synchronization and local HTTP demo verification
- [ ] Automatic polling, durable retries and provider-specific adapters
- [ ] Cargo simulator, shipment lifecycle and tracking
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

Developed with AI assistance. Work is reviewed in feature branches and is intended
to be studied and explained by the project owner. See [delivery notes](docs/delivery/01-order-api.md).
