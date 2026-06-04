# Ticket özeti

| # | Başlık | Durum | Not |
|---|--------|-------|-----|
| 1 | Ortam: Spring Boot + PostgreSQL | Tamamlandı | Locale `C`, DB `stock_order_payment`, servis çalışıyor, app 8080 |
| 2 | Product vertical slice (MVP) | Tamamlandı | CRUD-ish API, validation, 409/404, Swagger smoke |
| 3 | Stock modülü (MVP) | Devam ediyor | Adım 1–3 bitti; sırada Service, Controller, smoke |

---

## Ticket 1 — Ortam ve PostgreSQL

Spring Boot bağımlılıkları, `application.yml`, PostgreSQL 18 kurulumu (initdb için locale **C**), Windows servisi, veritabanı oluşturma, uygulamanın DB’ye bağlanarak ayağa kalkması.

**Bitti kriteri:** `Started StockOrderPaymentApplication` + Hikari havuz + PostgreSQL 18.3 bağlantısı.

---

## Ticket 2 — Product vertical slice (MVP)

**Scope:** Ürün kataloğu için ilk dikey dilim — entity, repository, service, REST, DTO + validation. Stok/sipariş/ödeme bu ticket’ta **yok** (scope creep).

**Kabul kriterleri (AC):**
- `POST /api/v1/products` — ürün oluşturur (ör. name, sku, price)
- `GET /api/v1/products` — liste
- `GET /api/v1/products/{id}` — tek kayıt
- Geçersiz istek → anlamlı **400** (validation)
- pgAdmin: `products` tablosu oluşur; kayıt görünür
- Swagger’da endpoint’ler görünür

**DoD:** App ayağa kalkıyor; Swagger veya curl ile smoke test geçti; bu ticket `TICKETS.md`’de tamamlandı işaretlendi.

**Önerilen paket yapısı:** `domain` / `repository` / `service` / `web` (controller + dto)

---

## Ticket 3 — Stock modülü (MVP)

**Scope:** Ürün başına tek stok kaydı. Ayrı lifecycle (Product API’ye dokunulmaz).

**Endpoint’ler:** `POST/GET/PATCH /api/v1/products/{productId}/stock`

**Kurallar:** Product yok → 404; stok zaten var → 409; quantity negatif → 400

**İlerleme:**

| Adım | Durum | Dosyalar |
|------|--------|----------|
| 1 Entity | Tamam | `domain/stock/Stock.java` |
| 2 Repository | Tamam | `repository/stock/StockRepository.java` |
| 3 DTO | Tamam | `CreateStockRequest`, `UpdateStockRequest`, `StockResponse` |
| 4 Service | Bekliyor | — |
| 5 Controller | Bekliyor | — |
| 6 Smoke test | Bekliyor | Swagger + pgAdmin `stock` |

**Sıradaki:** Adım 4 — `StockService` + exception’lar.

---

## Şablon (yeni ticket)

```
## Ticket N — Kısa başlık
Tek cümle kapsam.
Bitti kriteri: …
```
