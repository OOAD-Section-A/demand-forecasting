package com.forecast.models;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * FeatureTimeSeries represents engineered features extracted from historical
 * sales data organized as time series data. These features are used as inputs
 * to forecasting models.
 *
 * Responsibilities:
 * - Store time series feature vectors for a product
 * - Maintain temporal organization of features
 * - Provide access to individual and aggregate features
 * - Track feature metadata and validity
 *
 * Features may include:
 * - Lagged demand values
 * - Seasonal indices
 * - Trend components
 * - Promotional indicators
 * - Holiday flags
 * - Custom engineered features
 *
 * @author Demand Forecasting Team
 * @version 1.0
 */
public class FeatureTimeSeries {

    private String productId;
    private String storeId;
    private LocalDate startDate;
    private LocalDate endDate;
    private List<LocalDate> dates;
    private List<BigDecimal> demandValues;
    private List<BigDecimal> trendComponent;
    private List<BigDecimal> seasonalComponent;
    private List<BigDecimal> laggedDemand;
    private Map<String, List<BigDecimal>> customFeatures;
    private boolean isNormalized;
    private String featureEngineeringVersion;

    /**
     * Default constructor for FeatureTimeSeries.
     */
    public FeatureTimeSeries() {
        this.isNormalized = false;
    }

    /**
     * Constructor with essential parameters.
     *
     * @param productId the product identifier
     * @param storeId the store identifier
     * @param dates the list of dates for the time series
     * @param demandValues the corresponding demand values
     */
    public FeatureTimeSeries(
        String productId,
        String storeId,
        List<LocalDate> dates,
        List<BigDecimal> demandValues
    ) {
        this.productId = productId;
        this.storeId = storeId;
        this.dates = dates;
        this.demandValues = demandValues;
        this.isNormalized = false;
        if (!dates.isEmpty()) {
            this.startDate = dates.get(0);
            this.endDate = dates.get(dates.size() - 1);
        }
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

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public List<LocalDate> getDates() {
        return dates;
    }

    public void setDates(List<LocalDate> dates) {
        this.dates = dates;
    }

    public List<BigDecimal> getDemandValues() {
        return demandValues;
    }

    public void setDemandValues(List<BigDecimal> demandValues) {
        this.demandValues = demandValues;
    }

    public List<BigDecimal> getTrendComponent() {
        return trendComponent;
    }

    public void setTrendComponent(List<BigDecimal> trendComponent) {
        this.trendComponent = trendComponent;
    }

    public List<BigDecimal> getSeasonalComponent() {
        return seasonalComponent;
    }

    public void setSeasonalComponent(List<BigDecimal> seasonalComponent) {
        this.seasonalComponent = seasonalComponent;
    }

    public List<BigDecimal> getLaggedDemand() {
        return laggedDemand;
    }

    public void setLaggedDemand(List<BigDecimal> laggedDemand) {
        this.laggedDemand = laggedDemand;
    }

    public Map<String, List<BigDecimal>> getCustomFeatures() {
        return customFeatures;
    }

    public void setCustomFeatures(
        Map<String, List<BigDecimal>> customFeatures
    ) {
        this.customFeatures = customFeatures;
    }

    public boolean isNormalized() {
        return isNormalized;
    }

    public void setNormalized(boolean normalized) {
        isNormalized = normalized;
    }

    public String getFeatureEngineeringVersion() {
        return featureEngineeringVersion;
    }

    public void setFeatureEngineeringVersion(String featureEngineeringVersion) {
        this.featureEngineeringVersion = featureEngineeringVersion;
    }

    /**
     * Returns the number of time series observations.
     *
     * @return the number of observations
     */
    public int size() {
        return dates != null ? dates.size() : 0;
    }
}
