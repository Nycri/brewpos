# BrewPOS — Café Point-of-Sale System

## 1. Overview

**BrewPOS** is a web-based Café Point-of-Sale (POS) application built with **Java Spring Boot** and **PostgreSQL**. It is designed to demonstrate relational database design, data integrity, and everyday café register operations.

### Main Features
- **Register:** Tap active menu items to build an order, adjust quantities, choose **Cash** or **Online**, and charge the order.
- **Menu:** Create and edit categories, add or update menu items, and activate or deactivate items on the register.
- **Orders:** Browse completed orders, filter by date, and open itemized receipts.
- **Daily Sales:** View daily net sales, total completed orders, and a **Cash vs. Online** breakdown.

### Tech Stack
- **Database:** PostgreSQL (`grp_brewposdb`, schema `public`)
- **Back End:** Java 17, Spring Boot 4.1.1, Spring Data JPA, Bean Validation
- **Front End:** HTML5, Bootstrap 5, CSS, and Vanilla JavaScript
- **Build Tool:** Included Maven Wrapper (`mvnw.cmd` / `./mvnw` — no separate Maven install required)

---

## 2. Project Documentation & Key Files

| Requirement | File Path | Purpose |
|---|---|---|
| **Setup & Run Guide** | [README.md](README.md) | Overview, prerequisites, IDE extensions, PowerShell tutorial, and execution guide. |
| **Environment Template** | [.env.example](.env.example) | Sample configuration file (no real passwords included). |
| **Database Schema Script** | [database/schema.sql](database/schema.sql) | Creates the 5 database tables, keys, constraints, and indexes. |
| **Sample Data Seeder** | [database/seed.sql](database/seed.sql) | Inserts demo staff, categories, menu items, and sample orders for instant testing. |
| **Database Documentation** | [database/DATABASE_DOCUMENTATION.md](database/DATABASE_DOCUMENTATION.md) | Full guide to `grp_brewposdb`, tables, ERD, keys, constraints, and objects. |
| **User Roles & Security** | [docs/user-roles.md](docs/user-roles.md) | Detailed mapping of application roles to PostgreSQL database roles and privileges. |
| **SQL Scripts & DBA Guide** | [database/README.md](database/README.md) | Overview of all SQL scripts, database role setup, and backup/restore steps. |
| **Testing Walkthrough** | [TESTING_GUIDE.md](TESTING_GUIDE.md) | Easy checklist for testing all features in the browser and running automated tests. |
| **Activity 4: Access Control** | [access-control/](access-control/) | RBAC role definitions, least-privilege verification, and audit reports across peer groups (`G02`–`G11`). |
| **Activity 5: Maintenance & Monitoring** | [activity-5/](activity-5/) | Health inspections, bloat audits, `VACUUM`/`ANALYZE`, and `EXPLAIN (ANALYZE, BUFFERS)` profiling. |
| **Activity 5: Technical Evidence** | [activity-5/Evidence-act5/](activity-5/Evidence-act5/) | Concrete diagnostic queries, raw outputs, server logs, and execution plan metrics. |
| **Activity 6: Backup & Recovery** | [activity-6/](activity-6/) | Compressed backups (`pg_dump -Fc`), SHA-256 hashes, test restorations, and disaster recovery drills. |
| **Activity 6: Technical Evidence** | [activity-6/Evidence-act6/](activity-6/Evidence-act6/) | Checksum verification outputs, `pg_restore -l` archives, row parity proofs, and drill transcripts. |

---

### 2.1 COMP 018: Activities 4, 5, and 6 Database Administration Evaluation Repository

As part of the **COMP 018: Database Administration** coursework, **Group 4 (BrewPOS)** performed comprehensive database administration audits on 8 peer group projects:

| Group Code | Evaluated Peer Group | Project Monitored | Primary Database | Activity 4 (Access Control) | Activity 5 (Maintenance) | Activity 6 (Backup & Recovery) |
|---|---|---|---|:---:|:---:|:---:|
| **G02** | Group 02 | Park-It: Parking Management System | `grp_parkitdb` | [G02_Act-4.md](access-control/G02_Act-4.md) | [G02_Act-5.md](activity-5/G02_Act-5.md) ([Evidence](activity-5/Evidence-act5/G02_Act-5_Evidence.md)) | [G02_Act-6.md](activity-6/G02_Act-6.md) ([Evidence](activity-6/Evidence-act6/G02_Act-6_Evidence.md)) |
| **G03** | Group 03 | OJT Management System | `ojt_management` | [G03_Act-4.md](access-control/G03_Act-4.md) | [G03_Act-5.md](activity-5/G03_Act-5.md) ([Evidence](activity-5/Evidence-act5/G03_Act-5_Evidence.md)) | [G03_Act-6.md](activity-6/G03_Act-6.md) ([Evidence](activity-6/Evidence-act6/G03_Act-6_Evidence.md)) |
| **G05** | Group 05 | PUP-SPOT: Campus Lost and Found | `grp05_lostandfounddb` | [G05_Act-4.md](access-control/G05_Act-4.md) | [G05_Act-5.md](activity-5/G05_Act-5.md) ([Evidence](activity-5/Evidence-act5/G05_Act-5_Evidence.md)) | [G05_Act-6.md](activity-6/G05_Act-6.md) ([Evidence](activity-6/Evidence-act6/G05_Act-6_Evidence.md)) |
| **G06** | Group 06 | HabalGo: Ride and Booking System | `grp00_habalgodb` | [G06_Act-4.md](access-control/G06_Act-4.md) | [G06_Act-5.md](activity-5/G06_Act-5.md) ([Evidence](activity-5/Evidence-act5/G06_Act-5_Evidence.md)) | [G06_Act-6.md](activity-6/G06_Act-6.md) ([Evidence](activity-6/Evidence-act6/G06_Act-6_Evidence.md)) |
| **G07** | Group 07 | CampusSafe: Safety Reporting System | `grp07_campussafedb` | [G07_Act-4.md](access-control/G07_Act-4.md) | [G07_Act-5.md](activity-5/G07_Act-5.md) ([Evidence](activity-5/Evidence-act5/G07_Act-5_Evidence.md)) | [G07_Act-6.md](activity-6/G07_Act-6.md) ([Evidence](activity-6/Evidence-act6/G07_Act-6_Evidence.md)) |
| **G08** | Group 08 | Campus Canteen Pre-Order & Queue | `canteen_db` | [G08_Act-4.md](access-control/G08_Act-4.md) | [G08_Act-5.md](activity-5/G08_Act-5.md) ([Evidence](activity-5/Evidence-act5/G08_Act-5_Evidence.md)) | [G08_Act-6.md](activity-6/G08_Act-6.md) ([Evidence](activity-6/Evidence-act6/G08_Act-6_Evidence.md)) |
| **G10** | Group 10 | Restaurant Online Ordering & Reservation | `grp_binarysisters_projectdb` | [G10_Act-4.md](access-control/G10_Act-4.md) | [G10_Act-5.md](activity-5/G10_Act-5.md) ([Evidence](activity-5/Evidence-act5/G10_Act-5_Evidence.md)) | [G10_Act-6.md](activity-6/G10_Act-6.md) ([Evidence](activity-6/Evidence-act6/G10_Act-6_Evidence.md)) |
| **G11** | Group 11 | BoardTrack: Boarding Rental System | `boardtrack_db` | [G11_Act-4.md](access-control/G11_Act-4.md) | [G11_Act-5.md](activity-5/G11_Act-5.md) ([Evidence](activity-5/Evidence-act5/G11_Act-5_Evidence.md)) | [G11_Act-6.md](activity-6/G11_Act-6.md) ([Evidence](activity-6/Evidence-act6/G11_Act-6_Evidence.md)) |

---

## 3. Prerequisites & Required IDE Extension Packs

### 3.1 System Software Requirements
Before running BrewPOS on your local device, make sure the following software is installed:

1. **Git** — to clone the repository (or download and extract the repository ZIP from GitHub).
2. **Java Development Kit (JDK 17 or newer)** — verify by running `java -version` and `javac -version` in PowerShell/Terminal.
3. **PostgreSQL (v16, v17, or v18 recommended)** — running locally (default port `5432`), with **pgAdmin 4** or **`psql`** installed.
4. **Modern Web Browser** — Google Chrome, Microsoft Edge, Firefox, or Safari.
5. **Internet Connection (first run only)** — needed once so the Maven Wrapper (`mvnw.cmd`) or your IDE can download the required Spring Boot and PostgreSQL libraries automatically.

### 3.2 Required Local Compiler / IDE Extension Packs
> **Important:** If you are opening, compiling, or running BrewPOS inside a local code editor or IDE, **you must install all necessary Java and Spring Boot extension packs first**. Without these extensions, your IDE will not recognize `pom.xml`, Java imports will appear unresolved, and the **Run / Debug** button on `BrewPosApplication.java` will not show up.

Install the required packs for whichever IDE/compiler you are using:

- **Visual Studio Code / Cursor / Antigravity / Windsurf:**
  Open the **Extensions** sidebar (`Ctrl + Shift + X`) and install **both** of the following packs:
  1. **Extension Pack for Java** *(by Microsoft — `vscjava.vscode-java-pack`)*
     - Installs *Language Support for Java™ by Red Hat*, *Debugger for Java*, *Maven for Java*, *Test Runner for Java*, and *Project Manager for Java*.
  2. **Spring Boot Extension Pack** *(by VMware — `vmware.vscode-boot-dev-pack`)*
     - Installs *Spring Boot Tools*, *Spring Initializr Java Support*, and *Spring Boot Dashboard*.
  *(Optional but helpful: **PostgreSQL** extension by Microsoft or Chris Kolkman if you want to inspect tables inside your editor.)*

- **IntelliJ IDEA (Community or Ultimate Edition):**
  - Ensure the built-in **Java** and **Maven** plugins are enabled (`File` -> `Settings` -> `Plugins`) — Ultimate users should also keep the **Spring Boot** plugin enabled.
  - Set the **Project SDK** (`File` -> `Project Structure` -> `Project`) to **JDK 17 or newer**.

- **Eclipse IDE / Spring Tool Suite (STS):**
  - Use **Eclipse IDE for Enterprise Java and Web Developers** or install **Spring Tools 4 (aka Spring Tool Suite 4)** from the *Eclipse Marketplace*, with **Maven Integration for Eclipse (m2e)** and **JDK 17+** selected under `Window` -> `Preferences` -> `Java` -> `Installed JREs`.

---

## 4. Installation Steps on Your Local Device

### Step 1: Clone the Repository & Navigate Using Windows PowerShell (`Set-Location`)

1. **Open Windows PowerShell:**
   Press the **`Windows`** key, type **PowerShell**, and click **Windows PowerShell** (or open your IDE's built-in terminal with `` Ctrl + ` `` and make sure **PowerShell** is selected).

2. **Move to the folder where you want to store the project using `Set-Location`:**
   In PowerShell, `Set-Location` changes your active working directory. For example, to place the project inside your `Downloads` or `Documents` folder:
   ```powershell
   Set-Location -Path "$HOME\Downloads"
   ```

3. **Clone the GitHub repository:**
   ```powershell
   git clone <repository-url>
   ```
   *(If you downloaded the project as a `.zip` file instead of using `git clone`, extract the ZIP first, then note the exact folder path where `pom.xml` and `mvnw.cmd` are located.)*

4. **Enter the `BrewPOS` project root folder using `Set-Location`:**
   ```powershell
   Set-Location -Path "$HOME\Downloads\BrewPOS"
   ```
   > **Tip — Custom Paths & Folders with Spaces:** If you cloned or extracted `BrewPOS` somewhere else (such as `Desktop` or `OneDrive\Documents`), pass your exact folder path inside **double quotes** `"..."`:
   > ```powershell
   > Set-Location -Path "C:\Users\<YourUsername>\Documents\BrewPOS"
   > ```

5. **Verify you are inside the right folder:**
   Run these two commands to confirm PowerShell is pointing directly at the project root:
   ```powershell
   # Shows your current folder path
   Get-Location

   # Lists files in this folder — make sure pom.xml, mvnw.cmd, and .env.example are visible!
   Get-ChildItem
   ```
   *(macOS / Linux equivalent: `git clone <repository-url> && cd BrewPOS && pwd && ls`)*

---

### Step 2: Set Up the Local PostgreSQL Database (`grp_brewposdb`)

Choose **either** **Option A (pgAdmin 4 GUI)** **or** **Option B (Windows PowerShell + `psql`)** below.

#### Option A: Using pgAdmin 4 (Recommended if you prefer a GUI)
1. Open **pgAdmin 4** and connect to your local PostgreSQL server.
2. Right-click **Databases** -> **Create** -> **Database...**, type **`grp_brewposdb`**, and click **Save**.
3. Right-click **`grp_brewposdb`** -> **Query Tool**:
   - Click the **Open File** icon, select **`database/schema.sql`** inside your `BrewPOS` folder, and click **Execute (F5)** to create the 5 tables, constraints, and indexes.
   - Click **Open File** again, select **`database/seed.sql`**, and click **Execute (F5)** to load the starter staff, categories, menu items, and sample orders.
4. *(Optional Least-Privilege Role)* To also create the restricted `brewpos_app` runtime account instead of connecting as `postgres`, open and execute **`database/05_create_app_role.sql`**, then run:
   ```sql
   ALTER ROLE brewpos_app WITH PASSWORD 'your_chosen_password';
   ```

#### Option B: Using Windows PowerShell (`psql` Command Line)
1. Make sure your PowerShell window is inside the `BrewPOS` project folder:
   ```powershell
   Set-Location -Path "C:\Users\<YourUsername>\Downloads\BrewPOS"
   ```
2. If PowerShell says `'psql' is not recognized`, temporarily add PostgreSQL's `bin` folder to your current PowerShell session (adjust `18` to `16` or `17` if you installed a different PostgreSQL version):
   ```powershell
   $env:Path += ";C:\Program Files\PostgreSQL\18\bin"
   ```
3. Run the following commands in order (enter your local `postgres` password when prompted):
   ```powershell
   # 1. Create the database
   psql -U postgres -d postgres -c "CREATE DATABASE grp_brewposdb;"

   # 2. Create the 5 tables, constraints, and indexes
   psql -U postgres -d grp_brewposdb -f database/schema.sql

   # 3. Load starter staff, categories, menu items, and sample orders
   psql -U postgres -d grp_brewposdb -f database/seed.sql
   ```
4. *(Optional Least-Privilege Role)* To create and set a password for the dedicated `brewpos_app` account:
   ```powershell
   psql -U postgres -d grp_brewposdb -f database/05_create_app_role.sql
   psql -U postgres -d grp_brewposdb -c "\password brewpos_app"
   ```

---

## 5. Configuration (`.env` Setup)

BrewPOS reads your local database credentials from a `.env` file located in the `BrewPOS` root directory (`spring.config.import=optional:file:.env[.properties]`).

### Step 3: Create and Edit Your Local `.env` File

1. **In Windows PowerShell** (while inside the `BrewPOS` folder), copy `.env.example` to `.env` and open it in Notepad:
   ```powershell
   # Confirm you are in the BrewPOS root folder
   Set-Location -Path "C:\Users\<YourUsername>\Downloads\BrewPOS"

   # Copy the example file to .env
   Copy-Item -Path ".\.env.example" -Destination ".\.env"

   # Open .env in Notepad to enter your local PostgreSQL password
   notepad .\.env
   ```
   *(macOS / Linux equivalent: `cp .env.example .env && nano .env`)*

2. **Fill in your local PostgreSQL credentials** inside `.env`, save (`Ctrl + S`), and close Notepad:

   - **If connecting with your local `postgres` account (simplest for quick local testing):**
     ```dotenv
     DB_URL=jdbc:postgresql://localhost:5432/grp_brewposdb
     DB_USERNAME=postgres
     DB_PASSWORD=your_postgres_password
     SERVER_PORT=8081
     ```
   - **If connecting with `brewpos_app` (after running `05_create_app_role.sql`):**
     ```dotenv
     DB_URL=jdbc:postgresql://localhost:5432/grp_brewposdb
     DB_USERNAME=brewpos_app
     DB_PASSWORD=your_brewpos_app_password
     SERVER_PORT=8081
     ```

| Variable | What It Does | Default |
|---|---|---|
| `DB_URL` | JDBC connection URL to `grp_brewposdb` (change `5432` only if your local PostgreSQL uses another port). | `jdbc:postgresql://localhost:5432/grp_brewposdb` |
| `DB_USERNAME` | Your local PostgreSQL username (`postgres` or `brewpos_app`). | `postgres` |
| `DB_PASSWORD` | Password for `DB_USERNAME` on your local machine. | *(Required)* |
| `SERVER_PORT` | Local HTTP port where the web app runs (`8081`). | `8081` |

*(Security Note: `.env` is ignored by `.gitignore`, so your local database password is never committed to GitHub.)*

---

## 6. How to Run the System on Your Local Device

You can launch BrewPOS either **via Windows PowerShell** (Method A — works regardless of which editor you use) or **directly inside your IDE** after installing the extension packs from Section 3.2 (Method B).

### Method A: Step-by-Step Using Windows PowerShell (Recommended)

1. **Open Windows PowerShell** on your computer.

2. **Navigate to your `BrewPOS` project folder using `Set-Location`:**
   Replace the example path below with the actual location where you cloned or extracted `BrewPOS`:
   ```powershell
   Set-Location -Path "C:\Users\<YourUsername>\Downloads\BrewPOS"
   ```

3. **Verify your working directory and confirm `.env` and `mvnw.cmd` exist:**
   ```powershell
   Get-Location
   Get-ChildItem -Force
   ```
   *(Using `-Force` reveals hidden dot-files so you can verify `.env` is named `.env` and not `.env.txt`.)*

4. **Check that Java JDK 17+ is active in PowerShell:**
   ```powershell
   java -version
   ```
   - If Maven later reports `JAVA_HOME is not defined correctly`, run this one-liner in the same PowerShell window to point `JAVA_HOME` to your installed JDK automatically:
     ```powershell
     $env:JAVA_HOME = (Split-Path (Split-Path (Get-Command javac).Source))
     ```

5. **(Only if prompted) Allow script execution for the current PowerShell session:**
   If Windows blocks local scripts with an execution policy message, run:
   ```powershell
   Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
   ```
   *(This applies only to the current PowerShell window and resets automatically when you close it.)*

6. **Start the BrewPOS Spring Boot server:**
   ```powershell
   .\mvnw.cmd spring-boot:run
   ```
   - On the **very first run**, wait about **30–60 seconds** while the Maven Wrapper downloads dependencies.
   - The server is ready once you see a message like:
     ```text
     Started BrewPosApplication in X.XXX seconds
     ```

7. **Open BrewPOS in your browser:**
   Keep the PowerShell window open in the background and navigate to:
   - **http://localhost:8081**

8. **Stop the server when finished:**
   Click inside the PowerShell window and press **`Ctrl + C`** (then type `Y` and press `Enter` if asked `Terminate batch job (Y/N)?`).

---

### Method B: Running Directly Inside Your Local IDE / Compiler

1. Confirm you have installed all required **Java & Spring Boot Extension Packs** for your IDE (**Section 3.2**), created `grp_brewposdb` (**Step 2**), and saved your `.env` file in the `BrewPOS` root (**Step 3**).
2. Open your IDE and choose **File -> Open Folder...**, then select the **`BrewPOS`** root folder directly (the folder that contains `pom.xml`—do not open a parent folder above it).
3. Wait **15–30 seconds** for the Java / Maven extension pack to finish importing `pom.xml` and indexing the project (check the bottom status bar of your IDE).
4. In the file explorer, open:
   `src/main/java/com/brewpos/BrewPosApplication.java`
5. Click the **Run** button that appears above `public static void main(String[] args)` *(or open the **Spring Boot Dashboard** panel in the left sidebar and click the **Run** icon next to `brewpos`)*.
6. Wait for `Started BrewPosApplication` in the IDE console, then open **http://localhost:8081** in your browser.

---

### Method C: Running on macOS / Linux Terminal

```bash
cd /path/to/BrewPOS
chmod +x mvnw
./mvnw spring-boot:run
```
Then open **http://localhost:8081** in your browser.

---

### Quick Tour Once Running (`http://localhost:8081`)
- **Place an order:** On **Register**, click a few drinks or pastries, pick **Cash** or **Online**, and click **Charge**.
- **Manage the menu:** Use the **top-right staff dropdown** to switch from `Demo Cashier` to **`Demo Manager`**, then click **Menu** to add or edit categories and items.
- **Check receipts & reports:** Click **Orders** to inspect itemized receipts, or **Daily Sales** to view net sales and payment breakdowns.

---

## 7. Common Troubleshooting

| Problem | How to Fix It |
|---|---|
| **`.\mvnw.cmd` is not recognized** | PowerShell is not inside the `BrewPOS` project root. Run `Set-Location -Path "C:\path\to\BrewPOS"` and then `Get-ChildItem` to verify `mvnw.cmd` and `pom.xml` are in that exact folder (check for nested `BrewPOS\BrewPOS` folders if you extracted a ZIP). |
| **No "Run" button on `BrewPosApplication.java` or red Java import warnings in IDE** | Install the required **Extension Pack for Java** and **Spring Boot Extension Pack** in your IDE (see **Section 3.2**), reload the IDE window, and ensure you opened the `BrewPOS` root folder directly. |
| **PowerShell says `running scripts is disabled on this system`** | Run `Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass` in PowerShell, then retry `.\mvnw.cmd spring-boot:run`. |
| **`JAVA_HOME is not defined correctly`** | Install JDK 17+ and run in PowerShell before starting the app:<br>`$env:JAVA_HOME = (Split-Path (Split-Path (Get-Command javac).Source))` |
| **`'psql' is not recognized` in PowerShell** | Add PostgreSQL's `bin` folder to your PowerShell session (adjust version `18` if needed):<br>`$env:Path += ";C:\Program Files\PostgreSQL\18\bin"` |
| **`password authentication failed for user ...`** | Check `DB_USERNAME` and `DB_PASSWORD` in `.env`. Also run `Get-ChildItem -Force` in PowerShell to confirm Notepad saved the file as `.env` and not `.env.txt`. |
| **`Connection to localhost:5432 refused` or `database "grp_brewposdb" does not exist`** | Ensure the PostgreSQL Windows service is running, verify the port in `DB_URL` (`5432` vs `5433`), and confirm you created `grp_brewposdb` and ran `database/schema.sql` + `database/seed.sql` (**Step 2**). |
| **Port `8081` is already in use** | Change `SERVER_PORT=8082` in `.env`, restart the server, and open `http://localhost:8082`. |

---

## 8. Design Notes & Scope

- **Role Dropdown (No Login Screen):** Employee login/authentication is outside the project scope. Selecting `Demo Cashier` or `Demo Manager` in the top-right dropdown switches the UI view for classroom demonstration.
- **Accurate Historical Prices:** Order totals are never stored as a fixed column. Instead, each item's price at the moment of sale is saved in `order_line_item.sold_price`, and totals are calculated dynamically (`SUM(quantity * sold_price)`). Changing a menu price later will never change past receipts.
- **Academic Scope:** Features like taxes, discounts, inventory tracking, customer accounts, and order refunds are intentionally excluded to preserve the required 5-table database design.
