# Stock-Order-Payment System

A Java 21 and Spring Boot 3.4 web application for company product, stock, location, employee and order operations.

> **Work in progress:** The central operations panel, order REST API, safe stock reservations,
> channel-order idempotency, and five independently running sales-channel simulators are implemented.
> Shipment/cargo simulation and payment completion are planned. See [the local demo guide](docs/DEMO.md).

The company-product requirements, verified deliveries and remaining gaps are tracked in
[the product roadmap](docs/PRODUCT_ROADMAP.md). By default marketplace traffic uses local simulators.
An optional Trendyol quantity-publication adapter is contract-tested and disabled by default;
this is not yet a completed live-marketplace integration or a production-ready release.

## Current status

| Module | Status | Details |
|---|---|---|
| Authentication and employees | Implemented | Invitations, configurable roles, personal permission exceptions, editable profiles/status, account-bound revocable JWT sessions |
| Branches and warehouses | Implemented | Delegated viewing/management, type/name editing, archive/restore safeguards |
| Product | Implemented | Create, edit, archive, name/value tags, filtering and consistent total quantity |
| Stock | Implemented | Single/batch receipts and issues, atomic internal transfers, configurable movement names |
| Inventory history | Done | Traceable transaction log per product |
| Order | Initial lifecycle implemented | Authenticated create/read, owner isolation, reservations, local shipment/cancellation and audit |
| Channel integration | Partial | Demo import/sync; configurable caps and durable Trendyol quantity publication; real order import not implemented |
| Operations panel | Implemented for current workflows | Five landing sections with separate task screens and permission-aware navigation |
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

| Method | Endpoint | Permission (administrator also allowed) |
|---|---|---|
| `POST` | `/api/v1/products` | `PRODUCTS_CREATE` |
| `GET` | `/api/v1/products` | `PRODUCTS_VIEW` or `STOCK_VIEW` |
| `GET` | `/api/v1/products/{id}` | `PRODUCTS_VIEW` or `STOCK_VIEW` |
| `PUT` | `/api/v1/products/{id}` | `PRODUCTS_EDIT` |
| `DELETE` | `/api/v1/products/{id}` | `PRODUCTS_DELETE`; archive empty product |

### Stock and inventory

| Method | Endpoint | Permission (administrator also allowed) |
|---|---|---|
| `POST` | `/api/v1/products/{productId}/stock` | `STOCK_MANAGE` |
| `GET` | `/api/v1/products/{productId}/stock` | `STOCK_VIEW` |
| `PATCH` | `/api/v1/products/{productId}/stock` | `STOCK_MANAGE` |
| `POST` | `/api/v1/products/{productId}/stock/in` | `STOCK_MANAGE` |
| `POST` | `/api/v1/products/{productId}/stock/out` | `STOCK_MANAGE` |
| `GET` | `/api/v1/products/{productId}/inventory-transactions` | `STOCK_VIEW` |
| `POST` | `/api/v1/inventory-operations` | `STOCK_MANAGE`, own `BRANCH_STOCK_MANAGE`, or `STOCK_TRANSFER`, depending on kind/location |

### Orders and operations

| Method | Endpoint | Access |
|---|---|---|
| POST | `/api/v1/orders` | `ORDERS_CREATE` or ADMIN; identity is taken from authentication |
| GET | `/api/v1/orders` | `ORDERS_VIEW` or ADMIN; own orders |
| GET | `/api/v1/orders/{id}` | `ORDERS_VIEW` or ADMIN; own order |
| GET | `/api/v1/operations/orders` | `OPERATIONS_ORDERS_VIEW` or ADMIN; all channels |
| PUT | `/api/v1/operations/orders/{id}/state` | `OPERATIONS_ORDERS_MANAGE` or ADMIN; local shipment/cancellation |
| GET | `/api/v1/operations/orders/{id}/history` | `OPERATIONS_ORDERS_VIEW` or ADMIN |
| POST | `/api/v1/operations/channels/{source}/orders` | ADMIN; idempotent import by source and external id |
| POST | `/api/v1/operations/channels/import` | ADMIN; import pending orders over HTTP |
| POST | `/api/v1/operations/channels/sync-stock` | ADMIN; send versioned stock snapshots |

Public order requests contain `items` with `productId` and `quantity`; user ids are
not accepted as an ownership authority. Channel requests contain `externalOrderId`
and items with `sku` and `quantity`. Current external line prices use the central
catalog. The real Trendyol adapter currently publishes quantities only; provider order
import, price reconciliation and provider-side order status updates remain incomplete.

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

Set a private, random `JWT_SECRET` of at least 32 bytes. For first-time administrator
setup, supply `APP_BOOTSTRAP_ADMIN_USERNAME` and `APP_BOOTSTRAP_ADMIN_PASSWORD`
(at least 12 characters) through the deployment environment. Remove the two
bootstrap variables after successful setup. The bootstrap creates an administrator
under an unused name and leaves existing credentials intact.

```bash
docker compose up --build
```

- Application: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- PostgreSQL: `localhost:5432`

An administrator can open **Kullanıcılar** in the panel to create a regular
employee account and copy a single-use password setup link. The link expires after
24 hours. Generating a fresh link for a pending account invalidates the previous link.
Use the panel's shared address when creating a link for a different computer.

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
- [x] Editable accounts, delegated branch/warehouse management and session revocation
- [x] Atomic multi-product receipts, issues and internal transfers
- [x] Configurable channel stock caps and durable Trendyol quantity publication (contract-tested; no live seller acceptance yet)
- [ ] Automatic polling, durable retries and provider-specific adapters
- [ ] Cargo simulator, shipment lifecycle and tracking
- [ ] Payment provider simulation and asynchronous callback
- [ ] Pagination and filtering
- [ ] Optional Redis/Kafka integration

Progress details: [TICKETS.md](TICKETS.md)

Current company-product progress: [PRODUCT_ROADMAP.md](docs/PRODUCT_ROADMAP.md).
First real-provider stock adapter setup and limits: [TRENDYOL_STOCK.md](docs/TRENDYOL_STOCK.md).
Deployment preparation and recovery: [COMPANY_RUNBOOK.md](docs/COMPANY_RUNBOOK.md).

## Demo credentials

Fresh development installations seed the administrator only. The explicit `demo`
and `test` profiles also seed the sample worker below. Production uses explicit
owner bootstrap settings. Existing accounts are never deleted by this initializer.

| Username | Password | Role |
|---|---|---|
| `admin` | `admin123` | `ADMIN` |
| `user` | `user123` | `USER` |

These credentials are for local development only and must be changed before any real deployment.

## License

Portfolio and learning project.

Developed with AI assistance. Work is reviewed in feature branches and is intended
to be studied and explained by the project owner. See [delivery notes](docs/delivery/01-order-api.md).
