# Database Module Integration Guide

## What This Module Does

This module is the shared database layer for all subsystems.

Subsystems should:

- use the database module JAR
- configure database connection details
- call the provided adapter classes

Subsystems should not:

- connect to MySQL directly
- write their own SQL for shared database operations
- run `schema.sql` manually in normal usage

## Main Rule

Use the adapter classes in:

```java
com.jackfruit.scm.database.adapter
```

Each subsystem should talk to the database through its adapter, not through direct JDBC code.

## JAR Files To Use

After building the database module, the main output JAR is:

```text
dist/database-module-1.0.0-SNAPSHOT-standalone.jar
```

This JAR contains:

- the database module classes
- required runtime dependencies
- the embedded `schema.sql`

This project also expects the exception handler JAR to be available:

```text
dist/scm-exception-handler-v3.jar
```

## How Subsystems Should Connect

### 1. Add The JAR To The Subsystem

If the subsystem is a Maven project, install or publish this module and add it as a dependency.

If the subsystem is not Maven-based, add these JARs to its classpath:

- `database-module-1.0.0-SNAPSHOT-standalone.jar`
- `scm-exception-handler-v3.jar`

### 2. Provide Database Configuration

The subsystem must provide valid MySQL connection settings.

Supported configuration sources:

1. JVM system properties
2. environment variables
3. `database.properties`

Supported keys:

| Setting | Environment Variable | Required |
|---|---|---|
| `db.url` | `DB_URL` | Yes |
| `db.username` | `DB_USERNAME` | Yes |
| `db.password` | `DB_PASSWORD` | Yes |
| `db.pool.size` | `DB_POOL_SIZE` | No |

Example values:

```properties
db.url=jdbc:mysql://localhost:3306/OOAD
db.username=root
db.password=your_password
db.pool.size=5
```

### 3. Instantiate The Facade And Adapter

Example:

```java
import com.jackfruit.scm.database.adapter.InventoryAdapter;
import com.jackfruit.scm.database.facade.SupplyChainDatabaseFacade;

public class InventorySubsystemApp {
    public static void main(String[] args) {
        try (SupplyChainDatabaseFacade facade = new SupplyChainDatabaseFacade()) {
            InventoryAdapter inventoryAdapter = new InventoryAdapter(facade);

            inventoryAdapter.listProducts()
                    .forEach(product -> System.out.println(product.productName()));
        }
    }
}
```

## Schema Setup Behavior

Subsystem teams do not need to manually run `schema.sql` in normal integration.

When `SupplyChainDatabaseFacade` starts, the module automatically:

1. reads the DB configuration
2. connects to MySQL
3. creates the target database if it does not already exist
4. checks whether the schema already exists
5. applies the embedded `schema.sql` only if required

Important:

- the module does not drop and recreate the database on every run
- the module bootstraps the schema automatically when needed
- MySQL must still be running and reachable
- the DB user must have enough permissions to create the database and tables when bootstrap is needed

## What Subsystem Teams Need To Do

Each subsystem team should follow this exact process:

1. Add the required JAR files to their subsystem.
2. Set valid database credentials.
3. Make sure MySQL is running.
4. Use the correct adapter for their subsystem.
5. Call adapter methods for create, read, update, and delete operations.

They do not need to:

- open MySQL and run `schema.sql` manually
- create separate DAOs
- build their own database connection layer

## Available Adapters

- `PricingAdapter`
- `OrderAdapter`
- `InventoryAdapter`
- `WarehouseManagementAdapter`
- `LogisticsAdapter`
- `ReportingAdapter`
- `BarcodeTrackingAdapter`
- `BarcodeReaderAdapter`
- `DeliveryOrdersAdapter`
- `DeliveryMonitoringAdapter`
- `OrderFulfillmentAdapter`
- `DemandForecastingAdapter`
- `CommissionAdapter`
- `PackagingAdapter`
- `ReturnsAdapter`
- `StockLedgerAdapter`
- `UiAdapter`
- `ExceptionHandlingAdapter`

## Demand Forecasting Adapter Deletes

The updated `DemandForecastingAdapter` exposes delete operations for:

- `deleteForecast(String forecastId)`
- `deleteSalesRecord(String saleId)`
- `deleteHolidayCalendar(String holidayId)`
- `deletePromotionalCalendar(String promoCalendarId)`
- `deleteProductMetadata(String productId)`
- `deleteProductLifecycleStage(String lifecycleId)`
- `deleteInventorySupply(String productId)`
- `deleteForecastPerformanceMetric(String evalId)`
- `deleteForecastTimeseries(String timeseriesId)`

## Build The JAR

From the `database_module` folder:

```bash
mvn clean package
```

Output:

```text
dist/database-module-1.0.0-SNAPSHOT-standalone.jar
```

## Quick Start For Other Teams

If another subsystem wants to use this module, they only need to:

1. get the JAR files
2. configure `db.url`, `db.username`, and `db.password`
3. instantiate `SupplyChainDatabaseFacade`
4. use the correct adapter

That is all.

## Notes

- If startup fails, first check DB URL, username, password, and whether MySQL is running.
- If schema bootstrap fails, check whether the DB user has create privileges.
- If an adapter does not expose an operation a subsystem needs, extend the database module instead of bypassing it.
