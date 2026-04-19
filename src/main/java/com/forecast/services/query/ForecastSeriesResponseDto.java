package com.forecast.services.query;

import java.util.List;

public class ForecastSeriesResponseDto {

    private String productId;
    private String forecastId;
    private List<ForecastPointDto> series;

    public ForecastSeriesResponseDto() {
    }

    public ForecastSeriesResponseDto(
        String productId,
        String forecastId,
        List<ForecastPointDto> series
    ) {
        this.productId = productId;
        this.forecastId = forecastId;
        this.series = series;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getForecastId() {
        return forecastId;
    }

    public void setForecastId(String forecastId) {
        this.forecastId = forecastId;
    }

    public List<ForecastPointDto> getSeries() {
        return series;
    }

    public void setSeries(List<ForecastPointDto> series) {
        this.series = series;
    }
}