package com.forecast.integration.inventory;

import java.time.LocalDate;
import java.util.List;

/**
 * IInventoryAdapter defines the contract for integrating with the inventory management subsystem.
 * This adapter abstracts the inventory system's operations, allowing the demand forecasting system
 * to request inventory information and trigger stock management operations.
 *
 * Responsibilities:
 * - Provide access to current inventory levels
 * - Supply inventory metadata (thresholds, safety stock levels)
 * - Record replenishment orders
 * - Query inventory history and status
 *
 * @author Integration Team
 * @version 1.0
 */
public interface IInventoryAdapter {

    /**
     * Retrieves the current stock level for a product at a specific location.
     *
     * @param productId the product identifier
     * @param locationId the location/store identifier
     * @return the current total quantity in stock, or 0 if product not found
     */
    int getCurrentStockLevel(String productId, String locationId);

    /**
     * Retrieves the available quantity (total - reserved) for a product at a location.
     *
     * @param productId the product identifier
     * @param locationId the location/store identifier
     * @return the available quantity, or 0 if product not found
     */
    int getAvailableQuantity(String productId, String locationId);

    /**
     * Gets the reserved quantity for a product at a location.
     *
     * @param productId the product identifier
     * @param locationId the location/store identifier
     * @return the reserved quantity
     */
    int getReservedQuantity(String productId, String locationId);

    /**
     * Retrieves the safety stock level for a product.
     *
     * @param productId the product identifier
     * @return the safety stock level
     */
    int getSafetyStockLevel(String productId);

    /**
     * Retrieves the reorder threshold for a product.
     *
     * @param productId the product identifier
     * @return the reorder threshold
     */
    int getReorderThreshold(String productId);

    /**
     * Records a replenishment order in the inventory system.
     *
     * @param productId the product identifier
     * @param locationId the location/store identifier
     * @param quantity the quantity to replenish
     * @param supplierId the supplier identifier
     * @param referenceId the order/reference ID for tracking
     * @return true if the order was successfully recorded, false otherwise
     */
    boolean recordReplenishmentOrder(
        String productId,
        String locationId,
        int quantity,
        String supplierId,
        String referenceId
    );

    /**
     * Checks if a product exists in the inventory system.
     *
     * @param productId the product identifier
     * @param locationId the location/store identifier
     * @return true if the product exists at the location, false otherwise
     */
    boolean productExists(String productId, String locationId);

    /**
     * Retrieves the ABC category classification for a product.
     * ABC analysis categorizes products based on their importance:
     * - A: High value, high importance
     * - B: Medium value, medium importance
     * - C: Low value, lower importance
     *
     * @param productId the product identifier
     * @return the ABC category (A, B, or C)
     */
    String getABCCategory(String productId);

    /**
     * Retrieves the average daily consumption for a product based on historical data.
     *
     * @param productId the product identifier
     * @param locationId the location/store identifier
     * @param daysBack the number of days to look back for historical data
     * @return the average daily consumption, or 0.0 if insufficient data
     */
    double getAverageDailyConsumption(String productId, String locationId, int daysBack);

    /**
     * Calculates days until potential stockout based on current consumption rate.
     *
     * @param productId the product identifier
     * @param locationId the location/store identifier
     * @return estimated days until stockout, or -1 if product doesn't exist or consumption is 0
     */
    int calculateDaysUntilStockout(String productId, String locationId);

    /**
     * Retrieves the lead time (in days) for replenishing a product from the default supplier.
     *
     * @param productId the product identifier
     * @return the lead time in days
     */
    int getLeadTime(String productId);

    /**
     * Validates whether the inventory system is currently accessible and operational.
     *
     * @return true if the inventory system is operational, false otherwise
     */
    boolean isInventorySystemAvailable();

    /**
     * Releases a reserved quantity back to available inventory.
     *
     * @param productId the product identifier
     * @param locationId the location/store identifier
     * @param quantity the quantity to release from reservation
     * @return true if the release was successful, false otherwise
     */
    boolean releaseReservation(String productId, String locationId, int quantity);

    /**
     * Retrieves the total inventory value (in currency) for a product across all locations.
     *
     * @param productId the product identifier
     * @return the total inventory value
     */
    double getTotalInventoryValue(String productId);
}
