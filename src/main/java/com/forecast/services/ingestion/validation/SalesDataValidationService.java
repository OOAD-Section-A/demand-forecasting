package com.forecast.services.ingestion.validation;

import com.forecast.models.RawSalesData;
import com.forecast.models.exceptions.ErrorCode;
import com.forecast.models.exceptions.ForecastingException;
import com.forecast.models.exceptions.IMLAlgorithmicExceptionSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * SalesDataValidationService — implements the data cleaning contract for raw sales records.
 *
 * This service satisfies the {@link com.forecast.models.interfaces.ICleanSalesData} contract
 * and integrates with the SCM exception subsystem via {@link IMLAlgorithmicExceptionSource}
 * to report data quality issues (DUPLICATE_SALES_RECORDS, OUTLIER_DETECTED).
 *
 * Validation rules applied:
 * <ul>
 *   <li>productId, storeId, saleDate must not be null/blank</li>
 *   <li>quantitySold must be &ge; 0</li>
 *   <li>unitPrice and revenue must be &ge; 0 if present</li>
 *   <li>Duplicate detection: same productId + storeId + saleDate combination</li>
 *   <li>Outlier detection: demand values more than 3 standard deviations from the mean</li>
 * </ul>
 *
 * @author  Demand Forecasting Team
 * @version 1.0
 */
public class SalesDataValidationService {

    private static final Logger LOG = Logger.getLogger(SalesDataValidationService.class.getName());

    /** Outlier threshold in standard deviations (from exception register: "3 std devs"). */
    private static final double OUTLIER_STD_DEV_THRESHOLD = 3.0;

    private final IMLAlgorithmicExceptionSource exceptionSource;

    /**
     * @param exceptionSource configured exception source; must not be null.
     */
    public SalesDataValidationService(IMLAlgorithmicExceptionSource exceptionSource) {
        this.exceptionSource = Objects.requireNonNull(exceptionSource,
            "exceptionSource must not be null");
        LOG.info("SalesDataValidationService initialised.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Runs the full cleaning pipeline: validate → deduplicate → detect outliers.
     * Returns a list of records that passed all checks.
     *
     * @param rawData raw records from the connector; must not be null
     * @return cleaned, deduplicated, non-outlier records
     */
    public List<RawSalesData> cleanData(List<RawSalesData> rawData) {
        Objects.requireNonNull(rawData, "rawData must not be null");
        LOG.info("Starting data cleaning pipeline on " + rawData.size() + " records.");

        // Step 1 — structural validation
        List<RawSalesData> valid = rawData.stream()
            .filter(this::validateRecord)
            .collect(Collectors.toList());
        LOG.info("After validation: " + valid.size() + " / " + rawData.size() + " records retained.");

        // Step 2 — deduplication
        List<RawSalesData> deduped = removeDuplicates(valid);

        // Step 3 — outlier flagging (we log and exclude outliers)
        List<RawSalesData> outliers = detectOutliers(deduped);
        Set<Long> outlierIds = outliers.stream()
            .map(RawSalesData::getSaleId)
            .collect(Collectors.toSet());

        List<RawSalesData> clean = deduped.stream()
            .filter(r -> !outlierIds.contains(r.getSaleId()))
            .collect(Collectors.toList());

        LOG.info("Cleaning complete. Final record count: " + clean.size());
        return clean;
    }

    /**
     * Validates a single record for structural completeness and non-negative numerics.
     *
     * @param record the record to validate
     * @return true if the record passes all structural rules
     */
    public boolean validateRecord(RawSalesData record) {
        if (record == null) return false;
        if (isBlank(record.getProductId())) {
            LOG.fine("Rejected record saleId=" + record.getSaleId() + ": blank productId");
            return false;
        }
        if (isBlank(record.getStoreId())) {
            LOG.fine("Rejected record saleId=" + record.getSaleId() + ": blank storeId");
            return false;
        }
        if (record.getSaleDate() == null || record.getSaleDate().isAfter(LocalDate.now())) {
            LOG.fine("Rejected record saleId=" + record.getSaleId() + ": invalid saleDate");
            return false;
        }
        if (record.getQuantitySold() < 0) {
            LOG.fine("Rejected record saleId=" + record.getSaleId() + ": negative quantitySold");
            return false;
        }
        if (record.getUnitPrice() != null &&
            record.getUnitPrice().compareTo(BigDecimal.ZERO) < 0) {
            LOG.fine("Rejected record saleId=" + record.getSaleId() + ": negative unitPrice");
            return false;
        }
        return true;
    }

    /**
     * Removes duplicate records (same productId + storeId + saleDate).
     * The first occurrence is kept; subsequent duplicates are discarded.
     * Fires exception ID 454 (repurposed as DUPLICATE_SALES_RECORDS warning) if any found.
     *
     * @param rawData validated records
     * @return deduplicated list
     */
    public List<RawSalesData> removeDuplicates(List<RawSalesData> rawData) {
        Set<String> seen = new HashSet<>();
        List<RawSalesData> deduped = new ArrayList<>();
        int duplicateCount = 0;

        for (RawSalesData r : rawData) {
            String key = r.getProductId() + "|" + r.getStoreId() + "|" + r.getSaleDate();
            if (seen.add(key)) {
                deduped.add(r);
            } else {
                duplicateCount++;
                LOG.fine("Duplicate detected and removed: " + key);
            }
        }

        if (duplicateCount > 0) {
            LOG.warning("Removed " + duplicateCount + " duplicate records.");
            // Fires exception ID 454 (OUTLIER_DETECTED repurposed) — closest available
            // mapping in Category 10. This is a WARNING; pipeline continues.
            exceptionSource.fireModelDegradation(
                454,
                "SalesDataValidationService",
                "duplicate_records",
                0,
                duplicateCount
            );
        }
        return deduped;
    }

    /**
     * Detects outliers using the 3-sigma rule on {@code quantitySold}.
     * Records more than {@value #OUTLIER_STD_DEV_THRESHOLD} standard deviations
     * from the mean are flagged. Fires exception ID 454 (OUTLIER_DETECTED) per outlier group.
     *
     * @param rawData deduplicated records
     * @return list of records identified as outliers
     */
    public List<RawSalesData> detectOutliers(List<RawSalesData> rawData) {
        if (rawData.size() < 4) {
            // Too few records for meaningful statistics
            return List.of();
        }

        double mean = rawData.stream()
            .mapToDouble(RawSalesData::getQuantitySold)
            .average().orElse(0.0);

        double variance = rawData.stream()
            .mapToDouble(r -> Math.pow(r.getQuantitySold() - mean, 2))
            .average().orElse(0.0);

        double stdDev = Math.sqrt(variance);
        double lowerBound = mean - OUTLIER_STD_DEV_THRESHOLD * stdDev;
        double upperBound = mean + OUTLIER_STD_DEV_THRESHOLD * stdDev;

        List<RawSalesData> outliers = rawData.stream()
            .filter(r -> r.getQuantitySold() < lowerBound || r.getQuantitySold() > upperBound)
            .collect(Collectors.toList());

        if (!outliers.isEmpty()) {
            LOG.warning("Detected " + outliers.size() + " outlier records "
                        + "(mean=" + mean + ", stdDev=" + stdDev + ", bounds=["
                        + lowerBound + "," + upperBound + "])");
            // Exception ID 454 — OUTLIER_DETECTED (WARNING)
            exceptionSource.fireModelDegradation(
                454,
                "SalesDataValidationService",
                "quantitySold_zscore",
                OUTLIER_STD_DEV_THRESHOLD,
                outliers.stream().mapToDouble(RawSalesData::getQuantitySold).max().orElse(0)
            );
        }

        return outliers;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
