# Demand Forecasting Subsystem

## Overview

This repository contains the **Demand Forecasting Subsystem** for the SCM project.

The subsystem is responsible for:
- Generating demand forecasts from historical data
- Persisting forecast results into the database
- Providing a query layer for frontend visualization

---

## Architecture

```
Forecast Engine
      ↓
ForecastOutputService
      ↓
ForecastPersistenceService
      ↓
Database (MySQL)
      ↓
ForecastQueryService (JAR)
      ↓
Frontend (Swing)
```

---

## Features

- Time-series demand forecasting
- Lifecycle-aware model selection
- Forecast persistence (summary + detailed)
- Graph-ready data storage
- Query layer exposed via JAR for frontend
- Shared database module integration through `DemandForecastingAdapter`

---

## Database Integration

The project uses the shared SCM database module JAR in `lib/`:

- `database-module-1.0.0-SNAPSHOT-standalone.jar`
- `scm-exception-handler-v3.jar`

See `docs/database-module-integration.md` for the database team's integration rules.

The database module bootstraps schema setup through `SupplyChainDatabaseFacade`, so subsystem users should not manually run `schema.sql` during normal integration.

---

## Query Layer

Use:

```java
ForecastQueryService service =
    new ForecastQueryService(DB_URL, USER, PASS);

ForecastSeriesResponseDto response =
    service.getLatestForecastSeries("P1001");
```

---

## Build

```bash
mvn clean package
```

---

## Output

```
target/demand-forecasting-1.0-SNAPSHOT.jar
```

---

## Notes

- Requires valid database module configuration through JVM properties, environment variables, or `database.properties`
- Forecast retrieved by productId
- Delete operations are available through `DemandForecastingDbAdapter` and `ForecastPersistenceService`

---

