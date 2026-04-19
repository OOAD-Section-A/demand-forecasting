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

---

## Database Design

### `forecast_timeseries` (NEW)

```sql
CREATE TABLE forecast_timeseries (
    id VARCHAR(50) PRIMARY KEY,
    forecast_id VARCHAR(50) NOT NULL,
    time_index INT NOT NULL,
    forecast_value DECIMAL(10,2) NOT NULL,
    lower_bound DECIMAL(10,2),
    upper_bound DECIMAL(10,2),
    CONSTRAINT fk_forecast_timeseries_forecast
        FOREIGN KEY (forecast_id)
        REFERENCES demand_forecasts(forecast_id)
        ON DELETE CASCADE
);
```

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

- Requires MySQL driver
- Uses JDBC (temporary)
- Forecast retrieved by productId

---

