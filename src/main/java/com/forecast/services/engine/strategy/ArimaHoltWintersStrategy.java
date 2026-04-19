package com.forecast.services.engine.strategy;

import com.forecast.models.FeatureTimeSeries;
import com.forecast.models.ForecastResult;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.logging.Logger;

public class ArimaHoltWintersStrategy implements ForecastStrategy {

    private static final Logger logger = Logger.getLogger(ArimaHoltWintersStrategy.class.getName());
    private static final int MINIMUM_DATA_POINTS = 24;
    private static final int MAXIMUM_FORECAST_HORIZON = 12;
    private static final String STRATEGY_NAME = "ARIMA_HOLT_WINTERS";
    private static final MathContext MC = new MathContext(10, RoundingMode.HALF_UP);

    private FeatureTimeSeries trainingData;
    private boolean isTrained = false;
    private String selectedAlgorithm = "AUTO";

    @Override
    public ForecastResult generateForecast(FeatureTimeSeries features, int horizonMonths) {
        logger.info("Generating ARIMA/Holt-Winters forecast for product [" + features.getProductId() + "] with horizon [" + horizonMonths + "] months");

        if (!validateInputData(features)) {
            return null;
        }
        if (!isTrained && !train(features)) {
            return null;
        }

        int horizon = Math.max(1, Math.min(horizonMonths, MAXIMUM_FORECAST_HORIZON));
        List<BigDecimal> demand = features.getDemandValues();
        int seasonLength = detectSeasonLength(demand);
        selectedAlgorithm = seasonLength >= 4 ? "HOLT_WINTERS" : "ARIMA";

        BigDecimal level = average(lastWindow(demand, Math.min(8, demand.size())));
        BigDecimal slope = computeSlope(demand);
        List<BigDecimal> seasonalFactors = buildSeasonalFactors(demand, seasonLength);
        BigDecimal rmseValue = estimateRmse(demand, level);

        List<BigDecimal> forecast = new ArrayList<>();
        List<BigDecimal> lower = new ArrayList<>();
        List<BigDecimal> upper = new ArrayList<>();

        for (int step = 1; step <= horizon; step++) {
            BigDecimal trendValue = level.add(slope.multiply(BigDecimal.valueOf(step), MC), MC);
            BigDecimal seasonal = seasonalFactors.isEmpty()
                ? BigDecimal.ONE
                : seasonalFactors.get((step - 1) % seasonalFactors.size());
            BigDecimal point = trendValue.multiply(seasonal, MC).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            BigDecimal band = rmseValue.max(BigDecimal.ONE).multiply(BigDecimal.valueOf(1.28), MC).setScale(2, RoundingMode.HALF_UP);
            forecast.add(point);
            lower.add(point.subtract(band).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
            upper.add(point.add(band).setScale(2, RoundingMode.HALF_UP));
        }

        ForecastResult result = new ForecastResult();
        result.setProductId(features.getProductId());
        result.setStoreId(features.getStoreId());
        result.setForecastGeneratedDate(LocalDate.now());
        result.setForecastStartDate(features.getEndDate() != null ? features.getEndDate().plusMonths(1) : LocalDate.now().plusMonths(1));
        result.setForecastEndDate(result.getForecastStartDate().plusMonths(horizon - 1L));
        result.setForecastedDemand(forecast);
        result.setConfidenceIntervalLower(lower);
        result.setConfidenceIntervalUpper(upper);
        result.setMape(BigDecimal.valueOf(backtestMape(demand, seasonLength)).setScale(2, RoundingMode.HALF_UP));
        result.setRmse(rmseValue.setScale(2, RoundingMode.HALF_UP));
        result.setModelUsed(STRATEGY_NAME + ":" + selectedAlgorithm);
        result.setStatus("SUCCESS");
        return result;
    }

    @Override
    public boolean validateInputData(FeatureTimeSeries features) {
        return features != null
            && features.getDemandValues() != null
            && !features.getDemandValues().isEmpty()
            && features.getDemandValues().size() >= MINIMUM_DATA_POINTS;
    }

    @Override
    public boolean train(FeatureTimeSeries features) {
        if (!validateInputData(features)) {
            return false;
        }
        this.trainingData = features;
        this.isTrained = true;
        return true;
    }

    @Override public int getMinimumDataPoints() { return MINIMUM_DATA_POINTS; }
    @Override public int getMaximumForecastHorizon() { return MAXIMUM_FORECAST_HORIZON; }
    @Override public String getStrategyName() { return STRATEGY_NAME; }
    @Override public String getDescription() { return "Statistical strategy for trend and seasonal demand."; }
    @Override public double calculateMape(List<Double> actual, List<Double> predicted) { return sharedMape(actual, predicted); }
    @Override public double calculateRmse(List<Double> actual, List<Double> predicted) { return sharedRmse(actual, predicted); }

    public String getSelectedAlgorithm() { return selectedAlgorithm; }
    public boolean isTrained() { return isTrained; }

    private int detectSeasonLength(List<BigDecimal> demand) {
        if (demand.size() >= 52) return 12;
        if (demand.size() >= 24) return 4;
        return 0;
    }

    private List<BigDecimal> buildSeasonalFactors(List<BigDecimal> demand, int seasonLength) {
        if (seasonLength <= 0 || demand.size() < seasonLength * 2) {
            return List.of();
        }
        BigDecimal overallAvg = average(demand);
        List<BigDecimal> factors = new ArrayList<>();
        for (int i = 0; i < seasonLength; i++) {
            BigDecimal bucketSum = BigDecimal.ZERO;
            int count = 0;
            for (int j = i; j < demand.size(); j += seasonLength) {
                bucketSum = bucketSum.add(demand.get(j), MC);
                count++;
            }
            BigDecimal bucketAvg = bucketSum.divide(BigDecimal.valueOf(count), MC);
            factors.add(overallAvg.signum() == 0 ? BigDecimal.ONE : bucketAvg.divide(overallAvg, MC));
        }
        return factors;
    }

    private BigDecimal computeSlope(List<BigDecimal> demand) {
        List<BigDecimal> window = lastWindow(demand, Math.min(8, demand.size()));
        if (window.size() < 2) return BigDecimal.ZERO;
        return window.get(window.size() - 1).subtract(window.get(0), MC)
            .divide(BigDecimal.valueOf(window.size() - 1L), MC);
    }

    private double backtestMape(List<BigDecimal> demand, int seasonLength) {
        int backtest = Math.min(4, Math.max(1, demand.size() / 6));
        if (demand.size() <= backtest + 4) return 0.0;
        List<Double> actual = new ArrayList<>();
        List<Double> predicted = new ArrayList<>();
        for (int i = demand.size() - backtest; i < demand.size(); i++) {
            int start = Math.max(0, i - Math.max(4, seasonLength));
            BigDecimal pred = average(demand.subList(start, i));
            actual.add(demand.get(i).doubleValue());
            predicted.add(pred.doubleValue());
        }
        return sharedMape(actual, predicted);
    }

    private BigDecimal estimateRmse(List<BigDecimal> demand, BigDecimal center) {
        double mse = demand.stream()
            .mapToDouble(v -> Math.pow(v.subtract(center, MC).doubleValue(), 2.0))
            .average().orElse(0.0);
        return BigDecimal.valueOf(Math.sqrt(mse));
    }

    private List<BigDecimal> lastWindow(List<BigDecimal> demand, int size) {
        return demand.subList(Math.max(0, demand.size() - size), demand.size());
    }

    private BigDecimal average(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) return BigDecimal.ZERO;
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), MC);
    }

    private static double sharedMape(List<Double> actual, List<Double> predicted) {
        if (actual == null || predicted == null || actual.size() != predicted.size() || actual.isEmpty()) return Double.NaN;
        double total = 0.0;
        int count = 0;
        for (int i = 0; i < actual.size(); i++) {
            double a = actual.get(i);
            double p = predicted.get(i);
            if (Math.abs(a) < 1e-9) continue;
            total += Math.abs((a - p) / a) * 100.0;
            count++;
        }
        return count == 0 ? 0.0 : total / count;
    }

    private static double sharedRmse(List<Double> actual, List<Double> predicted) {
        if (actual == null || predicted == null || actual.size() != predicted.size() || actual.isEmpty()) return Double.NaN;
        double mse = 0.0;
        for (int i = 0; i < actual.size(); i++) {
            double err = actual.get(i) - predicted.get(i);
            mse += err * err;
        }
        return Math.sqrt(mse / actual.size());
    }
}
