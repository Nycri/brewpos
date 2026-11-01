# BrewPOS — Testing Guide

This guide walks another group or evaluator through testing **BrewPOS** after completing the setup steps in [README.md](README.md).

---

## Part 1: Browser Walkthrough (http://localhost:8081)

Start the app (`.\mvnw.cmd spring-boot:run` on Windows or `./mvnw spring-boot:run` on macOS/Linux) and open **http://localhost:8081**.

### 1. Check Starter Data on the Register
- Confirm the **Register** screen loads categories (`Coffee`, `Non-Coffee`, `Pastries`) and active menu tiles.
- Confirm **`Pumpkin Spice Latte`** is **not** shown on the Register (it is seeded as `Inactive` in `seed.sql` to test active-only filtering).

### 2. Test Cashier vs. Manager View
- Keep **`Demo Cashier`** selected in the top-right dropdown and click **Menu**.
  - Confirm a yellow read-only notice appears and add/edit buttons are hidden.
- Switch the top-right dropdown to **`Demo Manager`**.
  - Confirm the **New Category** and **New Menu Item** forms appear.

### 3. Add a Category & Menu Item
1. In **Menu** (as `Demo Manager`), type **`Tea Specials`** under **New Category** and click **Add**.
2. Refresh the browser (`F5`) and confirm `Tea Specials` stays saved in PostgreSQL.
3. Under **New Menu Item**, enter:
   - **Item name:** `Hojicha Latte`
   - **Category:** `Tea Specials`
   - **Price:** `115.00`
   - **Active:** Checked
   - Click **Add Item**.
4. Click **Register** and confirm `Hojicha Latte` (`115.00`) now appears on the menu.

### 4. Deactivate & Reactivate an Item
1. Go to **Menu** (as `Demo Manager`) and click **Deactivate** next to `Hojicha Latte`.
2. Click **Register** and confirm `Hojicha Latte` disappears from the order screen.
3. Return to **Menu** and click **Activate** on `Hojicha Latte` so it appears on **Register** again.

### 5. Place a Cash Order & Verify Duplicate Merging
1. On **Register**, click **`Hojicha Latte`** **twice** -> confirm **Current Ticket** combines it into **1 row** with quantity **`2`**.
2. Click **`Butter Croissant`** (`75.50`) once.
3. Keep payment set to **`Cash`** and click **`Charge · 305.50`**.
4. Confirm the green **Order Completed** receipt banner appears with total **`305.50`** (note the **Order #**).

### 6. Verify Past Receipts Keep Their Original Sold Price
1. Go to **Menu** (as `Demo Manager`), click **Edit** on `Hojicha Latte`, change its price from `115.00` to **`150.00`**, and click **Save**.
2. Click **Orders** in the top navigation bar, find the order you placed in Step 5, and click **Receipt**.
3. Confirm `Hojicha Latte` on that past receipt still shows Unit Price **`115.00`** and Total **`305.50`** (unchanged by the new `150.00` menu price).

### 7. Place an Online Order & Check Daily Sales
1. Go to **Register**, click **`Hojicha Latte`** once (now **`150.00`**), toggle payment to **`Online`**, and click **`Charge · 150.00`**.
2. Click **Orders** -> confirm both new orders appear at the top of the list. Try clicking **Today** and **All** to test date filtering.
3. Click **Daily Sales** -> confirm **Net Sales**, **Completed Orders**, and the **Cash / Online** breakdown update to include your new orders.

### 8. Check Input Validation
- On **Register**, confirm **Charge Order** is disabled when the ticket is empty.
- In **Menu** (as `Demo Manager`), try adding another category named **`Coffee`** -> confirm an error message prevents duplicate categories.

---

## Part 2: Optional SQL Check (pgAdmin 4 or `psql`)

You can run these queries in **pgAdmin 4 Query Tool** or **`psql`** on `grp_brewposdb` to verify the database directly:

```sql
-- 1. Confirm strictly 5 tables exist in public
SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
ORDER BY table_name;

-- 2. Compare today's Daily Sales directly from SQL against the Daily Sales screen
SELECT COALESCE(SUM(l.quantity * l.sold_price), 0) AS net_sales,
       COUNT(DISTINCT t.order_id)                  AS completed_orders
FROM public.order_ticket t
JOIN public.order_line_item l ON l.order_id = t.order_id
WHERE t.order_timestamp >= CURRENT_DATE
  AND t.order_timestamp <  CURRENT_DATE + 1;
```

---

## Part 3: Automated JUnit Tests (Optional)

The project includes **19 automated tests** covering schema validation, business rules, transaction rollback, and REST API endpoints.

Once your `.env` file is set up (from [README.md](README.md)) and PostgreSQL is running, simply run:

- **Windows (PowerShell):**
  ```powershell
  .\mvnw.cmd test
  ```
- **macOS / Linux:**
  ```bash
  ./mvnw test
  ```

Expected output:
```text
Tests run: 19, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```
