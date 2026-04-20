package com.forecast.integration.inventory;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * InventoryParameters encapsulates all configuration and state parameters
 * needed to make inventory replenishment decisions based on forecasts.
 *
 * This class bridges the demand forecasting domain with inventory management,
 * capturing both forecast-driven metrics and inventory-specific constraints.
 *
 * @author Integration Team
 * @version 1.0
 */
public class InventoryParameters {

    // Product and Location Identifiers
    private String productId;
    private String locationId;
    private String storeId;

    // Current Inventory State
    private int currentStockLevel;
    private int reservedQuantity;
    private int availableQuantity;

    // Inventory Policy Parameters
    private int safetyStockLevel;
    private int reorderThreshold;
    private int economicOrderQuantity;
    private int maximumStockLevel;
    private int minimumStockLevel;

    // Lead Time and Service Level
    private int replenishmentLeadTimeDays;
    private BigDecimal targetServiceLevel; // 0.0 to 1.0, e.g., 0.95 for 95%

    // Demand Characteristics (from forecasts)
    private BigDecimal averageDailyDemand;
    private BigDecimal peakDemand;
    private BigDecimal demandStandardDeviation;
    private BigDecimal demandVariabilityCoefficient; // coefficient of variation

    // ABC Classification
    private String abcCategory; // A, B, or C
    private int annualUsageValue; // in units or currency

    // Inventory Metrics
    private BigDecimal holdingCostPerUnit; // storage, insurance, etc.
    private BigDecimal orderingCostPerOrder; // setup, handling, etc.
    private BigDecimal stockoutCostPerUnit; // lost sales, goodwill, etc.

    // Temporal Information
    private LocalDate lastReplenishmentDate;
    private LocalDate lastDemandDate;
    private int daysInCurrentCycle;

    // Forecast Integration Data
    private BigDecimal forecastedAverageDemand;
    private BigDecimal forecastedPeakDemand;
    private BigDecimal confidenceIntervalLower;
    private BigDecimal confidenceIntervalUpper;
    private LocalDate forecastPeriodStart;
    private LocalDate forecastPeriodEnd;
    private String lifecycleStage;
    private String forecastModel;

    // Status and Flags
    private boolean isActive;
    private boolean requiresSpecialHandling;
    private String status; // NORMAL, CRITICAL, OBSOLETE, etc.

    public InventoryParameters() {
    }

    public InventoryParameters(String productId, String locationId, String storeId) {
        this.productId = productId;
        this.locationId = locationId;
        this.storeId = storeId;
        this.isActive = true;
        this.currentStockLevel = 0;
        this.reservedQuantity = 0;
        this.availableQuantity = 0;
    }

    // Getters and Setters

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getLocationId() {
        return locationId;
    }

    public void setLocationId(String locationId) {
        this.locationId = locationId;
    }

    public String getStoreId() {
        return storeId;
    }

    public void setStoreId(String storeId) {
        this.storeId = storeId;
    }

    public int getCurrentStockLevel() {
        return currentStockLevel;
    }

    public void setCurrentStockLevel(int currentStockLevel) {
        this.currentStockLevel = currentStockLevel;
    }

    public int getReservedQuantity() {
        return reservedQuantity;
    }

    public void setReservedQuantity(int reservedQuantity) {
        this.reservedQuantity = reservedQuantity;
    }

    public int getAvailableQuantity() {
        return availableQuantity;
    }

    public void setAvailableQuantity(int availableQuantity) {
        this.availableQuantity = availableQuantity;
    }

    public int getSafetyStockLevel() {
        return safetyStockLevel;
    }

    public void setSafetyStockLevel(int safetyStockLevel) {
        this.safetyStockLevel = safetyStockLevel;
    }

    public int getReorderThreshold() {
        return reorderThreshold;
    }

    public void setReorderThreshold(int reorderThreshold) {
        this.reorderThreshold = reorderThreshold;
    }

    public int getEconomicOrderQuantity() {
        return economicOrderQuantity;
    }

    public void setEconomicOrderQuantity(int economicOrderQuantity) {
        this.economicOrderQuantity = economicOrderQuantity;
    }

    public int getMaximumStockLevel() {
        return maximumStockLevel;
    }

    public void setMaximumStockLevel(int maximumStockLevel) {
        this.maximumStockLevel = maximumStockLevel;
    }

    public int getMinimumStockLevel() {
        return minimumStockLevel;
    }

    public void setMinimumStockLevel(int minimumStockLevel) {
        this.minimumStockLevel = minimumStockLevel;
    }

    public int getReplenishmentLeadTimeDays() {
        return replenishmentLeadTimeDays;
    }

    public void setReplenishmentLeadTimeDays(int replenishmentLeadTimeDays) {
        this.replenishmentLeadTimeDays = replenishmentLeadTimeDays;
    }

    public BigDecimal getTargetServiceLevel() {
        return targetServiceLevel;
    }

    public void setTargetServiceLevel(BigDecimal targetServiceLevel) {
        this.targetServiceLevel = targetServiceLevel;
    }

    public BigDecimal getAverageDailyDemand() {
        return averageDailyDemand;
    }

    public void setAverageDailyDemand(BigDecimal averageDailyDemand) {
        this.averageDailyDemand = averageDailyDemand;
    }

    public BigDecimal getPeakDemand() {
        return peakDemand;
    }

    public void setPeakDemand(BigDecimal peakDemand) {
        this.peakDemand = peakDemand;
    }

    public BigDecimal getDemandStandardDeviation() {
        return demandStandardDeviation;
    }

    public void setDemandStandardDeviation(BigDecimal demandStandardDeviation) {
        this.demandStandardDeviation = demandStandardDeviation;
    }

    public BigDecimal getDemandVariabilityCoefficient() {
        return demandVariabilityCoefficient;
    }

    public void setDemandVariabilityCoefficient(BigDecimal demandVariabilityCoefficient) {
        this.demandVariabilityCoefficient = demandVariabilityCoefficient;
    }

    public String getAbcCategory() {
        return abcCategory;
    }

    public void setAbcCategory(String abcCategory) {
        this.abcCategory = abcCategory;
    }

    public int getAnnualUsageValue() {
        return annualUsageValue;
    }

    public void setAnnualUsageValue(int annualUsageValue) {
        this.annualUsageValue = annualUsageValue;
    }

    public BigDecimal getHoldingCostPerUnit() {
        return holdingCostPerUnit;
    }

    public void setHoldingCostPerUnit(BigDecimal holdingCostPerUnit) {
        this.holdingCostPerUnit = holdingCostPerUnit;
    }

    public BigDecimal getOrderingCostPerOrder() {
        return orderingCostPerOrder;
    }

    public void setOrderingCostPerOrder(BigDecimal orderingCostPerOrder) {
        this.orderingCostPerOrder = orderingCostPerOrder;
    }

    public BigDecimal getStockoutCostPerUnit() {
        return stockoutCostPerUnit;
    }

    public void setStockoutCostPerUnit(BigDecimal stockoutCostPerUnit) {
        this.stockoutCostPerUnit = stockoutCostPerUnit;
    }

    public LocalDate getLastReplenishmentDate() {
        return lastReplenishmentDate;
    }

    public void setLastReplenishmentDate(LocalDate lastReplenishmentDate) {
        this.lastReplenishmentDate = lastReplenishmentDate;
    }

    public LocalDate getLastDemandDate() {
        return lastDemandDate;
    }

    public void setLastDemandDate(LocalDate lastDemandDate) {
        this.lastDemandDate = lastDemandDate;
    }

    public int getDaysInCurrentCycle() {
        return daysInCurrentCycle;
    }

    public void setDaysInCurrentCycle(int daysInCurrentCycle) {
        this.daysInCurrentCycle = daysInCurrentCycle;
    }

    public BigDecimal getForecastedAverageDemand() {
        return forecastedAverageDemand;
    }

    public void setForecastedAverageDemand(BigDecimal forecastedAverageDemand) {
        this.forecastedAverageDemand = forecastedAverageDemand;
    }

    public BigDecimal getForecastedPeakDemand() {
        return forecastedPeakDemand;
    }

    public void setForecastedPeakDemand(BigDecimal forecastedPeakDemand) {
        this.forecastedPeakDemand = forecastedPeakDemand;
    }

    public BigDecimal getConfidenceIntervalLower() {
        return confidenceIntervalLower;
    }

    public void setConfidenceIntervalLower(BigDecimal confidenceIntervalLower) {
        this.confidenceIntervalLower = confidenceIntervalLower;
    }

    public BigDecimal getConfidenceIntervalUpper() {
        return confidenceIntervalUpper;
    }

    public void setConfidenceIntervalUpper(BigDecimal confidenceIntervalUpper) {
        this.confidenceIntervalUpper = confidenceIntervalUpper;
    }

    public LocalDate getForecastPeriodStart() {
        return forecastPeriodStart;
    }

    public void setForecastPeriodStart(LocalDate forecastPeriodStart) {
        this.forecastPeriodStart = forecastPeriodStart;
    }

    public LocalDate getForecastPeriodEnd() {
        return forecastPeriodEnd;
    }

    public void setForecastPeriodEnd(LocalDate forecastPeriodEnd) {
        this.forecastPeriodEnd = forecastPeriodEnd;
    }

    public String getLifecycleStage() {
        return lifecycleStage;
    }

    public void setLifecycleStage(String lifecycleStage) {
        this.lifecycleStage = lifecycleStage;
    }

    public String getForecastModel() {
        return forecastModel;
    }

    public void setForecastModel(String forecastModel) {
        this.forecastModel = forecastModel;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public boolean isRequiresSpecialHandling() {
        return requiresSpecialHandling;
    }

    public void setRequiresSpecialHandling(boolean requiresSpecialHandling) {
        this.requiresSpecialHandling = requiresSpecialHandling;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "InventoryParameters{" +
                "productId='" + productId + '\'' +
                ", locationId='" + locationId + '\'' +
                ", currentStockLevel=" + currentStockLevel +
                ", safetyStockLevel=" + safetyStockLevel +
                ", reorderThreshold=" + reorderThreshold +
                ", forecastedAverageDemand=" + forecastedAverageDemand +
                ", status='" + status + '\'' +
                '}';
    }
}
