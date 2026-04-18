package com.forecast.services.query;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ForecastQueryService {

    private final String dbUrl;
    private final String dbUsername;
    private final String dbPassword;

    public ForecastQueryService(String dbUrl, String dbUsername, String dbPassword) {
        this.dbUrl = Objects.requireNonNull(dbUrl, "dbUrl must not be null");
        this.dbUsername = Objects.requireNonNull(dbUsername, "dbUsername must not be null");
        this.dbPassword = Objects.requireNonNull(dbPassword, "dbPassword must not be null");
    }

    public ForecastSeriesResponseDto getLatestForecastSeries(String productId) throws Exception {
        Objects.requireNonNull(productId, "productId must not be null");

        String latestForecastQuery =
            "SELECT forecast_id " +
            "FROM demand_forecasts " +
            "WHERE product_id = ? " +
            "ORDER BY generated_at DESC " +
            "LIMIT 1";

        String seriesQuery =
            "SELECT time_index, forecast_value, lower_bound, upper_bound " +
            "FROM forecast_timeseries " +
            "WHERE forecast_id = ? " +
            "ORDER BY time_index";

        try (
            Connection conn = DriverManager.getConnection(dbUrl, dbUsername, dbPassword);
            PreparedStatement latestStmt = conn.prepareStatement(latestForecastQuery)
        ) {
            latestStmt.setString(1, productId);

            String forecastId;

            try (ResultSet latestRs = latestStmt.executeQuery()) {
                if (!latestRs.next()) {
                    return new ForecastSeriesResponseDto(
                        productId,
                        null,
                        new ArrayList<ForecastPointDto>()
                    );
                }
                forecastId = latestRs.getString("forecast_id");
            }

            List<ForecastPointDto> series = new ArrayList<>();

            try (PreparedStatement seriesStmt = conn.prepareStatement(seriesQuery)) {
                seriesStmt.setString(1, forecastId);

                try (ResultSet rs = seriesStmt.executeQuery()) {
                    while (rs.next()) {
                        series.add(
                            new ForecastPointDto(
                                rs.getInt("time_index"),
                                rs.getBigDecimal("forecast_value"),
                                rs.getBigDecimal("lower_bound"),
                                rs.getBigDecimal("upper_bound")
                            )
                        );
                    }
                }
            }

            return new ForecastSeriesResponseDto(productId, forecastId, series);
        }
    }
}