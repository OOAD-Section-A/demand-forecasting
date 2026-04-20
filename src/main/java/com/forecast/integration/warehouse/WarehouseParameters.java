package com.forecast.integration.warehouse;

/**
 * Model representing warehouse parameters and configuration settings.
 *
 * This class encapsulates warehouse-specific state information and policy
 * parameters that are used by the forecasting system to generate actionable
 * replenishment recommendations for the warehouse.
 *
 * @author Demand Forecasting Team
 * @version 1.0
 */
public class WarehouseParameters {

    private String warehouseId;
    private String warehouseName;
    private String location;

    // Capacity and utilization
    private int totalCapacity;
    private int currentUtilization;
    private double utilizationThreshold;

    // Operational parameters
    private int defaultLeadTimeDays;
    private double safetyStockMultiplier;
    private int minimumOrderQuantity;
    private int maximumOrderQuantity;
    private int reorderThreshold;

    // Warehouse status
    private boolean operational;
    private long lastUpdateTimestamp;

    // Performance metrics
    private double averagePickingTimeMinutes;
    private double averageReceivingTimeMinutes;
    private int activeTaskCount;

    /**
     * Default constructor with reasonable defaults.
     */
    public WarehouseParameters() {
        this.warehouseId = "WH-DEFAULT";
        this.warehouseName = "Default Warehouse";
        this.location = "Unknown";
        this.totalCapacity = 10000;
        this.currentUtilization = 0;
        this.utilizationThreshold = 0.8;
        this.defaultLeadTimeDays = 7;
        this.safetyStockMultiplier = 1.5;
        this.minimumOrderQuantity = 10;
        this.maximumOrderQuantity = 5000;
        this.reorderThreshold = 100;
        this.operational = true;
        this.lastUpdateTimestamp = System.currentTimeMillis();
        this.averagePickingTimeMinutes = 15.0;
        this.averageReceivingTimeMinutes = 30.0;
        this.activeTaskCount = 0;
    }

    // Getters and Setters

    public String getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(String warehouseId) {
        this.warehouseId = warehouseId;
    }

    public String getWarehouseName() {
        return warehouseName;
    }

    public void setWarehouseName(String warehouseName) {
        this.warehouseName = warehouseName;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public int getTotalCapacity() {
        return totalCapacity;
    }

    public void setTotalCapacity(int totalCapacity) {
        this.totalCapacity = totalCapacity;
    }

    public int getCurrentUtilization() {
        return currentUtilization;
    }

    public void setCurrentUtilization(int currentUtilization) {
        this.currentUtilization = currentUtilization;
    }

    public double getUtilizationThreshold() {
        return utilizationThreshold;
    }

    public void setUtilizationThreshold(double utilizationThreshold) {
        this.utilizationThreshold = utilizationThreshold;
    }

    public int getDefaultLeadTimeDays() {
        return defaultLeadTimeDays;
    }

    public void setDefaultLeadTimeDays(int defaultLeadTimeDays) {
        this.defaultLeadTimeDays = defaultLeadTimeDays;
    }

    public double getSafetyStockMultiplier() {
        return safetyStockMultiplier;
    }

    public void setSafetyStockMultiplier(double safetyStockMultiplier) {
        this.safetyStockMultiplier = safetyStockMultiplier;
    }

    public int getMinimumOrderQuantity() {
        return minimumOrderQuantity;
    }

    public void setMinimumOrderQuantity(int minimumOrderQuantity) {
        this.minimumOrderQuantity = minimumOrderQuantity;
    }

    public int getMaximumOrderQuantity() {
        return maximumOrderQuantity;
    }

    public void setMaximumOrderQuantity(int maximumOrderQuantity) {
        this.maximumOrderQuantity = maximumOrderQuantity;
    }

    public int getReorderThreshold() {
        return reorderThreshold;
    }

    public void setReorderThreshold(int reorderThreshold) {
        this.reorderThreshold = reorderThreshold;
    }

    public boolean isOperational() {
        return operational;
    }

    public void setOperational(boolean operational) {
        this.operational = operational;
    }

    public long getLastUpdateTimestamp() {
        return lastUpdateTimestamp;
    }

    public void setLastUpdateTimestamp(long lastUpdateTimestamp) {
        this.lastUpdateTimestamp = lastUpdateTimestamp;
    }

    public double getAveragePickingTimeMinutes() {
        return averagePickingTimeMinutes;
    }

    public void setAveragePickingTimeMinutes(double averagePickingTimeMinutes) {
        this.averagePickingTimeMinutes = averagePickingTimeMinutes;
    }

    public double getAverageReceivingTimeMinutes() {
        return averageReceivingTimeMinutes;
    }

    public void setAverageReceivingTimeMinutes(double averageReceivingTimeMinutes) {
        this.averageReceivingTimeMinutes = averageReceivingTimeMinutes;
    }

    public int getActiveTaskCount() {
        return activeTaskCount;
    }

    public void setActiveTaskCount(int activeTaskCount) {
        this.activeTaskCount = activeTaskCount;
    }

    /**
     * Calculates the available capacity in the warehouse.
     *
     * @return The available capacity (total - current utilization)
     */
    public int getAvailableCapacity() {
        return totalCapacity - currentUtilization;
    }

    /**
     * Calculates the current utilization percentage.
     *
     * @return The utilization percentage (0-100)
     */
    public double getUtilizationPercentage() {
        if (totalCapacity == 0) return 0;
        return (double) currentUtilization / totalCapacity * 100;
    }

    /**
     * Checks if the warehouse is nearing capacity.
     *
     * @return true if utilization percentage exceeds the threshold
     */
    public boolean isNearCapacity() {
        return getUtilizationPercentage() >= (utilizationThreshold * 100);
    }

    @Override
    public String toString() {
        return "WarehouseParameters{" +
                "warehouseId='" + warehouseId + '\'' +
                ", warehouseName='" + warehouseName + '\'' +
                ", location='" + location + '\'' +
                ", totalCapacity=" + totalCapacity +
                ", currentUtilization=" + currentUtilization +
                ", utilizationPercentage=" + String.format("%.2f", getUtilizationPercentage()) + "%" +
                ", defaultLeadTimeDays=" + defaultLeadTimeDays +
                ", safetyStockMultiplier=" + safetyStockMultiplier +
                ", minimumOrderQuantity=" + minimumOrderQuantity +
                ", maximumOrderQuantity=" + maximumOrderQuantity +
                ", reorderThreshold=" + reorderThreshold +
                ", operational=" + operational +
                ", activeTaskCount=" + activeTaskCount +
                '}';
    }
}
