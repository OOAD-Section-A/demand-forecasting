package com.forecast.services.ingestion.feature;

import com.forecast.models.FeatureTimeSeries;
import com.forecast.models.PatternProfile;
import com.forecast.models.PromoData;
import com.forecast.models.RawSalesData;
import com.forecast.models.exceptions.ErrorCode;
import com.forecast.models.exceptions.ForecastingException;
import com.forecast.models.exceptions.IMLAlgorithmicExceptionSource;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * FeatureEngineeringService transforms cleaned {@link RawSalesData} records into
 * {@link FeatureTimeSeries} objects ready for ingestion by the forecasting strategies.
 *
 * Responsibilities:
 * - Build demand time series from raw sales records
 * - Compute lagged demand features
 * - Decompose trend and seasonal components (placeholder implementations)
 * - Detect demand patterns and produce a {@link PatternProfile}
 * - Inject promotional lift where promo data is available
 * - Flag and report via {@link IMLAlgorithmicExceptionSource} when steps are skipped
 *
 * SOLID — SRP: this service only performs feature engineering.
 *         DIP: depends on IMLAlgorithmicExceptionSource, not a concrete handler.
 *
 * @author  Demand Forecasting Team
 * @version 1.0
 */
public class FeatureEngineeringService {

    private static final Logger LOG = Logger.getLogger(
        FeatureEngineeringService.class.getName()
    );

    /** Minimum lag window for lagged demand feature (weeks). */
    private static final int LAG_WINDOW = 4;

    private final IMLAlgorithmicExceptionSource exceptionSource;

    /**
     * @param exceptionSource configured exception source with a registered handler;
     *                        must not be null.
     */
    public FeatureEngineeringService(
        IMLAlgorithmicExceptionSource exceptionSource
    ) {
        if (exceptionSource == null) {
            throw new IllegalArgumentException(
                "exceptionSource must not be null"
            );
        }
        this.exceptionSource = exceptionSource;
        LOG.info("FeatureEngineeringService initialised.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds a {@link FeatureTimeSeries} from a list of raw sales records for a
     * specific product–store combination.
     *
     * @param productId  the product to build features for
     * @param storeId    the store to build features for
     * @param rawRecords all raw records (will be filtered internally)
     * @param promoData  promotional data for the product–store pair; may be null
     * @return populated FeatureTimeSeries; never null
     * @throws ForecastingException with FEATURE_ENGINEERING_SKIPPED if critical
     *         steps cannot run due to empty / null input
     */
    public FeatureTimeSeries buildFeatures(
        String productId,
        String storeId,
        List<RawSalesData> rawRecords,
        List<PromoData> promoData
    ) {
        LOG.info(
            "Building features for product=[" +
                productId +
                "] store=[" +
                storeId +
                "]"
        );

        if (rawRecords == null || rawRecords.isEmpty()) {
            // Fire exception ID 455 — FEATURE_ENGINEERING_SKIPPED
            exceptionSource.fireModelDegradation(
                455,
                "FeatureEngineeringService",
                "input_records",
                1,
                0
            );
            throw new ForecastingException(
                ErrorCode.FEATURE_ENGINEERING_SKIPPED,
                "No raw records available for product=" +
                    productId +
                    " store=" +
                    storeId
            );
        }

        // Filter to this product-store and sort by date
        List<RawSalesData> filtered = rawRecords
            .stream()
            .filter(
                r ->
                    productId.equals(r.getProductId()) &&
                    storeId.equals(r.getStoreId())
            )
            .sorted((a, b) -> a.getSaleDate().compareTo(b.getSaleDate()))
            .collect(Collectors.toList());

        List<LocalDate> dates = filtered
            .stream()
            .map(RawSalesData::getSaleDate)
            .collect(Collectors.toList());

        List<BigDecimal> demandValues = filtered
            .stream()
            .map(r -> BigDecimal.valueOf(r.getQuantitySold()))
            .collect(Collectors.toList());

        FeatureTimeSeries fts = new FeatureTimeSeries(
            productId,
            storeId,
            dates,
            demandValues
        );
        fts.setFeatureEngineeringVersion("1.0");

        // Compute lagged demand
        fts.setLaggedDemand(computeLaggedDemand(demandValues, LAG_WINDOW));

        // Trend and seasonal decomposition (stubbed — requires statistical library)
        try {
            fts.setTrendComponent(computeTrend(demandValues));
            fts.setSeasonalComponent(computeSeasonalIndex(demandValues));
        } catch (Exception ex) {
            LOG.warning(
                "Trend/seasonal decomposition skipped: " + ex.getMessage()
            );
            // ID 455 — FEATURE_ENGINEERING_SKIPPED (non-fatal: continue with partial features)
            exceptionSource.fireModelDegradation(
                455,
                "FeatureEngineeringService",
                "decomposition_step",
                1,
                0
            );
        }

        // Inject promotional lift if promo data is available
        if (promoData == null || promoData.isEmpty()) {
            LOG.warning(
                "No promo data supplied for product=[" +
                    productId +
                    "]. " +
                    "Promotional features will be absent."
            );
            // ID 458 — MISSING_PROMOTIONAL_DATA
            exceptionSource.fireMissingInputData(
                458,
                "FeatureEngineeringService",
                "PromoCalendar",
                fts.getStartDate() + "/" + fts.getEndDate()
            );
        }

        LOG.info("Feature build complete: " + fts.size() + " observations.");
        return fts;
    }

    /**
     * Analyses a {@link FeatureTimeSeries} and produces a {@link PatternProfile}.
     *
     * @param fts the feature time series to analyse
     * @return a PatternProfile describing the detected demand patterns
     */
    public PatternProfile detectPatterns(FeatureTimeSeries fts) {
        LOG.info("Detecting patterns for product=[" + fts.getProductId() + "]");

        PatternProfile profile = new PatternProfile(
            fts.getProductId(),
            fts.getStoreId(),
            "UNKNOWN"
        );

        if (fts.getDemandValues() == null || fts.getDemandValues().isEmpty()) {
            profile.setDominantPattern("INSUFFICIENT_DATA");
            profile.setRequiresSpecialHandling(true);
            return profile;
        }

        // TODO: Replace with proper statistical pattern detection
        //  (ACF/PACF analysis, STL decomposition, spectral analysis)
        double coefficientOfVariation = computeCoefficientOfVariation(
            fts.getDemandValues()
        );
        profile.setNoiseLevel(coefficientOfVariation);
        profile.setPatternStability(
            1.0 - Math.min(coefficientOfVariation, 1.0)
        );

        if (coefficientOfVariation < 0.2) {
            profile.setDominantPattern("STABLE");
            profile.setForecastingRecommendation("ARIMA_HOLT_WINTERS");
        } else if (coefficientOfVariation < 0.5) {
            profile.setDominantPattern("SEASONAL");
            profile.setForecastingRecommendation("ARIMA_HOLT_WINTERS");
        } else {
            profile.setDominantPattern("VOLATILE");
            profile.setForecastingRecommendation("PROPHET_LSTM");
            profile.setRequiresSpecialHandling(true);
        }

        LOG.info(
            "Pattern detected: " +
                profile.getDominantPattern() +
                ", recommendation: " +
                profile.getForecastingRecommendation()
        );
        return profile;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers — statistical computations (stub implementations)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Computes lagged demand: for each position i, the lagged value is the average
     * of the previous {@code lagWindow} observations (or fewer if near the start).
     */
    private List<BigDecimal> computeLaggedDemand(
        List<BigDecimal> demand,
        int lagWindow
    ) {
        List<BigDecimal> lagged = new ArrayList<>(demand.size());
        for (int i = 0; i < demand.size(); i++) {
            int start = Math.max(0, i - lagWindow);
            BigDecimal sum = BigDecimal.ZERO;
            for (int j = start; j < i; j++) {
                sum = sum.add(demand.get(j));
            }
            int count = i - start;
            lagged.add(
                count == 0
                    ? BigDecimal.ZERO
                    : sum.divide(
                          BigDecimal.valueOf(count),
                          MathContext.DECIMAL64
                      )
            );
        }
        return lagged;
    }

    /**
     * Computes a simple linear trend component using a centred moving average (stub).
     * TODO: Replace with proper STL or polynomial regression.
     */
    private List<BigDecimal> computeTrend(List<BigDecimal> demand) {
        int window = Math.max(3, demand.size() / 10);
        List<BigDecimal> trend = new ArrayList<>(demand.size());
        for (int i = 0; i < demand.size(); i++) {
            int start = Math.max(0, i - window / 2);
            int end = Math.min(demand.size(), i + window / 2 + 1);
            BigDecimal sum = BigDecimal.ZERO;
            for (int j = start; j < end; j++) {
                sum = sum.add(demand.get(j));
            }
            trend.add(
                sum.divide(
                    BigDecimal.valueOf(end - start),
                    MathContext.DECIMAL64
                )
            );
        }
        return trend;
    }

    /**
     * Computes a simplified seasonal index (demand / trend ratio) (stub).
     * TODO: Replace with proper multiplicative seasonal decomposition.
     */
    private List<BigDecimal> computeSeasonalIndex(List<BigDecimal> demand) {
        List<BigDecimal> trend = computeTrend(demand);
        List<BigDecimal> seasonal = new ArrayList<>(demand.size());
        for (int i = 0; i < demand.size(); i++) {
            BigDecimal t = trend.get(i);
            if (t.compareTo(BigDecimal.ZERO) == 0) {
                seasonal.add(BigDecimal.ONE);
            } else {
                seasonal.add(demand.get(i).divide(t, MathContext.DECIMAL64));
            }
        }
        return seasonal;
    }

    /**
     * Computes the Coefficient of Variation (stdDev / mean) as a measure of noise.
     */
    private double computeCoefficientOfVariation(List<BigDecimal> values) {
        if (values.isEmpty()) return 0.0;
        double sum = values.stream().mapToDouble(BigDecimal::doubleValue).sum();
        double mean = sum / values.size();
        if (mean == 0) return 1.0;
        double variance = values
            .stream()
            .mapToDouble(v -> Math.pow(v.doubleValue() - mean, 2))
            .average()
            .orElse(0.0);
        return Math.sqrt(variance) / mean;
    }
}
