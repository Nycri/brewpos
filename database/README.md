# BrewPOS — SQL Scripts & Database Administration Guide

This folder contains all SQL scripts needed to create, seed, verify, and maintain the **`grp_brewposdb`** PostgreSQL database.

For full details on every table, column, key, and constraint, see **[DATABASE_DOCUMENTATION.md](DATABASE_DOCUMENTATION.md)**.

---

## 1. Which SQL Files Should You Run?

For a **brand-new setup**, you only need **`schema.sql`** and **`seed.sql`** (plus `05_create_app_role.sql` if you want to test the restricted database role):

| File | Purpose |
|---|---|
| **[schema.sql](schema.sql)** | **Main Schema Script.** Creates all 5 tables, constraints, and indexes on a new/empty `grp_brewposdb`. |
| **[seed.sql](seed.sql)** | **Main Seeder Script.** Loads starter staff, categories, menu items (10 active + 1 inactive), and 5 sample orders so the app is ready to test right away. Safe to run multiple times. |
| [05_create_app_role.sql](05_create_app_role.sql) | Creates the restricted `brewpos_app` database user (can insert orders and manage menu items, but cannot edit/delete completed orders). |
| [03_verification_queries.sql](03_verification_queries.sql) | Read-only check queries to verify tables, keys, constraints, indexes, and sales totals. |
| [06_align_existing_db.sql](06_align_existing_db.sql) | Updates an older existing `grp_brewposdb` safely without deleting data. |
| [01_create_tables.sql](01_create_tables.sql) | Standalone table creation script (same tables/indexes as `schema.sql`). |
| [02_seed_reference_data.sql](02_seed_reference_data.sql) | Seeds only staff, categories, and menu items (without sample orders). |
| [04_sample_orders.sql](04_sample_orders.sql) | Seeds only the 5 sample orders (skipped automatically if orders already exist). |

> **Note:** Never run `schema.sql` or `01_create_tables.sql` on a database that already has tables—they contain no `DROP` statements so existing data is never overwritten by mistake.

---

## 2. Quick Database Setup

### Using pgAdmin 4
1. Create a new database named **`grp_brewposdb`**.
2. Open **Query Tool** on `grp_brewposdb`.
3. Open and execute **`database/schema.sql`**.
4. Open and execute **`database/seed.sql`**.
5. *(Optional)* Open and execute **`database/05_create_app_role.sql`**, then run:
   ```sql
   ALTER ROLE brewpos_app WITH PASSWORD 'your_chosen_password';
   ```

### Using `psql` Command Line
Run from the project root folder:

```bash
psql -U postgres -d postgres -c "CREATE DATABASE grp_brewposdb;"
psql -U postgres -d grp_brewposdb -f database/schema.sql
psql -U postgres -d grp_brewposdb -f database/seed.sql
```

*(Optional — create `brewpos_app` and set its password:)*
```bash
psql -U postgres -d grp_brewposdb -f database/05_create_app_role.sql
psql -U postgres -d grp_brewposdb -c "\password brewpos_app"
```

---

## 3. Verifying the Database

To confirm all 5 tables, constraints, indexes, and sample totals are working properly:

```bash
psql -U postgres -d grp_brewposdb -f database/03_verification_queries.sql
```
*(Or open `database/03_verification_queries.sql` inside pgAdmin 4 Query Tool and run any section.)*

---

## 4. Backup & Restore (`pg_dump` / `pg_restore`)

### Create a Backup
```bash
pg_dump -U postgres -Fc -d grp_brewposdb -f brewpos_backup.dump
```

### Restore from a Backup
> **Warning:** `--clean` replaces existing tables in `grp_brewposdb` with the backup copy.
```bash
pg_restore -U postgres -d grp_brewposdb --clean --if-exists brewpos_backup.dump
```
