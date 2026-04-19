# Demand Forecasting Subsystem

> **Module:** `demand-forecasting`  
> **Version:** 1.0-SNAPSHOT  
> **Java:** 11  
> **Build:** Maven  
> **Database:** MySQL 8.x  

---

## Overview

The **Demand Forecasting Subsystem** is a Java module within the Supply Chain Management (SCM) platform. It generates demand forecasts from historical sales data and makes them available to inventory, procurement, reporting, and frontend visualization teams.

The subsystem handles the full forecasting lifecycle end-to-end:

- **Data Ingestion** — reads raw sales records from CSV files or a relational database
- **Data Cleaning** — validates, deduplicates, and removes statistical outliers
- **Feature Engineering** — computes lag features, trend and seasonal decomposition
- **Lifecycle-Aware Strategy Selection** — picks the right algorithm based on the product's market stage
- **Forecasting** — runs ARIMA/Holt-Winters or Prophet/LSTM strategies
- **Accuracy Validation** — evaluates MAPE and RMSE before accepting results
- **Persistence** — writes forecast summaries, per-month time-series, and performance metrics to MySQL
- **Query Layer** — exposes a read-only JAR API for frontend and downstream consumers

---

## Features

### Dual Forecasting Strategies

The system supports two pluggable forecasting strategies, automatically selected based on product lifecycle and demand pattern:

| Strategy | Best For | Min Data Points | Max Horizon |
|---|---|---|---|
| **ARIMA / Holt-Winters** | Stable, trending, or seasonal demand | 24 months | 12 months |
| **Prophet / LSTM** | Volatile, promotional, or nonlinear demand | 12 months | 6 months |

Within each strategy, the algorithm is further selected at runtime:

- ARIMA/Holt-Winters: chooses **HOLT_WINTERS** when strong seasonality is detected (≥24 data points), otherwise **ARIMA**
- Prophet/LSTM: chooses **LSTM** for high-volatility demand (CV > 25%), otherwise **PROPHET**

### Product Lifecycle Awareness

Forecasting parameters adapt to five lifecycle stages:

- **INTRODUCTION** — short horizon (3 months), uses Prophet/LSTM for new product curves
- **GROWTH** — medium horizon (6 months), uses Prophet/LSTM for accelerating trends
- **MATURITY** — medium horizon (6 months), strategy selected by pattern profile
- **DECLINE** — short horizon (3 months), uses ARIMA/Holt-Winters for decaying trends
- **DISCONTINUED** — minimal horizon (1 month), ARIMA/Holt-Winters only

### Data Ingestion

Supports two source types with a common connector abstraction:

- **CSV Connector** — reads RFC-4180 CSV files with configurable delimiter and header
- **Database Connector** — reads from a configured JDBC source (MySQL, PostgreSQL)

Both connectors use **exponential backoff retry** (3 attempts, 500 ms initial delay, up to 8 s max) to handle transient failures.

### Data Quality Pipeline

Every ingested dataset passes through:

1. **Structural validation** — required fields, non-negative values, no future dates
2. **Deduplication** — composite key (product + store + date), first occurrence wins
3. **Outlier detection** — 3-sigma rule on quantity sold; outlier records excluded

Quality events are routed to the shared SCM exception handler for cross-subsystem visibility.

### Feature Engineering

Transforms cleaned records into model-ready features:

- Lagged demand (4-period rolling average)
- Linear trend component (centred moving average)
- Seasonal index (demand / trend ratio)
- Promotional lift injection (when promo calendar data is provided)
- Pattern classification (STABLE / SEASONAL / VOLATILE) via coefficient of variation

### Confidence Intervals & Accuracy Metrics

Every forecast includes:

- Per-month **confidence interval lower and upper bounds**
- **MAPE** (Mean Absolute Percentage Error) — threshold: 25%
- **RMSE** (Root Mean Square Error) — threshold: 35% of average forecasted demand

If thresholds are exceeded, the system automatically falls back to a **degraded forecast** (rolling average of last 4 periods), marked `status=DEGRADED`.

### Graceful Degradation

When the primary forecast pipeline fails:

- A degraded forecast is generated using the last 4 observed demand values
- Standard deviation is computed for confidence band estimation
- Result is published as `status=DEGRADED` so consumers can distinguish it
- Lifecycle stage is preserved on the degraded result

### Persistence

Three layers of data are written to MySQL on every successful forecast:

1. **Forecast summary** → `demand_forecasts`
2. **Performance metrics** → `forecast_performance_metrics`
3. **Monthly time-series** → `forecast_timeseries`

### Query Layer (JAR API)

A lightweight query service is distributed via the compiled JAR for downstream consumers. It abstracts two SQL queries behind a single method call and returns ordered, graph-ready data.

### Exception Integration

Integrates with the shared SCM exception handler (`DemandForecastingSubsystem.INSTANCE`) to fire structured events for:

- Model accuracy violations
- Missing promotional or holiday data
- Detected outliers
- Replenishment signal failures
- Algorithmic demand spikes

All events are persisted to `SCM_EXCEPTION_LOG`.

---

## Quick Start

### Prerequisites

- Java 11+
- Maven 3.6+
- MySQL 8.x running at `localhost:3306`
- Database `OOAD` created and schema applied
- Local JARs present in `lib/`: `database-module-1.0.0-SNAPSHOT-standalone.jar`, `scm-exception-handler-v3.jar`

### Build

```bash
mvn clean package
```

Output: `target/demand-forecasting-1.0-SNAPSHOT.jar`

### Run the Integration Test

```bash
mvn exec:java -Dexec.mainClass="com.forecast.TestForecastRunner"
```

This runs a full forecast for product `P1001` at store `S001` against the local database and prints results to stdout.

> **Warning:** `TestForecastRunner` contains hardcoded development credentials. Do not use in production.

### Apply Database Schema

```bash
mysql -u root -p OOAD < src/main/resources/schema.sql
```

---

## Project Structure

```
src/main/java/com/forecast/
├── TestForecastRunner.java              # Integration test entry point
├── controllers/
│   ├── ForecastController.java          # Forecast trigger endpoint
│   └── IngestionController.java         # Data ingestion endpoint
├── models/
│   ├── RawSalesData.java                # Immutable raw sales record (Builder)
│   ├── FeatureTimeSeries.java           # Engineered feature container
│   ├── ForecastResult.java              # Forecast output object
│   ├── LifecycleContent.java            # Product lifecycle stage metadata
│   ├── PatternProfile.java              # Detected demand pattern data
│   ├── PromoData.java                   # Promotional campaign metadata
│   ├── exceptions/
│   │   ├── ErrorCode.java               # Canonical error code enum
│   │   ├── ForecastingException.java    # Base subsystem exception
│   │   ├── IMLAlgorithmicExceptionSource.java  # Exception routing contract
│   │   └── MLAlgorithmicExceptionSource.java   # Concrete exception router
│   └── interfaces/
│       ├── ICleanSalesData.java         # Data cleaning contract
│       ├── IDataConnector.java          # Connector contract (DIP)
│       └── IPromoData.java              # Promotional data contract
├── integration/db/
│   ├── DemandForecastMapper.java        # ForecastResult → DB model mapper
│   ├── DemandForecastingDbAdapter.java  # Wrapper over shared DB JAR
│   └── ForecastPersistenceService.java  # Orchestrates 3-table write
├── services/
│   ├── engine/
│   │   ├── ForecastProcessor.java       # Core pipeline orchestrator
│   │   ├── lifecycle/
│   │   │   └── LifeCycleManager.java    # Stage resolution & strategy recommendation
│   │   └── strategy/
│   │       ├── ForecastStrategy.java    # Strategy interface
│   │       ├── ArimaHoltWintersStrategy.java  # Statistical strategy
│   │       └── ProphetLSTMStrategy.java       # Adaptive ML strategy
│   ├── ingestion/
│   │   ├── connector/
│   │   │   ├── DataSourceConnector.java  # Abstract base with retry
│   │   │   ├── CsvDataSourceConnector.java
│   │   │   ├── DbDataSourceConnector.java
│   │   │   └── RetryPolicy.java
│   │   ├── feature/
│   │   │   └── FeatureEngineeringService.java
│   │   └── validation/
│   │       └── SalesDataValidationService.java
│   ├── output/
│   │   └── ForecastOutputService.java   # Publish & replenishment signal
│   └── query/
│       ├── ForecastQueryService.java    # Public JAR query API
│       ├── ForecastSeriesResponseDto.java
│       └── ForecastPointDto.java
```

---

## Configuration

### database.properties

```properties
db.url=jdbc:mysql://localhost:3306/OOAD
db.username=root
db.password=your_password
```

Located at `src/main/resources/database.properties` and `lib/database.properties`.

### pom.xml Key Settings

| Property | Value |
|---|---|
| Java source/target | 11 |
| Maven compiler | 3.11.0 |
| SLF4J version | 2.0.7 |
| JUnit Jupiter | 5.9.3 |

---

## Design Principles

The codebase follows SOLID and GRASP principles throughout:

| Principle | Application |
|---|---|
| **SRP** | Each class has a single, clear responsibility (controller routes, processor orchestrates, strategy forecasts, persistence saves) |
| **OCP** | New forecasting strategies implement `ForecastStrategy` without modifying `ForecastProcessor` |
| **DIP** | Controllers depend on `IDataConnector`; services depend on `IMLAlgorithmicExceptionSource` — never concrete types |
| **Strategy Pattern** | `ArimaHoltWintersStrategy` and `ProphetLSTMStrategy` are interchangeable at runtime |
| **Builder Pattern** | `RawSalesData`, `DataSourceConnector`, `RetryPolicy` all use immutable builders |
| **Template Method** | `DataSourceConnector` defines the retry skeleton; subclasses implement `openConnection()`, `doFetchSalesData()`, `closeConnection()` |
| **Mapper Pattern** | `DemandForecastMapper` converts domain objects to DB models in isolation |
| **Information Expert (GRASP)** | `DataSourceConnector` owns retry state; `ForecastResult` owns its own validation data |

---

## Dependencies

| Dependency | Purpose |
|---|---|
| `database-module-1.0.0-SNAPSHOT-standalone.jar` | Shared SCM database adapters and models |
| `scm-exception-handler-v3.jar` | Shared SCM exception routing infrastructure |
| `slf4j-api + slf4j-jdk14` | Logging facade (bridges to java.util.logging) |
| `mysql-connector-j` (runtime) | JDBC driver for MySQL connectivity |
| `junit-jupiter` (test) | Unit test framework |

---

## License

Internal use only — part of the SCM platform project.