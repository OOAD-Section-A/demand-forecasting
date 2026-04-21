# Demand Forecasting Subsystem — Integration Guide

> **Version:** 1.0  
> **Target audience:** Frontend team, Inventory team, Reporting team, Database team  
> **Subsystem owner:** Demand Forecasting Team  
> **JAR artifact:** `demand-forecasting-1.0-SNAPSHOT.jar`

---

## Table of Contents

1. [Overview](#1-overview)
2. [Architecture](#2-architecture)
3. [Database Setup](#3-database-setup)
4. [JAR Integration — Frontend](#4-jar-integration--frontend)
5. [JAR Integration — Other Backend Services](#5-jar-integration--other-backend-services)
6. [Public API Reference](#6-public-api-reference)
7. [Exception Events](#7-exception-events)
8. [Running the Forecasting Engine](#8-running-the-forecasting-engine)
9. [Data Contracts](#9-data-contracts)
10. [Known Limitations & TODOs](#10-known-limitations--todos)
11. [Troubleshooting](#11-troubleshooting)
12. [Contact](#12-contact)

---

## 1. Overview

The **Demand Forecasting Subsystem** is a self-contained Maven module within the SCM platform. It is responsible for:

- Ingesting historical sales data (CSV or database)
- Cleaning, validating, and feature-engineering time series data
- Running lifecycle-aware ML forecasting strategies (ARIMA/Holt-Winters and Prophet/LSTM)
- Persisting forecast summaries, accuracy metrics, and time-series data to the shared MySQL database
- Exposing a **read-only query layer** to frontend and downstream systems via a distributable JAR

**Integration surface:**

| Integrating Team | What they consume |
|---|---|
| Frontend (Swing UI) | `ForecastQueryService` from the JAR to fetch graph-ready forecast series |
| Inventory/Procurement | `demand_forecasts.reorder_signal` and `suggested_order_qty` columns |
| Reporting & Analytics | `vw_reporting_dashboard` view (reads `demand_forecasts`) |
| Database team | `forecast_timeseries` and `forecast_performance_metrics` table definitions |
| SCM Exception Handler | Subscriptions to `DemandForecastingSubsystem` events |

---

## 2. Architecture

```
External Callers
      │
      ▼
ForecastController          IngestionController
      │                            │
      ▼                            ▼
ForecastProcessor     DataSourceConnector (CSV / DB)
      │                            │
      ├── LifeCycleManager         ▼
      │                    SalesDataValidationService
      ├── ForecastStrategy         │
      │   (ARIMA/Prophet)          ▼
      │                    FeatureEngineeringService
      ▼                            │
ForecastOutputService ◄────────────┘
      │
      ▼
ForecastPersistenceService
      │
      ▼
DemandForecastingDbAdapter  ──►  shared database-module JAR
      │
      ▼
MySQL Database
      │
      ▼
ForecastQueryService  ──►  Frontend / Consumer
```

---

## 3. Database Setup

### 3.1 Required Tables

All core tables are defined in `src/main/resources/schema.sql`. The tables that affect other teams are:

```sql
-- Core forecast output (written by this subsystem, read by inventory and reporting)
CREATE TABLE IF NOT EXISTS demand_forecasts (
    forecast_id              VARCHAR(50)    NOT NULL,
    product_id               VARCHAR(50)    NOT NULL,
    forecast_period          VARCHAR(30)    NOT NULL,
    forecast_date            DATE           NULL,
    predicted_demand         INT            NOT NULL,
    confidence_score         DECIMAL(5,2)   NULL,
    reorder_signal           BOOLEAN        NOT NULL DEFAULT FALSE,
    suggested_order_qty      INT            NULL,
    lifecycle_stage          VARCHAR(50)    NULL,
    algorithm_used           VARCHAR(100)   NULL,
    generated_at             DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    source_event_reference   VARCHAR(100),
    PRIMARY KEY (forecast_id)
);

-- Graph-ready time-series (written by this subsystem, read by frontend)
CREATE TABLE IF NOT EXISTS forecast_timeseries (
    id              VARCHAR(50)    PRIMARY KEY,
    forecast_id     VARCHAR(50)    NOT NULL,
    time_index      INT            NOT NULL,
    forecast_value  DECIMAL(10,2)  NOT NULL,
    lower_bound     DECIMAL(10,2),
    upper_bound     DECIMAL(10,2),
    CONSTRAINT fk_forecast_timeseries_forecast
        FOREIGN KEY (forecast_id)
        REFERENCES demand_forecasts(forecast_id)
        ON DELETE CASCADE
);

-- Performance metrics (written by this subsystem, consumed by monitoring/reporting)
CREATE TABLE IF NOT EXISTS forecast_performance_metrics (
    eval_id      VARCHAR(50) NOT NULL,
    forecast_id  VARCHAR(50) NOT NULL,
    forecast_date DATE       NOT NULL,
    predicted_qty INT        NOT NULL,
    actual_qty   INT         NULL,
    mape         DECIMAL(8,2) NULL,
    rmse         DECIMAL(12,4) NULL,
    model_used   VARCHAR(100) NULL,
    PRIMARY KEY (eval_id),
    FOREIGN KEY (forecast_id) REFERENCES demand_forecasts(forecast_id) ON DELETE CASCADE
);
```

### 3.2 Required Input Tables

This subsystem **reads** from:

| Table | Owner | What we read |
|---|---|---|
| `sales_records` | This subsystem / DB team | Historical sales for feature engineering |
| `product_lifecycle_stages` | DB team | Current lifecycle stage per product |
| `promotional_calendar` | Pricing subsystem | Promo events as demand features |
| `holiday_calendar` | DB team | Holidays as demand features |
| `inventory_supply` | Inventory subsystem | Stock and lead time data |

---

## 4. JAR Integration — Frontend

### 4.1 Add the JAR

**Option A — lib folder**
```
project/
  lib/
    demand-forecasting-1.0-SNAPSHOT.jar
```
Add to classpath in your IDE:
- IntelliJ: File → Project Structure → Libraries → Add JAR
- VS Code: Add to `.classpath` or `java.project.referencedLibraries`

**Option B — Maven (local install)**
```bash
mvn install:install-file \
  -Dfile=demand-forecasting-1.0-SNAPSHOT.jar \
  -DgroupId=com.forecast \
  -DartifactId=demand-forecasting \
  -Dversion=1.0-SNAPSHOT \
  -Dpackaging=jar
```
Then add to your `pom.xml`:
```xml
<dependency>
    <groupId>com.forecast</groupId>
    <artifactId>demand-forecasting</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

### 4.2 Required Runtime Dependency

Your project must include the MySQL JDBC driver:
```xml
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <version>8.3.0</version>
</dependency>
```

### 4.3 Fetch a Forecast Series

```java
import com.forecast.services.query.ForecastQueryService;
import com.forecast.services.query.ForecastSeriesResponseDto;
import com.forecast.services.query.ForecastPointDto;

// Instantiate once per application session (not per request)
ForecastQueryService service = new ForecastQueryService(
    "jdbc:mysql://localhost:3306/OOAD",
    "your_username",
    "your_password"
);

// Fetch the latest forecast for a product
ForecastSeriesResponseDto response = service.getLatestForecastSeries("P1001");

System.out.println("Product: " + response.getProductId());
System.out.println("Forecast ID: " + response.getForecastId());

for (ForecastPointDto point : response.getSeries()) {
    System.out.printf(
        "Month %d -> Forecast=%.2f  Lower=%.2f  Upper=%.2f%n",
        point.getTimeIndex(),
        point.getForecastValue(),
        point.getLowerBound(),
        point.getUpperBound()
    );
}
```

### 4.4 Display in a JFreeChart Graph

```java
import org.jfree.chart.*;
import org.jfree.data.xy.*;

XYSeriesCollection dataset = new XYSeriesCollection();
XYSeries forecastSeries = new XYSeries("Forecast");
XYSeries lowerSeries    = new XYSeries("Lower Bound");
XYSeries upperSeries    = new XYSeries("Upper Bound");

for (ForecastPointDto point : response.getSeries()) {
    forecastSeries.add(point.getTimeIndex(), point.getForecastValue());
    lowerSeries.add(point.getTimeIndex(), point.getLowerBound());
    upperSeries.add(point.getTimeIndex(), point.getUpperBound());
}

dataset.addSeries(forecastSeries);
dataset.addSeries(lowerSeries);
dataset.addSeries(upperSeries);

JFreeChart chart = ChartFactory.createXYLineChart(
    "Demand Forecast — " + response.getProductId(),
    "Month", "Demand", dataset
);
```

### 4.5 Display in a JTable

```java
String[] columns = { "Month", "Forecast", "Lower Bound", "Upper Bound" };
List<ForecastPointDto> series = response.getSeries();
Object[][] data = new Object[series.size()][4];

for (int i = 0; i < series.size(); i++) {
    ForecastPointDto p = series.get(i);
    data[i][0] = p.getTimeIndex();
    data[i][1] = p.getForecastValue();
    data[i][2] = p.getLowerBound();
    data[i][3] = p.getUpperBound();
}

JTable table = new JTable(data, columns);
```

### 4.6 Handling Empty Results

If no forecast has been generated yet for a product, `getLatestForecastSeries()` returns a `ForecastSeriesResponseDto` with:
- `forecastId` = `null`
- `series` = empty `ArrayList`

Always check before iterating:
```java
if (response.getSeries().isEmpty()) {
    // Show "No forecast available" message in the UI
}
```

---

## 5. JAR Integration — Other Backend Services

### 5.1 Inventory / Procurement Team

After a forecast run completes, the following columns in `demand_forecasts` are populated and available for replenishment logic:

| Column | Meaning |
|---|---|
| `predicted_demand` | Total predicted units for the forecast period |
| `reorder_signal` | Currently always `FALSE` (replenishment logic is a stub — see §10) |
| `suggested_order_qty` | Currently `NULL` (pending integration) |
| `lifecycle_stage` | Stage under which forecast was generated |
| `algorithm_used` | e.g. `ARIMA_HOLT_WINTERS:HOLT_WINTERS` |

### 5.2 Reporting & Analytics Team

The `vw_reporting_dashboard` view joins `demand_forecasts` and is safe to query. Use:
- `df.predicted_demand` for sales volume column
- `df.forecast_period` for period grouping
- `df.suggested_order_qty` for predicted inventory needs

---

## 6. Public API Reference

### 6.1 Classes exposed in the JAR

| Class | Package | Description |
|---|---|---|
| `ForecastQueryService` | `com.forecast.services.query` | Main entry point for queries |
| `ForecastSeriesResponseDto` | `com.forecast.services.query` | Query response object |
| `ForecastPointDto` | `com.forecast.services.query` | Single time-series point |

All other classes are internal and should not be referenced directly.

### 6.2 ForecastQueryService

```java
public class ForecastQueryService {

    /**
     * @param dbUrl      JDBC connection string, e.g. "jdbc:mysql://host:3306/OOAD"
     * @param dbUsername Database username
     * @param dbPassword Database password
     */
    public ForecastQueryService(String dbUrl, String dbUsername, String dbPassword);

    /**
     * Returns the most recently generated forecast series for a product.
     *
     * @param productId  The product identifier, e.g. "P1001"
     * @return ForecastSeriesResponseDto; never null; series may be empty if no forecast exists
     * @throws Exception on JDBC connection or query failure
     */
    public ForecastSeriesResponseDto getLatestForecastSeries(String productId) throws Exception;
}
```

### 6.3 ForecastSeriesResponseDto

| Field | Type | Description |
|---|---|---|
| `productId` | `String` | The product for which this forecast was generated |
| `forecastId` | `String` | Unique forecast run identifier (null if no forecast exists) |
| `series` | `List<ForecastPointDto>` | Time-series data ordered by timeIndex ascending |

### 6.4 ForecastPointDto

| Field | Type | Description |
|---|---|---|
| `timeIndex` | `int` | 1-based month offset from forecast start |
| `forecastValue` | `BigDecimal` | Central forecast demand value |
| `lowerBound` | `BigDecimal` | Lower confidence interval bound |
| `upperBound` | `BigDecimal` | Upper confidence interval bound |

---

## 7. Exception Events

The subsystem routes exception events to the shared SCM exception handler. Other teams may subscribe to the following events through `DemandForecastingSubsystem.INSTANCE`:

| Event Method | ErrorCode | Severity | When fired |
|---|---|---|---|
| `onModelAccuracyBelowThreshold()` | MODEL_ACCURACY_BELOW_THRESHOLD | MINOR | MAPE > 25% or RMSE > 35% of avg demand |
| `onReplenishmentSignalNotGenerated()` | REPLENISHMENT_SIGNAL_NOT_GENERATED | WARNING | No forecasted demand values to compute signal |
| `onMissingPromotionalData()` | MISSING_PROMOTIONAL_DATA | MINOR | No promo calendar entries for forecast period |
| `onHolidayDataStale()` | HOLIDAY_DATA_STALE | MINOR | Holiday calendar not recently updated |
| `onOutlierDetected()` | OUTLIER_DETECTED | WARNING | quantitySold > 3 standard deviations from mean |

All exception events are also written to `SCM_EXCEPTION_LOG`.

---

## 8. Running the Forecasting Engine

To trigger a forecast programmatically (e.g. from a batch job or scheduler):

```java
import com.forecast.controllers.ForecastController;
import com.forecast.integration.db.*;
import com.forecast.models.*;
import com.forecast.models.exceptions.MLAlgorithmicExceptionSource;
import com.forecast.services.engine.ForecastProcessor;
import com.forecast.services.engine.lifecycle.LifeCycleManager;
import com.forecast.services.output.ForecastOutputService;
import com.jackfruit.scm.database.facade.SupplyChainDatabaseFacade;

// 1. Build the dependency graph
SupplyChainDatabaseFacade facade = new SupplyChainDatabaseFacade();
DemandForecastingDbAdapter dbAdapter = new DemandForecastingDbAdapter(facade);
ForecastPersistenceService persistence = new ForecastPersistenceService(dbAdapter);
MLAlgorithmicExceptionSource exSource = new MLAlgorithmicExceptionSource();
ForecastOutputService output = new ForecastOutputService(exSource, persistence);
ForecastProcessor processor = new ForecastProcessor(new LifeCycleManager(), output, exSource);
ForecastController controller = new ForecastController(processor);

// 2. Load feature data (from DB or CSV — see data ingestion section)
FeatureTimeSeries features = /* ... */;

// 3. Generate forecast (pass null lifecycle to use automatic detection)
ForecastResult result = controller.generateForecast("P1001", "S001", features, null);

System.out.println("Status: " + result.getStatus());
System.out.println("Model:  " + result.getModelUsed());
```

### 8.1 Data Ingestion via CSV

```java
import com.forecast.services.ingestion.connector.CsvDataSourceConnector;
import com.forecast.controllers.IngestionController;

CsvDataSourceConnector connector = new CsvDataSourceConnector.Builder()
    .filePath("/data/sales/2024_sales.csv")
    .delimiter(',')
    .hasHeaderRow(true)
    .sourceName("csv-2024-sales")
    .build();

IngestionController ingestion = new IngestionController(connector);
List<RawSalesData> raw = ingestion.ingestData();
```

### 8.2 Data Ingestion via Database

```java
import com.forecast.services.ingestion.connector.DbDataSourceConnector;

DbDataSourceConnector connector = new DbDataSourceConnector.Builder()
    .jdbcUrl("jdbc:mysql://localhost:3306/OOAD")
    .credentials("username", "password")
    .schema("OOAD")
    .salesTable("sales_records")
    .sourceName("db-prod")
    .build();

IngestionController ingestion = new IngestionController(connector);
List<RawSalesData> raw = ingestion.ingestData();
```

---

## 9. Data Contracts

### 9.1 sales_records Expected Format (CSV)

| Column | Type | Required | Notes |
|---|---|---|---|
| sale_id | long | Yes | Unique identifier |
| product_id | String | Yes | Product reference |
| store_id | String | Yes | Store reference |
| sale_date | LocalDate (yyyy-MM-dd) | Yes | Must not be in future |
| quantity_sold | int | Yes | Must be >= 0 |
| unit_price | BigDecimal | Yes | Must be >= 0 |
| revenue | BigDecimal | Yes | |
| region | String | No | Optional geographic code |

### 9.2 Forecast Output Guarantees

- `forecastedDemand` list length equals the forecast horizon (1–12 months)
- `confidenceIntervalLower` and `confidenceIntervalUpper` are always the same length as `forecastedDemand`
- All demand and bound values are non-negative BigDecimal values with 2 decimal places
- `timeIndex` in `forecast_timeseries` is 1-based and contiguous

---


## 10. Troubleshooting

| Symptom | Likely Cause | Resolution |
|---|---|---|
| `forecast_timeseries` table not found | Table not created | Run `schema.sql` or add the table DDL manually |
| `ForecastingException: DATA_SOURCE_UNAVAILABLE` | JDBC connection failed after 3 retries | Check host, port, credentials, and network access |
| `ForecastingException: INSUFFICIENT_HISTORY` | Fewer than 12–24 data points | Ensure `sales_records` has enough history for the product |
| `ForecastingException: MODEL_ACCURACY_BELOW_THRESHOLD` | MAPE > 25% or RMSE too high | Review data quality; check for outliers or sudden demand spikes |
| Empty series returned by `ForecastQueryService` | No forecast run yet, or product ID mismatch | Verify productId matches exactly what is stored in `demand_forecasts` |
| MySQL driver not found at runtime | JDBC driver missing | Add `mysql-connector-j` to your project dependencies |

---

## 11. Contact

If you encounter integration issues, include in your message:

1. The exact exception message and stack trace
2. The product ID and store ID being used
3. Whether the `forecast_timeseries` table exists in your database
4. Your database connection string (credentials redacted)
5. Number of rows in `sales_records` for the affected product