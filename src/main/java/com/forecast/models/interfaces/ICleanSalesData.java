package com.forecast.models.interfaces;

import com.forecast.models.RawSalesData;
import java.util.List;

/**
 * ICleanSalesData defines the contract for cleaning and validating raw sales data.
 * Implementations of this interface handle data validation, normalization,
 * and preprocessing operations to prepare raw sales data for forecasting.
 *
 * Responsibilities:
 * - Validate data integrity and consistency
 * - Remove or flag duplicate records
 * - Handle missing or null values
 * - Normalize data formats and units
 * - Detect and handle outliers
 * - Apply data quality rules
 *
 * @author Demand Forecasting Team
 * @version 1.0
 */
public interface ICleanSalesData {
    /**
     * Cleans a list of raw sales data records.
     *
     * @param rawData list of raw sales data to clean
     * @return list of cleaned and validated sales data
     */
    List<RawSalesData> cleanData(List<RawSalesData> rawData);

    /**
     * Validates a single sales data record for quality and completeness.
     *
     * @param record the sales data record to validate
     * @return true if the record is valid, false otherwise
     */
    boolean validateRecord(RawSalesData record);

    /**
     * Removes duplicate records from the sales data.
     *
     * @param rawData list of sales data to deduplicate
     * @return list of deduplicated sales data
     */
    List<RawSalesData> removeDuplicates(List<RawSalesData> rawData);

    /**
     * Detects and flags outliers in the sales data.
     *
     * @param rawData list of sales data to analyze
     * @return list of records identified as outliers
     */
    List<RawSalesData> detectOutliers(List<RawSalesData> rawData);
}
