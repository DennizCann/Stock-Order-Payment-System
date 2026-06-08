# Stock–Order–Payment System

> **Work in progress** — Aktif geliştirilen bir portföy backend projesi.  
> Amaç: E-ticaret / perakende ortamlarındaki **stok, sipariş ve ödeme** akışlarını gerçekçi bir mimariyle simüle etmek.

---

## Proje vizyonu

Tutorial seviyesinde basit CRUD değil; aşağıdaki iş akışlarını hedefleyen bir backend:

```
Ürün kataloğu → Stok yönetimi → Sipariş oluşturma → Ödeme → Async callback
```

Ödeme sağlayıcı simülasyonu, stok–sipariş tutarlılığı ve asenkron callback gibi gerçek sistemlerde görülen senaryolar üzerinde pratik yapmak için tasarlandı.

---

## Şu an çalışan özellikler

| Modül | Durum | Açıklama |
|--------|--------|----------|
| **Product** | Tamamlandı | Ürün oluşturma, listeleme, id ile sorgulama |
| **Stock** | Tamamlandı | Ürün başına stok tanımı, okuma, miktar güncelleme (SET) |
| **Order** | Planlandı | — |
| **Payment** | Planlandı | — |
| **Async callback** | Planlandı | — |

### API özeti

**Products**

| Method | Endpoint | Açıklama |
|--------|----------|----------|
| `POST` | `/api/v1/products` | Yeni ürün |
| `GET` | `/api/v1/products` | Ürün listesi |
| `GET` | `/api/v1/products/{id}` | Tek ürün |

**Stock** (ürüne bağlı)

| Method | Endpoint | Açıklama |
|--------|----------|----------|
| `POST` | `/api/v1/products/{productId}/stock` | İlk stok kaydı |
| `GET` | `/api/v1/products/{productId}/stock` | Stok sorgula |
| `PATCH` | `/api/v1/products/{productId}/stock` | Miktarı güncelle |

**Hata yönetimi:** Validation → `400`, bulunamayan kayıt → `404`, çift SKU / çift stok → `409`.

---

## Yol haritası

- [x] Spring Boot + PostgreSQL altyapısı
- [x] Product vertical slice (entity → API)
- [x] Stock modülü (FK `product_id`, nested REST)
- [ ] Order oluşturma ve stok rezervasyonu
- [ ] Payment akışı ve provider simülasyonu
- [ ] Asenkron ödeme callback’i
- [ ] Flyway/Liquibase migration (şu an `ddl-auto: update`)
- [ ] Unit / integration testler
- [ ] Docker, JWT, Kafka (opsiyonel genişletmeler)

İlerleme detayı: [`TICKETS.md`](TICKETS.md)

---

## Tech stack

| Katman | Teknoloji |
|--------|-----------|
| Runtime | Java 21 |
| Framework | Spring Boot 3.4 |
| Persistence | Spring Data JPA, Hibernate |
| Database | PostgreSQL |
| API | REST, Bean Validation |
| Docs | Springdoc OpenAPI (Swagger UI) |
| Build | Maven |
| Boilerplate | Lombok |

---

## Mimari

Katmanlı, service-oriented yapı:

```
Controller  →  Service  →  Repository  →  Entity  →  PostgreSQL
     ↑              ↑
   DTO + @Valid   İş kuralları
```

- **DTO:** API sözleşmesi; entity dışarı sızdırılmaz.
- **GlobalExceptionHandler:** Domain exception’lar anlamlı HTTP status’lara map edilir.

```
com.denizcan.stockorderpayment
├── domain/          # JPA entity'ler
├── repository/      # Spring Data JPA
├── service/         # İş kuralları
├── web/             # Controller, DTO, exception handler
└── exception/       # Domain exception'lar
```

---

## Gereksinimler

- JDK 21
- Maven 3.9+
- PostgreSQL 16+ (yerelde 18 ile test edildi)

---

## Yerelde çalıştırma

### 1. Veritabanı

PostgreSQL’de veritabanı oluştur:

```sql
CREATE DATABASE stock_order_payment;
```

`src/main/resources/application.yml` içindeki kullanıcı, şifre ve port kendi kurulumunla uyumlu olmalı (varsayılan: `postgres` / `postgres`, port `5432`).

### 2. Uygulama

```bash
mvn spring-boot:run
```

veya IntelliJ’den `StockOrderPaymentApplication` sınıfını çalıştır.

### 3. API dokümantasyonu

Uygulama ayaktayken:

- Swagger UI: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
- OpenAPI JSON: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

### 4. Hızlı smoke test

1. `POST /api/v1/products` ile ürün oluştur.
2. Dönen `id` ile `POST /api/v1/products/{id}/stock` — örnek body: `{ "quantity": 100 }`.
3. `GET /api/v1/products/{id}/stock` ile doğrula.

---

## Notlar

- Geliştirme ortamında şema `spring.jpa.hibernate.ddl-auto: update` ile yönetiliyor; production benzeri ortamda migration aracına geçilmesi planlanıyor.
- Proje bilinçli olarak küçük ticket’larla ilerletiliyor; her modül aynı vertical slice kalıbını tekrarlar.

---

## Lisans

Bu proje portföy / öğrenme amaçlıdır.
