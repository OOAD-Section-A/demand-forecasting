package com.forecast.models;

import java.time.LocalDate;

/**
 * LifecycleContent represents metadata and configuration for a specific
 * product lifecycle stage. It contains information about how products
 * behave at different stages of their market lifecycle.
 *
 * Responsibilities:
 * - Store lifecycle stage information for a product
 * - Maintain stage-specific metadata and characteristics
 * - Track stage transition dates and durations
 * - Provide access to lifecycle-specific forecasting parameters
 *
 * Lifecycle Stages:
 * - INTRODUCTION: Product is new to market
 * - GROWTH: Product demand is rapidly increasing
 * - MATURITY: Product demand is stable
 * - DECLINE: Product demand is decreasing
 * - DISCONTINUED: Product is no longer sold
 *
 * @author Demand Forecasting Team
 * @version 1.0
 */
public class LifecycleContent {

    private String productId;
    private String currentStage;
    private LocalDate stageStartDate;
    private LocalDate stageEndDate;
    private LocalDate lastUpdated;
    private String description;
    private Double expectedGrowthRate;
    private Double expectedDemandVariability;
    private Integer forecastHorizonMonths;
    private Boolean useSeasonalAdjustment;
    private String notes;

    /**
     * Default constructor for LifecycleContent.
     */
    public LifecycleContent() {}

    /**
     * Constructor with essential lifecycle parameters.
     *
     * @param productId the product identifier
     * @param currentStage the current lifecycle stage
     * @param stageStartDate the date the product entered this stage
     */
    public LifecycleContent(
        String productId,
        String currentStage,
        LocalDate stageStartDate
    ) {
        this.productId = productId;
        this.currentStage = currentStage;
        this.stageStartDate = stageStartDate;
        this.lastUpdated = LocalDate.now();
    }

    // Getters and Setters

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getCurrentStage() {
        return currentStage;
    }

    public void setCurrentStage(String currentStage) {
        this.currentStage = currentStage;
    }

    public LocalDate getStageStartDate() {
        return stageStartDate;
    }

    public void setStageStartDate(LocalDate stageStartDate) {
        this.stageStartDate = stageStartDate;
    }

    public LocalDate getStageEndDate() {
        return stageEndDate;
    }

    public void setStageEndDate(LocalDate stageEndDate) {
        this.stageEndDate = stageEndDate;
    }

    public LocalDate getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDate lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Double getExpectedGrowthRate() {
        return expectedGrowthRate;
    }

    public void setExpectedGrowthRate(Double expectedGrowthRate) {
        this.expectedGrowthRate = expectedGrowthRate;
    }

    public Double getExpectedDemandVariability() {
        return expectedDemandVariability;
    }

    public void setExpectedDemandVariability(Double expectedDemandVariability) {
        this.expectedDemandVariability = expectedDemandVariability;
    }

    public Integer getForecastHorizonMonths() {
        return forecastHorizonMonths;
    }

    public void setForecastHorizonMonths(Integer forecastHorizonMonths) {
        this.forecastHorizonMonths = forecastHorizonMonths;
    }

    public Boolean getUseSeasonalAdjustment() {
        return useSeasonalAdjustment;
    }

    public void setUseSeasonalAdjustment(Boolean useSeasonalAdjustment) {
        this.useSeasonalAdjustment = useSeasonalAdjustment;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    /**
     * Determines if the product is currently in an active lifecycle stage.
     *
     * @return true if the stage is not DISCONTINUED
     */
    public boolean isActive() {
        return currentStage != null && !currentStage.equals("DISCONTINUED");
    }
}
