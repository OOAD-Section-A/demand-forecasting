package com.forecast.services.engine.lifecycle;

import com.forecast.models.LifecycleContent;
import com.forecast.models.PatternProfile;

import java.sql.*;
import java.time.LocalDate;
import java.util.List;
import java.util.logging.Logger;

/**
 * LifeCycleManager — manages product lifecycle stage resolution, forecast-horizon
 * determination, and strategy recommendation.
 *
 * <h3>Stage resolution (priority order)</h3>
 * <ol>
 *   <li>Live database lookup — queries {@code product_lifecycle_stages} for the row
 *       with the latest {@code stage_start_date} that is not in the future.
 *       Also supports store-level overrides via {@code store_lifecycle_overrides}.</li>
 *   <li>Heuristic default — if no DB row exists, returns MATURITY with a 6-month
 *       horizon and fires a LIFECYCLE_STAGE_NOT_SET warning.</li>
 * </ol>
 *
 * <h3>Store-level lifecycle overrides</h3>
 * When a row exists in {@code store_lifecycle_overrides} for the given
 * (productId, storeId), that row's stage takes precedence over the product-level
 * stage from {@code product_lifecycle_stages}.  This allows per-store
 * customisation without touching the global lifecycle record.
 *
 * @author  Demand Forecasting Team
 * @version 2.0
 */
public class LifeCycleManager {

    private static final Logger LOG = Logger.getLogger(LifeCycleManager.class.getName());

    // ── SQL ───────────────────────────────────────────────────────────────────

    /** Fetch the current product-level lifecycle stage. */
    private static final String SQL_PRODUCT_LIFECYCLE =
        "SELECT current_stage, stage_start_date, transition_date " +
        "FROM   product_lifecycle_stages " +
        "WHERE  product_id = ? " +
        "  AND  stage_start_date <= CURDATE() " +
        "ORDER  BY stage_start_date DESC " +
        "LIMIT  1";

    /**
     * Fetch a store-level lifecycle override.
     * This table is: store_lifecycle_overrides(product_id, store_id, current_stage,
     *   override_start_date, notes)
     */
    private static final String SQL_STORE_OVERRIDE =
        "SELECT current_stage, override_start_date, notes " +
        "FROM   store_lifecycle_overrides " +
        "WHERE  product_id = ? AND store_id = ? " +
        "  AND  override_start_date <= CURDATE() " +
        "ORDER  BY override_start_date DESC " +
        "LIMIT  1";

    // ── Fields ────────────────────────────────────────────────────────────────

    /** JDBC URL; null means offline/heuristic mode. */
    private final String jdbcUrl;
    private final String dbUser;
    private final String dbPassword;

    // ── Constructors ──────────────────────────────────────────────────────────

    /**
     * Online constructor — connects to the database for live lifecycle lookups.
     *
     * @param jdbcUrl    JDBC connection string (e.g. "jdbc:mysql://localhost:3306/OOAD")
     * @param dbUser     database username
     * @param dbPassword database password
     */
    public LifeCycleManager(String jdbcUrl, String dbUser, String dbPassword) {
        this.jdbcUrl    = jdbcUrl;
        this.dbUser     = dbUser;
        this.dbPassword = dbPassword;
        LOG.info("LifeCycleManager initialised (DB-backed mode).");
    }

    /**
     * Offline constructor — uses heuristic defaults only (no DB access).
     * Useful for tests or environments where the lifecycle registry is not available.
     */
    public LifeCycleManager() {
        this.jdbcUrl    = null;
        this.dbUser     = null;
        this.dbPassword = null;
        LOG.info("LifeCycleManager initialised (heuristic/offline mode).");
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Resolves the current lifecycle stage for a product at a specific store.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>Store-level override from {@code store_lifecycle_overrides}</li>
     *   <li>Product-level stage from {@code product_lifecycle_stages}</li>
     *   <li>Heuristic default (MATURITY, 6-month horizon)</li>
     * </ol>
     *
     * @param productId product identifier
     * @param storeId   store identifier (used for override lookup)
     * @return resolved LifecycleContent; never null
     */
    public LifecycleContent determineLifecycleStage(String productId, String storeId) {
        LOG.info("Resolving lifecycle for product=[" + productId + "] store=[" + storeId + "]");

        if (jdbcUrl != null) {
            // 1. Try store-level override
            LifecycleContent override = fetchStoreOverride(productId, storeId);
            if (override != null) {
                LOG.info("Store-level lifecycle override applied: product=[" + productId
                    + "] store=[" + storeId + "] stage=" + override.getCurrentStage());
                return override;
            }

            // 2. Try product-level stage
            LifecycleContent productLevel = fetchProductLifecycle(productId);
            if (productLevel != null) {
                LOG.info("Product-level lifecycle resolved: product=[" + productId
                    + "] stage=" + productLevel.getCurrentStage());
                return productLevel;
            }
        }

        // 3. Heuristic default
        LOG.warning("No lifecycle record found for product=[" + productId
            + "] store=[" + storeId + "]. Defaulting to MATURITY.");
        return buildDefault(productId);
    }

    /**
     * Updates the lastUpdated timestamp on an in-memory LifecycleContent object.
     * Does not write to the database (lifecycle records are managed externally).
     */
    public boolean updateLifecycleStage(LifecycleContent lifecycle) {
        if (lifecycle == null) return false;
        lifecycle.setLastUpdated(LocalDate.now());
        LOG.info("Lifecycle stage updated in memory: product=[" + lifecycle.getProductId()
            + "] stage=[" + lifecycle.getCurrentStage() + "]");
        return true;
    }

    /**
     * Returns the forecast horizon (in months) appropriate for the given lifecycle stage.
     *
     * <p>If {@link LifecycleContent#getForecastHorizonMonths()} is set and positive,
     * that value is used directly. Otherwise stage-based defaults apply:
     * <ul>
     *   <li>INTRODUCTION → 3</li>
     *   <li>GROWTH       → 6</li>
     *   <li>MATURITY     → 6</li>
     *   <li>DECLINE      → 3</li>
     *   <li>DISCONTINUED → 1</li>
     * </ul>
     */
    public int getForecastHorizon(LifecycleContent lifecycle) {
        if (lifecycle == null) {
            LOG.warning("Lifecycle is null, defaulting horizon to 6 months.");
            return 6;
        }
        if (lifecycle.getForecastHorizonMonths() != null && lifecycle.getForecastHorizonMonths() > 0) {
            return lifecycle.getForecastHorizonMonths();
        }
        switch (safeUpper(lifecycle.getCurrentStage())) {
            case "INTRODUCTION": return 3;
            case "GROWTH":       return 6;
            case "DECLINE":      return 3;
            case "DISCONTINUED": return 1;
            case "MATURITY":
            default:             return 6;
        }
    }

    /**
     * Recommends a forecasting strategy name based on lifecycle stage and pattern profile.
     *
     * <ul>
     *   <li>INTRODUCTION / GROWTH → PROPHET_LSTM (nonlinear growth curves)</li>
     *   <li>DECLINE / DISCONTINUED → ARIMA_HOLT_WINTERS (decaying trend)</li>
     *   <li>MATURITY → determined by pattern profile:
     *     <ul>
     *       <li>High volatility, PROMOTIONAL, ANOMALY, MULTI_SEASONAL → PROPHET_LSTM</li>
     *       <li>SEASONAL, TREND, STABLE → ARIMA_HOLT_WINTERS</li>
     *     </ul>
     *   </li>
     * </ul>
     */
    public String recommendForecastingStrategy(LifecycleContent lifecycle, PatternProfile pattern) {
        String stage = lifecycle == null ? "" : safeUpper(lifecycle.getCurrentStage());
        LOG.info("Recommending strategy for stage=[" + stage + "]");

        if ("INTRODUCTION".equals(stage) || "GROWTH".equals(stage)) return "PROPHET_LSTM";
        if ("DECLINE".equals(stage) || "DISCONTINUED".equals(stage)) return "ARIMA_HOLT_WINTERS";

        if (pattern != null) {
            String dp = safeUpper(pattern.getDominantPattern());
            if (pattern.isHighVolatility() ||
                List.of("PROMOTIONAL", "ANOMALY", "MULTI_SEASONAL").contains(dp)) {
                return "PROPHET_LSTM";
            }
            if (List.of("SEASONAL", "TREND", "STABLE").contains(dp)) {
                return "ARIMA_HOLT_WINTERS";
            }
        }
        return "ARIMA_HOLT_WINTERS";
    }

    /**
     * Returns {@code false} for DISCONTINUED and INACTIVE stages; {@code true} otherwise.
     */
    public boolean isActiveStage(LifecycleContent lifecycle) {
        if (lifecycle == null) return false;
        String stage = safeUpper(lifecycle.getCurrentStage());
        return !("DISCONTINUED".equals(stage) || "INACTIVE".equals(stage));
    }

    // ── DB helpers ────────────────────────────────────────────────────────────

    /**
     * Fetches the product-level lifecycle stage from {@code product_lifecycle_stages}.
     * Returns null if no matching row is found or if a SQL error occurs.
     */
    private LifecycleContent fetchProductLifecycle(String productId) {
        try (Connection conn = DriverManager.getConnection(jdbcUrl, dbUser, dbPassword);
             PreparedStatement ps = conn.prepareStatement(SQL_PRODUCT_LIFECYCLE)) {

            ps.setString(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;

                String stage = rs.getString("current_stage");
                Date   startDate = rs.getDate("stage_start_date");

                LifecycleContent lc = new LifecycleContent(
                    productId, stage,
                    startDate != null ? startDate.toLocalDate() : LocalDate.now().minusMonths(6));
                lc.setLastUpdated(LocalDate.now());
                enrichLifecycleDefaults(lc);
                return lc;
            }
        } catch (SQLException ex) {
            LOG.warning("SQL error fetching product lifecycle for product=["
                + productId + "]: " + ex.getMessage());
            return null;
        }
    }

    /**
     * Fetches a store-level lifecycle override from {@code store_lifecycle_overrides}.
     * Returns null if no override exists or if the table/row is absent.
     */
    private LifecycleContent fetchStoreOverride(String productId, String storeId) {
        try (Connection conn = DriverManager.getConnection(jdbcUrl, dbUser, dbPassword);
             PreparedStatement ps = conn.prepareStatement(SQL_STORE_OVERRIDE)) {

            ps.setString(1, productId);
            ps.setString(2, storeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;

                String stage     = rs.getString("current_stage");
                Date   startDate = rs.getDate("override_start_date");
                String notes     = rs.getString("notes");

                LifecycleContent lc = new LifecycleContent(
                    productId, stage,
                    startDate != null ? startDate.toLocalDate() : LocalDate.now());
                lc.setNotes("STORE_OVERRIDE[" + storeId + "]"
                    + (notes != null ? " " + notes : ""));
                lc.setLastUpdated(LocalDate.now());
                enrichLifecycleDefaults(lc);
                return lc;
            }
        } catch (SQLException ex) {
            // Table may not exist yet in all environments — non-fatal
            LOG.fine("Store lifecycle override query failed for product=[" + productId
                + "] store=[" + storeId + "]: " + ex.getMessage());
            return null;
        }
    }

    /**
     * Populates stage-specific default parameters on a LifecycleContent object.
     * Called after reading either the product-level or store-level record.
     */
    private void enrichLifecycleDefaults(LifecycleContent lc) {
        String stage = safeUpper(lc.getCurrentStage());
        switch (stage) {
            case "INTRODUCTION":
                setDefaults(lc, 0.20, 0.40, 3, true);
                break;
            case "GROWTH":
                setDefaults(lc, 0.15, 0.30, 6, true);
                break;
            case "MATURITY":
                setDefaults(lc, 0.02, 0.20, 6, true);
                break;
            case "DECLINE":
                setDefaults(lc, -0.05, 0.25, 3, false);
                break;
            case "DISCONTINUED":
                setDefaults(lc, -0.20, 0.10, 1, false);
                break;
            default:
                setDefaults(lc, 0.02, 0.20, 6, true);
        }
    }

    private void setDefaults(LifecycleContent lc,
                              double growthRate, double variability,
                              int horizonMonths, boolean seasonalAdj) {
        if (lc.getExpectedGrowthRate()        == null) lc.setExpectedGrowthRate(growthRate);
        if (lc.getExpectedDemandVariability()  == null) lc.setExpectedDemandVariability(variability);
        if (lc.getForecastHorizonMonths()      == null) lc.setForecastHorizonMonths(horizonMonths);
        if (lc.getUseSeasonalAdjustment()      == null) lc.setUseSeasonalAdjustment(seasonalAdj);
    }

    private LifecycleContent buildDefault(String productId) {
        LifecycleContent lc = new LifecycleContent(
            productId, "MATURITY", LocalDate.now().minusMonths(12));
        lc.setDescription("Heuristic default (MATURITY) — no lifecycle record found in database.");
        enrichLifecycleDefaults(lc);
        lc.setLastUpdated(LocalDate.now());
        lc.setNotes("DEFAULT");
        return lc;
    }

    private String safeUpper(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }
}