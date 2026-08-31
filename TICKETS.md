# Development tickets

| # | Item | Status | Notes |
|---|---|---|---|
| 1 | Spring Boot and PostgreSQL environment | Done | Database and application baseline |
| 2 | Product vertical slice | Done | REST API, validation, 404/409 handling |
| 3 | Stock module | Done | Quantity updates, stock-in, and stock-out |
| 4 | Flyway and environment profiles | Done | Development, test, and production profiles |
| 5 | Docker and Docker Compose | Done | Application and PostgreSQL containers |
| 6 | Security, JWT, and roles | Done | Login, `ADMIN`/`USER`, seeded demo users |
| 7 | Unit and integration tests | Done | Service tests and MockMvc integration tests |
| 8 | Inventory history | Done | Traceable stock movements and query API |
| 9 | Order domain and stock reservation | In progress | Model, persistence, DTOs, service, migrations, and tests implemented; REST controller pending |
| 10 | Payment simulation and callback | Planned | Provider abstraction and asynchronous callback flow |
| 11 | Pagination and filtering | Planned | Product list improvements |
| 12 | Optional Redis/Kafka integration | Planned | Add only when supported by a concrete use case |

## Current priority

1. Expose the order workflow through a REST controller.
2. Document order endpoints in OpenAPI and the README.
3. Complete end-to-end order integration tests.
4. Implement the payment provider simulation and asynchronous callback flow.
