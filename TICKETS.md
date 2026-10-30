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
| 9 | Order domain and stock reservation | Done for initial flow | REST API, owner isolation, transactional reservations, row locking and concurrency tests |
| 10 | Payment simulation and callback | Planned | Provider abstraction and asynchronous callback flow |
| 11 | Pagination and filtering | Planned | Product list improvements |
| 12 | Optional Redis/Kafka integration | Planned | Add only when supported by a concrete use case |
| 13 | Channel order intake | Initial demo done | Five sources, SKU mapping, duplicate detection, payload conflicts |
| 14 | Separate channel simulators | Initial demo done | Five processes share a lightweight simulator library |
| 15 | Operations panel | Initial demo done | Product/stock management, channel import/sync, aggregate orders |
| 16 | Cargo integration and simulator | Planned | Shipment creation, label, tracking and delivery updates |

## Current priority

1. Owner review of the current local feature branch; prepare the pull request after review.
2. Add the separate cargo simulator and central shipment workflow.
3. Add cancellation, completion, branch-sale settlement and reservation release/consumption.
4. Add automatic polling, durable retry visibility and stock synchronization recovery.
5. Implement payment simulation and callbacks where required by the company-store flow.

Optional Redis/Kafka is not selected for this delivery.
