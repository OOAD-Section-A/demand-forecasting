package com.forecast.integration.warehouse;

import java.util.Map;

/**
 * Adapter interface for integrating the Warehouse Management System (WMS)
 * with the Demand Forecasting system.
 *
 * This interface abstracts the WMS operations and provides a consistent
 * contract for inventory and order management across the two subsystems.
 *
 * @author Demand Forecasting Team
 * @version 1.0
 */
public interface IWarehouseAdapter {

    /**
     * Retrieves the current stock quantity for a specific SKU.
     *
     * @param sku The stock keeping unit identifier
     * @return The current quantity in stock, or -1 if the SKU is not found
     */
    int getStockLevel(String sku);

    /**
     * Retrieves stock levels for multiple SKUs.
     *
     * @param skus A map where key is SKU and value is desired quantity
     * @return A map of SKU to current stock quantity
     */
    Map<String, Integer> getMultipleStockLevels(String... skus);

    /**
     * Reserves stock for a forecasted order.
     * This performs a digital reservation without physical picking.
     *
     * @param sku The product SKU
     * @param quantity The quantity to reserve
     * @return true if reservation was successful, false otherwise
     */
    boolean reserveStock(String sku, int quantity);

    /**
     * Dispatches an order to the warehouse floor for fulfillment.
     * This bridges digital reservation with physical execution.
     *
     * @param orderId The order identifier
     * @param lineItems A map of SKU to quantity for order items
     * @return true if dispatch was successful, false otherwise
     */
    boolean dispatchOrder(String orderId, Map<String, Integer> lineItems);

    /**
     * Receives and stores incoming product inventory.
     *
     * @param sku The product SKU
     * @param productName The product name/description
     * @param quantity The quantity received
     * @param category The product category (e.g., STANDARD, PERISHABLE_COLD, FRAGILE)
     * @return true if receipt and storage was successful, false otherwise
     */
    boolean receiveAndStoreProduct(String sku, String productName, int quantity, String category);

    /**
     * Executes cross-docking for a product, bypassing putaway and routing
     * directly from receiving to shipping for a specific order.
     *
     * @param sku The product SKU
     * @param quantity The quantity to cross-dock
     * @param outboundOrderId The outbound order this is destined for
     * @return true if cross-docking was successful, false otherwise
     */
    boolean processCrossDock(String sku, int quantity, String outboundOrderId);

    /**
     * Checks the health and availability of the warehouse system.
     *
     * @return true if the warehouse system is operational, false otherwise
     */
    boolean isWarehouseOperational();

    /**
     * Retrieves warehouse parameters and configuration.
     *
     * @return A WarehouseParameters object containing warehouse state and policy
     */
    WarehouseParameters getWarehouseParameters();

    /**
     * Records a replenishment/purchase order in the warehouse system.
     *
     * @param orderId The replenishment order ID
     * @param sku The product SKU
     * @param quantity The quantity ordered
     * @param vendorId The supplier/vendor ID
     * @param targetDate The expected delivery date (YYYY-MM-DD format)
     * @return true if the order was recorded successfully, false otherwise
     */
    boolean recordReplenishmentOrder(String orderId, String sku, int quantity,
                                     String vendorId, String targetDate);

    /**
     * Gets detailed warehouse status information including capacity utilization,
     * active tasks, and operational metrics.
     *
     * @return A string containing warehouse status details
     */
    String getWarehouseStatus();

    /**
     * Handles exceptions and alerts from the WMS.
     * This method fires or retrieves any pending alerts/exceptions.
     *
     * @return true if there are pending alerts, false otherwise
     */
    boolean hasPendingAlerts();

    /**
     * Closes the adapter connection and cleans up resources.
     */
    void close();
}
