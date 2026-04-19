package com.forecast.services.query;

import java.math.BigDecimal;

public class ForecastPointDto {

    private int timeIndex;
    private BigDecimal forecastValue;
    private BigDecimal lowerBound;
    private BigDecimal upperBound;

    public ForecastPointDto() {
    }

    public ForecastPointDto(
        int timeIndex,
        BigDecimal forecastValue,
        BigDecimal lowerBound,
        BigDecimal upperBound
    ) {
        this.timeIndex = timeIndex;
        this.forecastValue = forecastValue;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
    }

    public int getTimeIndex() {
        return timeIndex;
    }

    public void setTimeIndex(int timeIndex) {
        this.timeIndex = timeIndex;
    }

    public BigDecimal getForecastValue() {
        return forecastValue;
    }

    public void setForecastValue(BigDecimal forecastValue) {
        this.forecastValue = forecastValue;
    }

    public BigDecimal getLowerBound() {
        return lowerBound;
    }

    public void setLowerBound(BigDecimal lowerBound) {
        this.lowerBound = lowerBound;
    }

    public BigDecimal getUpperBound() {
        return upperBound;
    }

    public void setUpperBound(BigDecimal upperBound) {
        this.upperBound = upperBound;
    }
}