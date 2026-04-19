package com.forecast.services.engine;

import com.forecast.models.exceptions.IMLAlgorithmicExceptionSource;
import com.forecast.models.FeatureTimeSeries;
import com.forecast.models.ForecastResult;
import com.forecast.models.LifecycleContent;
import com.forecast.models.PatternProfile;
import com.forecast.models.exceptions.ErrorCode;
import com.forecast.models.exceptions.ForecastingException;
import com.forecast.services.engine.lifecycle.LifeCycleManager;
import com.forecast.services.engine.strategy.ArimaHoltWintersStrategy;
import com.forecast.services.engine.strategy.ForecastStrategy;
import com.forecast.services.engine.strategy.ProphetLSTMStrategy;
import com.forecast.services.output.ForecastOutputService;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class ForecastProcessor {

    private static final Logger logger = Logger.getLogger(ForecastProcessor.class.getName());
    private static final BigDecimal MAPE_THRESHOLD = new BigDecimal("25.00");
    private static final BigDecimal RMSE_FACTOR_THRESHOLD = new BigDecimal("0.35");
    private static final int DEGRADED_AVG_WEEKS = 4;
    private static final MathContext MC = new MathContext(10, RoundingMode.HALF_UP);

    private final LifeCycleManager lifeCycleManager;
    private final ForecastOutputService forecastOutputService;
    private final IMLAlgorithmicExceptionSource exceptionSource;
    private final Map<String, FeatureTimeSeries> recentFeaturesByKey = new HashMap<>();

    public ForecastProcessor(LifeCycleManager lifeCycleManager,
                             ForecastOutputService forecastOutputService,
                             IMLAlgorithmicExceptionSource exceptionSource) {
        this.lifeCycleManager = lifeCycleManager;
        this.forecastOutputService = forecastOutputService;
        this.exceptionSource = exceptionSource;
        logger.fine("ForecastProcessor initialized");
    }

    public ForecastResult processForecast(String productId,
                                          String storeId,
                                          FeatureTimeSeries features,
                                          LifecycleContent lifecycle) {
        logger.info("Processing forecast for product [" + productId + "] at store [" + storeId + "]");

        if (features == null || features.getDemandValues() == null || features.getDemandValues().isEmpty()) {
            throw new ForecastingException(ErrorCode.INSUFFICIENT_HISTORY,
                "No demand values available for product=" + productId + ", store=" + storeId);
        }

        recentFeaturesByKey.put(key(productId, storeId), features);

        LifecycleContent resolvedLifecycle = lifecycle != null
            ? lifecycle
            : lifeCycleManager.determineLifecycleStage(productId, storeId);

        if (!lifeCycleManager.isActiveStage(resolvedLifecycle)) {
            logger.warning("Lifecycle stage is inactive for product [" + productId + "]. Returning degraded forecast.");
            ForecastResult degraded = generateDegradedForecast(productId, storeId);
            forecastOutputService.publishDegradedForecast(degraded);
            return degraded;
        }

        PatternProfile patternProfile = buildPatternProfile(features);
        String strategyName = lifeCycleManager.recommendForecastingStrategy(resolvedLifecycle, patternProfile);
        ForecastStrategy strategy = selectStrategy(strategyName);
        int horizon = Math.min(lifeCycleManager.getForecastHorizon(resolvedLifecycle), strategy.getMaximumForecastHorizon());

        try {
            if (!strategy.validateInputData(features) || !strategy.train(features)) {
                throw new ForecastingException(ErrorCode.FORECAST_MODEL_FAILURE,
                    "Strategy rejected input: " + strategy.getStrategyName());
            }

            ForecastResult result = strategy.generateForecast(features, horizon);
            if (result == null) {
                throw new ForecastingException(ErrorCode.FORECAST_MODEL_FAILURE,
                    "Strategy returned null result: " + strategy.getStrategyName());
            }

            result.setLifecycleStage(resolvedLifecycle.getCurrentStage());
            result.setProductId(productId);
            result.setStoreId(storeId);
            if (result.getForecastGeneratedDate() == null) {
                result.setForecastGeneratedDate(LocalDate.now());
            }
            if (result.getForecastStartDate() == null) {
                result.setForecastStartDate(features.getEndDate() != null ? features.getEndDate().plusMonths(1) : LocalDate.now().plusMonths(1));
            }
            if (result.getForecastEndDate() == null) {
                result.setForecastEndDate(result.getForecastStartDate().plusMonths(Math.max(0, horizon - 1L)));
            }
            if (result.getStatus() == null) {
                result.setStatus("SUCCESS");
            }

            validateForecastResult(result);
            forecastOutputService.publishForecast(result);
            return result;
        } catch (ForecastingException ex) {
            logger.warning("Primary forecast pipeline failed for product [" + productId + "] store [" + storeId + "]: " + ex.getMessage());
            ForecastResult degraded = generateDegradedForecast(productId, storeId);
            degraded.setLifecycleStage(resolvedLifecycle.getCurrentStage());
            forecastOutputService.publishDegradedForecast(degraded);
            return degraded;
        }
    }

    public boolean validateForecastResult(ForecastResult result) {
        logger.fine("Validating forecast result");
        if (result == null || result.getForecastedDemand() == null || result.getForecastedDemand().isEmpty()) {
            return false;
        }

        BigDecimal mape = result.getMape() == null ? BigDecimal.ZERO : result.getMape();
        BigDecimal rmse = result.getRmse() == null ? BigDecimal.ZERO : result.getRmse();
        BigDecimal avgDemand = average(result.getForecastedDemand());
        BigDecimal rmseThreshold = avgDemand.multiply(RMSE_FACTOR_THRESHOLD, MC).setScale(2, RoundingMode.HALF_UP);

        boolean mapeExceeded = mape.compareTo(MAPE_THRESHOLD) > 0;
        boolean rmseExceeded = avgDemand.signum() > 0 && rmse.compareTo(rmseThreshold) > 0;

        if (mapeExceeded || rmseExceeded) {
            String detail = "product=" + result.getProductId()
                + ", store=" + result.getStoreId()
                + ", mape=" + mape
                + ", mapeThreshold=" + MAPE_THRESHOLD
                + ", rmse=" + rmse
                + ", rmseThreshold=" + rmseThreshold;

            exceptionSource.fireModelDegradation(
                453,
                "ForecastProcessor.validateForecastResult",
                result.getProductId() + "/" + result.getStoreId(),
                mapeExceeded ? MAPE_THRESHOLD.doubleValue() : rmseThreshold.doubleValue(),
                mapeExceeded ? mape.doubleValue() : rmse.doubleValue()
            );
            throw new ForecastingException(ErrorCode.MODEL_ACCURACY_BELOW_THRESHOLD, detail);
        }
        return true;
    }

    public ForecastResult generateDegradedForecast(String productId, String storeId) {
        logger.warning("Generating degraded forecast for product [" + productId + "] at store [" + storeId + "]");

        FeatureTimeSeries features = recentFeaturesByKey.get(key(productId, storeId));
        if (features == null || features.getDemandValues() == null || features.getDemandValues().isEmpty()) {
            throw new ForecastingException(ErrorCode.INSUFFICIENT_HISTORY,
                "Degraded forecast unavailable due to missing cached feature history for product=" + productId + ", store=" + storeId);
        }

        List<BigDecimal> demand = features.getDemandValues();
        List<BigDecimal> recentWindow = demand.subList(Math.max(0, demand.size() - DEGRADED_AVG_WEEKS), demand.size());
        BigDecimal fallback = average(recentWindow).setScale(2, RoundingMode.HALF_UP);
        BigDecimal variability = estimateStdDev(recentWindow).setScale(2, RoundingMode.HALF_UP);
        int horizon = Math.max(1, Math.min(3, demand.size()));

        List<BigDecimal> forecast = new ArrayList<>();
        List<BigDecimal> lower = new ArrayList<>();
        List<BigDecimal> upper = new ArrayList<>();
        for (int i = 0; i < horizon; i++) {
            forecast.add(fallback);
            lower.add(fallback.subtract(variability).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
            upper.add(fallback.add(variability).setScale(2, RoundingMode.HALF_UP));
        }

        ForecastResult result = new ForecastResult();
        result.setProductId(productId);
        result.setStoreId(storeId);
        result.setForecastGeneratedDate(LocalDate.now());
        result.setForecastStartDate(features.getEndDate() != null ? features.getEndDate().plusMonths(1) : LocalDate.now().plusMonths(1));
        result.setForecastEndDate(result.getForecastStartDate().plusMonths(horizon - 1L));
        result.setForecastedDemand(forecast);
        result.setConfidenceIntervalLower(lower);
        result.setConfidenceIntervalUpper(upper);
        result.setMape(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        result.setRmse(variability);
        result.setModelUsed("RULE_BASED_LAST_" + DEGRADED_AVG_WEEKS + "_WEEK_AVERAGE");
        result.setStatus("DEGRADED");
        return result;
    }

    private ForecastStrategy selectStrategy(String strategyName) {
        if (strategyName != null && strategyName.toUpperCase().contains("PROPHET")) {
            return new ProphetLSTMStrategy();
        }
        return new ArimaHoltWintersStrategy();
    }

    private PatternProfile buildPatternProfile(FeatureTimeSeries features) {
        PatternProfile profile = new PatternProfile(features.getProductId(), features.getStoreId(), "STABLE");
        List<BigDecimal> demand = features.getDemandValues();
        BigDecimal slope = demand.get(demand.size() - 1).subtract(demand.get(0), MC)
            .divide(BigDecimal.valueOf(Math.max(1, demand.size() - 1)), MC);
        double cv = average(demand).signum() == 0 ? 0.0 : estimateStdDev(demand).divide(average(demand), MC).doubleValue();

        if (Math.abs(slope.doubleValue()) > 1.0) {
            profile.setDominantPattern("TREND");
            profile.setTrendDirection(slope.signum() >= 0 ? "UP" : "DOWN");
            profile.setTrendStrength(Math.min(1.0, Math.abs(slope.doubleValue()) / 10.0));
        }
        if (demand.size() >= 12) {
            profile.setSeasonalityStrength(0.6);
            profile.setSeasonalPeriodDays(30);
            if (cv < 0.20) {
                profile.setDominantPattern("SEASONAL");
            }
        }
        profile.setNoiseLevel(cv);
        profile.setPatternStability(Math.max(0.0, 1.0 - cv));
        profile.setRequiresSpecialHandling(cv > 0.35);
        return profile;
    }

    private BigDecimal average(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) return BigDecimal.ZERO;
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), MC);
    }

    private BigDecimal estimateStdDev(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) return BigDecimal.ZERO;
        BigDecimal mean = average(values);
        double variance = values.stream()
            .mapToDouble(v -> Math.pow(v.subtract(mean, MC).doubleValue(), 2.0))
            .average().orElse(0.0);
        return BigDecimal.valueOf(Math.sqrt(variance));
    }

    private String key(String productId, String storeId) {
        return productId + "|" + storeId;
    }
}
