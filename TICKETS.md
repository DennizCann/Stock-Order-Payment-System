# Ticket özeti

| # | Başlık | Durum | Not |
|---|--------|-------|-----|
| 1 | Ortam: Spring Boot + PostgreSQL | Tamamlandı | Locale `C`, DB `stock_order_payment` |
| 2 | Product vertical slice (MVP) | Tamamlandı | CRUD-ish API, validation, 409/404 |
| 3 | Stock modülü (MVP) | Tamamlandı | Nested URL, SET quantity |
| 4 | Flyway + profiles | Tamamlandı | V1–V3 migrations, dev/test/prod |
| 5 | Docker + Compose | Tamamlandı | Dockerfile + docker-compose.yml |
| 6 | Security + JWT + roles | Tamamlandı | ADMIN/USER, login, seed users |
| 7 | Unit + integration tests | Tamamlandı | Service + MockMvc IT |
| 8 | Inventory history | Tamamlandı | INITIAL/IN/OUT/ADJUSTMENT + API |

---

## Tamamlanan backlog (portfolio upgrades)

1. Stock MVP stabilize + docs sync
2. Flyway schema management + environment profiles
3. Dockerized run path
4. Spring Security + JWT + RBAC
5. Automated tests
6. Inventory transaction history (enterprise-like stock movements)

## Sonraki önerilen ticket’lar

- Order creation + stock reservation
- Payment provider simulation + async callback
- Pagination / filtering on product list
