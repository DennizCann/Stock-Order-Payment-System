# Company deployment and recovery notes

This is a preparation guide, not approval to use the current build for live business operations. See [the remaining requirements](PRODUCT_ROADMAP.md). In particular, live marketplace order import, provider status updates, display/non-saleable stock categories and operational acceptance are incomplete.

## Build and first owner

Build with Java 21 and `mvn verify`. The runnable application is `target/Stock-Order-Payment-System-1.0-SNAPSHOT.jar`.

Use a separate PostgreSQL database and configure the service environment:

- `SPRING_PROFILES_ACTIVE=prod`
- `DB_URL=jdbc:postgresql://your-database-host:5432/operations`
- `DB_USERNAME` and `DB_PASSWORD` for the deployment database user
- `JWT_SECRET`: a unique, securely generated secret of at least 32 bytes; do not use the development fallback
- `APP_BOOTSTRAP_ADMIN_USERNAME` and `APP_BOOTSTRAP_ADMIN_PASSWORD` for the first owner (password at least 12 characters)

Start with `java -jar target/Stock-Order-Payment-System-1.0-SNAPSHOT.jar`. Flyway applies versioned migrations; Hibernate validates the result. The `prod` profile does not create the development `admin` or `user` accounts. Bootstrap creates the supplied owner if absent and never silently overwrites an existing administrator's password. Remove bootstrap settings from the service after successful creation. Password recovery and owner-account administration still need a dedicated workflow before deployment.

Put the service behind HTTPS, restrict database network access, store secrets in the deployment's secret manager and keep database credentials out of Git. Choose a backup retention period and verify restore rehearsals before migrations. Test employee roles with a separate worker account; hiding a menu is not the authorization boundary. Production infrastructure, monitoring and external security review have not been accepted by a real company yet.

Keep `TRENDYOL_ENABLED=false` until an authorized test seller has accepted the adapter and the order/reconciliation workflow is ready. See [Trendyol setup and limitations](TRENDYOL_STOCK.md). Local simulator panels are development tools and must not be presented as real integrations.

## Backup and restore rehearsal

Use PostgreSQL client tools compatible with the server version. Set `PGHOST`, `PGPORT`, `PGUSER` and `PGDATABASE` for the intended database; use a protected password file or secret-injected password, rather than a password in a command argument. These are PostgreSQL client settings, separate from the application's JDBC `DB_URL`.

Create a custom-format backup in the approved backup destination:

```powershell
pg_dump --format=custom --file=operations-backup.dump
```

Restore into a **new, empty rehearsal database**, preserving the original database:

```powershell
createdb operations_restore_check
pg_restore --exit-on-error --no-owner --dbname=operations_restore_check operations-backup.dump
```

Verify Flyway history and important row counts, then start a separate application instance against the restored database with schema validation. Check owner login, roles, physical stock totals by location, stock movement history, order reservations and unresolved publication jobs. Keep real provider connections disabled during a rehearsal. A backup that has not been restored and checked is not recovery evidence. A database backup can contain credentials hashes and business data; use the same protected storage and access controls as the source.

Before a schema migration, record the deployed build and take a verified backup. Returning to an older Git commit alone does not undo a database migration. Restore the corresponding database into a separate candidate and verify the matching application before switching service traffic. Reconcile marketplace orders that arrived during downtime before publishing stock again.

## Manual order operations

**Satış Kanalları → Siparişler** provides shipment and cancellation for administrators and users explicitly granted operations-order management. Shipment consumes reserved stock; cancellation releases it. A repeated request does not apply a second stock change, and conflicting simultaneous actions are serialized. The operation history records who changed the state and when.

These actions update the local system only. They do not call a marketplace's shipment, cancellation, refund or payment API. Returns, partial shipment, payment settlement and provider-specific lifecycle handling remain unfinished.
