package com.forecast.models;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * ForecastResult represents the output of a demand forecasting operation.
 * It contains the predicted demand values, confidence intervals, accuracy metrics,
 * and metadata about the forecast.
 *
 * Responsibilities:
 * - Store forecast predictions for a product
 * - Maintain confidence intervals and accuracy metrics
 * - Track the lifecycle stage and forecast period
 * - Provide access to forecast metadata
 *
 * @author Demand Forecasting Team
 * @version 1.0
 */
public class ForecastResult {

    private String productId;
    private String storeId;
    private LocalDate forecastGeneratedDate;
    private LocalDate forecastStartDate;
    private LocalDate forecastEndDate;
    private List<BigDecimal> forecastedDemand;
    private List<BigDecimal> confidenceIntervalLower;
    private List<BigDecimal> confidenceIntervalUpper;
    private BigDecimal mape; // Mean Absolute Percentage Error
    private BigDecimal rmse; // Root Mean Square Error
    private String lifecycleStage;
    private String modelUsed;
    private String status; // SUCCESS, DEGRADED, FAILED

    /**
     * Default constructor for ForecastResult.
     */
    public ForecastResult() {}

    /**
     * Constructor with essential forecast parameters.
     *
     * @param productId the product identifier
     * @param storeId the store identifier
     * @param forecastedDemand list of forecasted demand values
     */
    public ForecastResult(
        String productId,
        String storeId,
        List<BigDecimal> forecastedDemand
    ) {
        this.productId = productId;
        this.storeId = storeId;
        this.forecastedDemand = forecastedDemand;
        this.forecastGeneratedDate = LocalDate.now();
    }

    // Getters and Setters

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getStoreId() {
        return storeId;
    }

    public void setStoreId(String storeId) {
        this.storeId = storeId;
    }

    public LocalDate getForecastGeneratedDate() {
        return forecastGeneratedDate;
    }

    public void setForecastGeneratedDate(LocalDate forecastGeneratedDate) {
        this.forecastGeneratedDate = forecastGeneratedDate;
    }

    public LocalDate getForecastStartDate() {
        return forecastStartDate;
    }

    public void setForecastStartDate(LocalDate forecastStartDate) {
        this.forecastStartDate = forecastStartDate;
    }

    public LocalDate getForecastEndDate() {
        return forecastEndDate;
    }

    public void setForecastEndDate(LocalDate forecastEndDate) {
        this.forecastEndDate = forecastEndDate;
    }

    public List<BigDecimal> getForecastedDemand() {
        return forecastedDemand;
    }

    public void setForecastedDemand(List<BigDecimal> forecastedDemand) {
        this.forecastedDemand = forecastedDemand;
    }

    public List<BigDecimal> getConfidenceIntervalLower() {
        return confidenceIntervalLower;
    }

    public void setConfidenceIntervalLower(
        List<BigDecimal> confidenceIntervalLower
    ) {
        this.confidenceIntervalLower = confidenceIntervalLower;
    }

    public List<BigDecimal> getConfidenceIntervalUpper() {
        return confidenceIntervalUpper;
    }

    public void setConfidenceIntervalUpper(
        List<BigDecimal> confidenceIntervalUpper
    ) {
        this.confidenceIntervalUpper = confidenceIntervalUpper;
    }

    public BigDecimal getMape() {
        return mape;
    }

    public void setMape(BigDecimal mape) {
        this.mape = mape;
    }

    public BigDecimal getRmse() {
        return rmse;
    }

    public void setRmse(BigDecimal rmse) {
        this.rmse = rmse;
    }

    public String getLifecycleStage() {
        return lifecycleStage;
    }

    public void setLifecycleStage(String lifecycleStage) {
        this.lifecycleStage = lifecycleStage;
    }

    public String getModelUsed() {
        return modelUsed;
    }

    public void setModelUsed(String modelUsed) {
        this.modelUsed = modelUsed;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
