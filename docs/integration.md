# Demand Forecasting Subsystem Integration Guide

## Overview

This document explains how to integrate the **Demand Forecasting Query Subsystem** into the frontend application.

A JAR file will be shared separately. That JAR exposes a query layer that allows the frontend to fetch **graph-ready forecast data** from the database.

---

## What this subsystem provides

The forecasting subsystem currently supports:

- Persisting forecast summary data into `demand_forecasts`
- Persisting graph-ready forecast points into `forecast_timeseries`
- Querying the latest forecast series for a product through the query layer in the JAR

The frontend team only needs the **query layer** from the JAR.

---

## Temporary database change required

Until the database team officially adds support for time-series forecast data, please add the following table in your local database:

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

## Query layer exposed through the JAR

The frontend should use only these classes from the JAR:

- `com.forecast.services.query.ForecastQueryService`
- `com.forecast.services.query.ForecastSeriesResponseDto`
- `com.forecast.services.query.ForecastPointDto`

These classes are intended as the integration interface for the frontend.

---

## Add the JAR to your project

Add the provided JAR file to your Swing project.

Typical ways to do this:

### Option 1: Add to a `lib` folder
- Create a `lib/` folder in your project
- Put the JAR inside it
- Add it to the project classpath

### Option 2: Add through IDE
- IntelliJ: **File -> Project Structure -> Libraries -> Add JAR**
- VS Code: add the JAR to Java project dependencies

---

## Required dependency

Make sure your frontend project already includes the MySQL JDBC driver.

You need:

- `mysql-connector-j`

Without this, the query service will not be able to connect to the database.

---

## How to use the subsystem

### Imports

```java
import com.forecast.services.query.ForecastQueryService;
import com.forecast.services.query.ForecastSeriesResponseDto;
import com.forecast.services.query.ForecastPointDto;
```

### Create the query service

```java
ForecastQueryService service =
    new ForecastQueryService(
        "jdbc:mysql://localhost:3306/YOUR_DB_NAME",
        "your_username",
        "your_password"
    );
```

### Fetch the latest forecast series for a product

```java
ForecastSeriesResponseDto response =
    service.getLatestForecastSeries("P1001");
```

### Read the returned data

```java
for (ForecastPointDto point : response.getSeries()) {
    System.out.println(
        "Month " + point.getTimeIndex() +
        " -> " + point.getForecastValue()
    );
}
```

---

## Returned data structure

### `ForecastSeriesResponseDto`
Contains:

- `productId`
- `forecastId`
- `series` -> list of `ForecastPointDto`

### `ForecastPointDto`
Contains:

- `timeIndex`
- `forecastValue`
- `lowerBound`
- `upperBound`

This structure is already ordered by `timeIndex` inside the query service.

---

## How the frontend should display this data

The frontend can use the returned series either in a graph or in a table.

### For graphs

Recommended mapping:

- `timeIndex` -> X-axis
- `forecastValue` -> main forecast line
- `lowerBound` -> lower confidence band
- `upperBound` -> upper confidence band

Suggested Swing chart libraries:

- **JFreeChart**
- **XChart**

### For table display

Suggested columns:

- Month
- Forecast
- Lower Bound
- Upper Bound

Example:

```java
String[] columns = {"Month", "Forecast", "Lower", "Upper"};
Object[][] data = new Object[response.getSeries().size()][4];

for (int i = 0; i < response.getSeries().size(); i++) {
    ForecastPointDto p = response.getSeries().get(i);
    data[i][0] = p.getTimeIndex();
    data[i][1] = p.getForecastValue();
    data[i][2] = p.getLowerBound();
    data[i][3] = p.getUpperBound();
}

JTable table = new JTable(data, columns);
```

---

## Expected read pattern

The query service internally follows this logic:

1. Get the latest `forecast_id` for a given `product_id`
2. Read the corresponding rows from `forecast_timeseries`
3. Return them ordered by `time_index`

So the frontend does not need to manually write these SQL queries when using the JAR.

---

## Current assumptions

- Forecast retrieval is currently based on **product ID**
- The latest forecast for that product is returned
- Data is sorted in forecast order
- The `forecast_timeseries` table must exist for graph-ready data retrieval

---

## Notes

- The current JAR exposes only the **query layer** for frontend usage
- The rest of the forecasting subsystem remains internal
- The direct JDBC usage in the query layer is temporary until the database team adds equivalent support in their shared implementation

---

## Summary

To integrate this subsystem:

1. Add the `forecast_timeseries` table locally
2. Add the provided JAR to the frontend project
3. Ensure MySQL JDBC driver is present
4. Use `ForecastQueryService` to fetch forecast series
5. Display the returned data in a graph or table

---

## Contact

If any integration issue comes up, reach out with:
- the error message
- the DB connection config being used
- whether the `forecast_timeseries` table has been added
