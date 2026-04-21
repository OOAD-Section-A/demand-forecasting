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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * FeatureEngineeringService transforms cleaned {@link RawSalesData} records into
 * {@link FeatureTimeSeries} objects ready for ingestion by the forecasting strategies.
 *
 * Responsibilities:
 * - Build demand time series from raw sales records
 * - Compute lagged demand features
 * - Decompose trend and seasonal components via full STL (Seasonal-Trend decomposition
 *   using LOESS) — pure-Java, no external libraries required
 * - Apply holiday demand-lift adjustments when holiday dates are supplied
 * - Inject promotional lift multipliers into customFeatures when promo data is supplied
 * - Detect demand patterns and produce a {@link PatternProfile}
 * - Report via {@link IMLAlgorithmicExceptionSource} when steps are skipped
 *
 * STL reference:
 *   Cleveland et al. (1990) "STL: A Seasonal-Trend Decomposition Procedure Based on Loess"
 *   Journal of Official Statistics, 6(1), 3-33.
 *
 * @author  Demand Forecasting Team
 * @version 2.0
 */
public class FeatureEngineeringService {

    private static final Logger LOG = Logger.getLogger(FeatureEngineeringService.class.getName());

    // ── Constants ─────────────────────────────────────────────────────────────
    private static final int    LAG_WINDOW              = 4;
    private static final int    DEFAULT_SEASONAL_PERIOD = 12;
    private static final int    STL_INNER_LOOPS         = 2;
    private static final int    STL_OUTER_LOOPS         = 1;
    private static final double LOESS_TREND_BW          = 0.30;
    /** 15% uplift for periods coinciding with a holiday. */
    private static final double HOLIDAY_LIFT_FACTOR     = 1.15;

    private final IMLAlgorithmicExceptionSource exceptionSource;

    // ── Constructor ───────────────────────────────────────────────────────────

    public FeatureEngineeringService(IMLAlgorithmicExceptionSource exceptionSource) {
        if (exceptionSource == null) throw new IllegalArgumentException("exceptionSource must not be null");
        this.exceptionSource = exceptionSource;
        LOG.info("FeatureEngineeringService initialised (STL v2.0).");
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Full pipeline: filter → lag → STL → holiday adjust → promo inject.
     *
     * @param productId    target product
     * @param storeId      target store
     * @param rawRecords   all raw records (filtered internally)
     * @param promoData    promotions for this product-store; may be null
     * @param holidayDates known holiday dates within the window; may be null
     */
    public FeatureTimeSeries buildFeatures(
        String productId, String storeId,
        List<RawSalesData> rawRecords,
        List<PromoData> promoData,
        List<LocalDate> holidayDates
    ) {
        LOG.info("Building features for product=[" + productId + "] store=[" + storeId + "]");

        if (rawRecords == null || rawRecords.isEmpty()) {
            exceptionSource.fireModelDegradation(455, "FeatureEngineeringService", "input_records", 1, 0);
            throw new ForecastingException(ErrorCode.FEATURE_ENGINEERING_SKIPPED,
                "No raw records for product=" + productId + " store=" + storeId);
        }

        // 1. Filter + sort
        List<RawSalesData> filtered = rawRecords.stream()
            .filter(r -> productId.equals(r.getProductId()) && storeId.equals(r.getStoreId()))
            .sorted(Comparator.comparing(RawSalesData::getSaleDate))
            .collect(Collectors.toList());

        List<LocalDate>  dates        = filtered.stream().map(RawSalesData::getSaleDate).collect(Collectors.toList());
        List<BigDecimal> demandValues = filtered.stream().map(r -> BigDecimal.valueOf(r.getQuantitySold())).collect(Collectors.toList());

        FeatureTimeSeries fts = new FeatureTimeSeries(productId, storeId, dates, demandValues);
        fts.setFeatureEngineeringVersion("2.0");

        // 2. Lagged demand
        fts.setLaggedDemand(computeLaggedDemand(demandValues, LAG_WINDOW));

        // 3. STL decomposition
        try {
            int period = detectSeasonalPeriod(demandValues);
            StlResult stl = stlDecompose(toDoubleArr(demandValues), period, STL_INNER_LOOPS, STL_OUTER_LOOPS);
            fts.setTrendComponent(toBdList(stl.trend));
            fts.setSeasonalComponent(toBdList(stl.seasonal));
            LOG.info("STL decomposition OK: period=" + period + " n=" + demandValues.size());
        } catch (Exception ex) {
            LOG.warning("STL failed, using CMA fallback: " + ex.getMessage());
            exceptionSource.fireModelDegradation(455, "FeatureEngineeringService", "decomposition_step", 1, 0);
            fts.setTrendComponent(cmaFallback(demandValues));
            fts.setSeasonalComponent(seasonalIndexFallback(demandValues, fts.getTrendComponent()));
        }

        // 4. Holiday adjustment
        if (holidayDates != null && !holidayDates.isEmpty()) {
            fts.setDemandValues(applyHolidayAdjustment(fts.getDemandValues(), dates, holidayDates));
            LOG.info("Holiday adjustment applied: " + holidayDates.size() + " dates for product=[" + productId + "]");
        } else {
            LOG.warning("No holiday dates for product=[" + productId + "]. Adjustment skipped.");
            exceptionSource.fireMissingInputData(459, "FeatureEngineeringService", "holiday_data",
                fts.getStartDate() + "/" + fts.getEndDate());
        }

        // 5. Promo features
        if (promoData == null || promoData.isEmpty()) {
            LOG.warning("No promo data for product=[" + productId + "]. Promo features absent.");
            exceptionSource.fireMissingInputData(458, "FeatureEngineeringService", "PromoCalendar",
                fts.getStartDate() + "/" + fts.getEndDate());
        } else {
            injectPromoFeatures(fts, promoData, dates);
        }

        LOG.info("Feature build complete: " + fts.size() + " observations.");
        return fts;
    }

    /** Backwards-compatible overload without holidayDates. */
    public FeatureTimeSeries buildFeatures(
        String productId, String storeId,
        List<RawSalesData> rawRecords,
        List<PromoData> promoData
    ) {
        return buildFeatures(productId, storeId, rawRecords, promoData, null);
    }

    /**
     * Pattern detection using STL-derived components.
     * Classifies series as SEASONAL, TREND, VOLATILE, or STABLE based on
     * seasonal strength (from STL), trend slope magnitude, and CV.
     */
    public PatternProfile detectPatterns(FeatureTimeSeries fts) {
        LOG.info("Detecting patterns for product=[" + fts.getProductId() + "]");

        PatternProfile profile = new PatternProfile(fts.getProductId(), fts.getStoreId(), "UNKNOWN");

        if (fts.getDemandValues() == null || fts.getDemandValues().isEmpty()) {
            profile.setDominantPattern("INSUFFICIENT_DATA");
            profile.setRequiresSpecialHandling(true);
            return profile;
        }

        double cv = computeCV(fts.getDemandValues());
        profile.setNoiseLevel(cv);
        profile.setPatternStability(1.0 - Math.min(cv, 1.0));

        double seasonalStrength = 0.0;
        if (fts.getSeasonalComponent() != null && !fts.getSeasonalComponent().isEmpty()) {
            seasonalStrength = seasonalStrength(fts.getSeasonalComponent(), fts.getDemandValues());
        }
        profile.setSeasonalityStrength(seasonalStrength);

        String trendDir = "FLAT";
        double trendStrength = 0.0;
        if (fts.getTrendComponent() != null && fts.getTrendComponent().size() >= 2) {
            double slope = trendSlope(fts.getTrendComponent());
            double meanD = mean(fts.getDemandValues());
            trendStrength = meanD > 0 ? Math.min(1.0, Math.abs(slope) / meanD) : 0.0;
            if      (slope > meanD * 0.01)  trendDir = "UP";
            else if (slope < -meanD * 0.01) trendDir = "DOWN";
        }
        profile.setTrendDirection(trendDir);
        profile.setTrendStrength(trendStrength);

        if (cv >= 0.5) {
            profile.setDominantPattern("VOLATILE");
            profile.setForecastingRecommendation("PROPHET_LSTM");
            profile.setRequiresSpecialHandling(true);
        } else if (seasonalStrength >= 0.4) {
            profile.setDominantPattern("SEASONAL");
            profile.setForecastingRecommendation("ARIMA_HOLT_WINTERS");
            profile.setSeasonalPeriodDays(30);
        } else if (trendStrength >= 0.1 && !"FLAT".equals(trendDir)) {
            profile.setDominantPattern("TREND");
            profile.setForecastingRecommendation("ARIMA_HOLT_WINTERS");
        } else {
            profile.setDominantPattern("STABLE");
            profile.setForecastingRecommendation("ARIMA_HOLT_WINTERS");
        }

        LOG.info("Pattern: " + profile.getDominantPattern()
            + " seasonalStr=" + String.format("%.3f", seasonalStrength)
            + " trendDir=" + trendDir + " cv=" + String.format("%.3f", cv));
        return profile;
    }

    // ── STL ──────────────────────────────────────────────────────────────────

    private StlResult stlDecompose(double[] y, int period, int nInner, int nOuter) {
        int n = y.length;
        double[] trend    = new double[n];
        double[] seasonal = new double[n];
        double[] robW     = new double[n];
        Arrays.fill(robW, 1.0);

        for (int outer = 0; outer <= nOuter; outer++) {
            for (int inner = 0; inner < nInner; inner++) {
                // Step 1 — detrend
                double[] detrended = new double[n];
                for (int i = 0; i < n; i++) detrended[i] = y[i] - trend[i];

                // Step 2 — cycle-subseries smoothing
                double[] rawSeasonal = new double[n];
                for (int p = 0; p < period; p++) {
                    List<Integer> idx = new ArrayList<>();
                    for (int i = p; i < n; i += period) idx.add(i);

                    double[] subY = new double[idx.size()];
                    double[] subX = new double[idx.size()];
                    double[] subW = new double[idx.size()];
                    for (int k = 0; k < idx.size(); k++) {
                        subY[k] = detrended[idx.get(k)];
                        subX[k] = k;
                        subW[k] = robW[idx.get(k)];
                    }
                    double bw = Math.max(1.0, 3.0 / subY.length);
                    double[] sm = loess(subX, subY, subW, bw);
                    for (int k = 0; k < idx.size(); k++) rawSeasonal[idx.get(k)] = sm[k];
                }

                // Step 3 — low-pass filter
                double[] lp = ma(ma(ma(rawSeasonal, period), period), 3);
                lp = padTo(lp, n);
                for (int i = 0; i < n; i++) seasonal[i] = rawSeasonal[i] - lp[i];

                // Step 4 — deseasonalise + trend LOESS
                double[] deseas = new double[n];
                double[] xIdx   = new double[n];
                for (int i = 0; i < n; i++) { deseas[i] = y[i] - seasonal[i]; xIdx[i] = i; }
                trend = loess(xIdx, deseas, robW, LOESS_TREND_BW);
            }

            if (outer < nOuter) {
                double[] res = new double[n];
                for (int i = 0; i < n; i++) res[i] = y[i] - trend[i] - seasonal[i];
                robW = bisquareWeights(res);
            }
        }

        double[] residual = new double[n];
        for (int i = 0; i < n; i++) residual[i] = y[i] - trend[i] - seasonal[i];
        return new StlResult(trend, seasonal, residual);
    }

    private double[] loess(double[] x, double[] y, double[] weights, double bandwidth) {
        int n = x.length;
        int k = Math.max(2, (int) Math.ceil(bandwidth * n));
        double[] smoothed = new double[n];

        for (int i = 0; i < n; i++) {
            double[] dist = new double[n];
            for (int j = 0; j < n; j++) dist[j] = Math.abs(x[j] - x[i]);
            double maxDist = Math.max(1e-10, kthSmallest(dist, k - 1));

            double sumW=0, sumWX=0, sumWY=0, sumWXX=0, sumWXY=0;
            for (int j = 0; j < n; j++) {
                double u = dist[j] / maxDist;
                if (u >= 1.0) continue;
                double tc = Math.pow(1 - u*u*u, 3);
                double w  = tc * weights[j];
                sumW   += w;
                sumWX  += w * x[j];
                sumWY  += w * y[j];
                sumWXX += w * x[j] * x[j];
                sumWXY += w * x[j] * y[j];
            }
            double denom = sumW * sumWXX - sumWX * sumWX;
            if (Math.abs(denom) < 1e-12 || sumW < 1e-12) {
                smoothed[i] = sumW > 0 ? sumWY / sumW : y[i];
            } else {
                double b = (sumW * sumWXY - sumWX * sumWY) / denom;
                double a = (sumWY - b * sumWX) / sumW;
                smoothed[i] = a + b * x[i];
            }
        }
        return smoothed;
    }

    private double[] bisquareWeights(double[] residuals) {
        int n = residuals.length;
        double[] absR = new double[n];
        for (int i = 0; i < n; i++) absR[i] = Math.abs(residuals[i]);
        double h = 6.0 * median(absR);
        if (h < 1e-10) { double[] ones = new double[n]; Arrays.fill(ones, 1.0); return ones; }
        double[] w = new double[n];
        for (int i = 0; i < n; i++) {
            double u = absR[i] / h;
            w[i] = u < 1.0 ? Math.pow(1 - u*u, 2) : 0.0;
        }
        return w;
    }

    private double[] ma(double[] x, int window) {
        if (window <= 1 || x.length < window) return x.clone();
        int outLen = x.length - window + 1;
        double[] out = new double[outLen];
        double s = 0;
        for (int i = 0; i < window; i++) s += x[i];
        out[0] = s / window;
        for (int i = 1; i < outLen; i++) { s += x[i+window-1] - x[i-1]; out[i] = s / window; }
        return out;
    }

    private double[] padTo(double[] arr, int len) {
        if (arr.length == len) return arr;
        double[] out = new double[len];
        int offset = (len - arr.length) / 2;
        for (int i = 0; i < arr.length && (i+offset) < len; i++) out[i+offset] = arr[i];
        for (int i = 0; i < offset; i++) out[i] = arr.length > 0 ? arr[0] : 0.0;
        for (int i = offset + arr.length; i < len; i++) out[i] = arr.length > 0 ? arr[arr.length-1] : 0.0;
        return out;
    }

    // ── Seasonal period detection ─────────────────────────────────────────────

    private int detectSeasonalPeriod(List<BigDecimal> demand) {
        int n = demand.size();
        if (n < DEFAULT_SEASONAL_PERIOD * 2) return DEFAULT_SEASONAL_PERIOD;
        double[] y = toDoubleArr(demand);
        int[] candidates = {4, 6, 12, 52};
        double bestAcf = -1;
        int bestPeriod = DEFAULT_SEASONAL_PERIOD;
        for (int lag : candidates) {
            if (lag >= n / 2) continue;
            double acf = acf(y, lag);
            if (acf > bestAcf) { bestAcf = acf; bestPeriod = lag; }
        }
        LOG.fine("Detected seasonal period=" + bestPeriod + " (acf=" + String.format("%.3f", bestAcf) + ")");
        return bestPeriod;
    }

    private double acf(double[] y, int lag) {
        int n = y.length - lag;
        if (n <= 0) return 0.0;
        double m = 0; for (double v : y) m += v; m /= y.length;
        double num=0, d1=0, d2=0;
        for (int i = 0; i < n; i++) {
            double a = y[i]-m, b = y[i+lag]-m;
            num += a*b; d1 += a*a; d2 += b*b;
        }
        double den = Math.sqrt(d1 * d2);
        return den < 1e-10 ? 0.0 : num / den;
    }

    // ── Holiday adjustment ────────────────────────────────────────────────────

    private List<BigDecimal> applyHolidayAdjustment(
        List<BigDecimal> demand, List<LocalDate> dates, List<LocalDate> holidayDates
    ) {
        List<BigDecimal> adjusted = new ArrayList<>(demand);
        MathContext mc = MathContext.DECIMAL64;
        for (int i = 0; i < dates.size(); i++) {
            double lift = 1.0;
            for (LocalDate hd : holidayDates) {
                long days = Math.abs(dates.get(i).toEpochDay() - hd.toEpochDay());
                if (days == 0) {
                    lift = Math.max(lift, HOLIDAY_LIFT_FACTOR);
                } else if (days <= 30) {
                    double partial = 1.0 + (HOLIDAY_LIFT_FACTOR - 1.0) * (1.0 - days/30.0) * 0.5;
                    lift = Math.max(lift, partial);
                }
            }
            if (lift > 1.0) {
                adjusted.set(i, demand.get(i)
                    .multiply(BigDecimal.valueOf(lift), mc)
                    .setScale(2, RoundingMode.HALF_UP));
            }
        }
        return adjusted;
    }

    // ── Promotional lift injection ────────────────────────────────────────────

    private void injectPromoFeatures(
        FeatureTimeSeries fts, List<PromoData> promoData, List<LocalDate> dates
    ) {
        List<BigDecimal> liftList = new ArrayList<>(Collections.nCopies(dates.size(), BigDecimal.ONE));
        for (int i = 0; i < dates.size(); i++) {
            LocalDate d = dates.get(i);
            double maxLift = 0.0;
            for (PromoData p : promoData) {
                if (p.getPromotionStartDate() == null || p.getPromotionEndDate() == null) continue;
                if (!d.isBefore(p.getPromotionStartDate()) && !d.isAfter(p.getPromotionEndDate())) {
                    Double lift = p.getExpectedDemandLift();
                    if (lift != null && lift > maxLift) maxLift = lift;
                }
            }
            if (maxLift > 0) {
                liftList.set(i, BigDecimal.valueOf(1.0 + maxLift / 100.0).setScale(4, RoundingMode.HALF_UP));
            }
        }
        Map<String, List<BigDecimal>> custom = fts.getCustomFeatures();
        if (custom == null) custom = new HashMap<>();
        custom.put("promo_lift", liftList);
        fts.setCustomFeatures(custom);
        long active = liftList.stream().filter(v -> v.compareTo(BigDecimal.ONE) > 0).count();
        LOG.info("Promo features injected: " + active + " active periods for product=[" + fts.getProductId() + "]");
    }

    // ── Fallbacks ─────────────────────────────────────────────────────────────

    private List<BigDecimal> cmaFallback(List<BigDecimal> demand) {
        int window = Math.max(3, demand.size() / 10);
        List<BigDecimal> trend = new ArrayList<>(demand.size());
        for (int i = 0; i < demand.size(); i++) {
            int s = Math.max(0, i - window/2), e = Math.min(demand.size(), i + window/2 + 1);
            BigDecimal sum = BigDecimal.ZERO;
            for (int j = s; j < e; j++) sum = sum.add(demand.get(j));
            trend.add(sum.divide(BigDecimal.valueOf(e-s), MathContext.DECIMAL64));
        }
        return trend;
    }

    private List<BigDecimal> seasonalIndexFallback(List<BigDecimal> demand, List<BigDecimal> trend) {
        List<BigDecimal> seasonal = new ArrayList<>(demand.size());
        for (int i = 0; i < demand.size(); i++) {
            BigDecimal t = trend.get(i);
            seasonal.add(t.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ONE
                : demand.get(i).divide(t, MathContext.DECIMAL64));
        }
        return seasonal;
    }

    // ── Lagged demand ─────────────────────────────────────────────────────────

    private List<BigDecimal> computeLaggedDemand(List<BigDecimal> demand, int lagWindow) {
        List<BigDecimal> lagged = new ArrayList<>(demand.size());
        for (int i = 0; i < demand.size(); i++) {
            int start = Math.max(0, i - lagWindow);
            BigDecimal sum = BigDecimal.ZERO;
            for (int j = start; j < i; j++) sum = sum.add(demand.get(j));
            int count = i - start;
            lagged.add(count == 0 ? BigDecimal.ZERO : sum.divide(BigDecimal.valueOf(count), MathContext.DECIMAL64));
        }
        return lagged;
    }

    // ── Statistics helpers ────────────────────────────────────────────────────

    private double computeCV(List<BigDecimal> values) {
        if (values.isEmpty()) return 0.0;
        double m = mean(values);
        if (m == 0) return 1.0;
        double var = values.stream().mapToDouble(v -> Math.pow(v.doubleValue() - m, 2)).average().orElse(0.0);
        return Math.sqrt(var) / m;
    }

    private double mean(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) return 0.0;
        return values.stream().mapToDouble(BigDecimal::doubleValue).sum() / values.size();
    }

    private double variance(List<BigDecimal> values) {
        if (values == null || values.size() < 2) return 0.0;
        double m = mean(values);
        return values.stream().mapToDouble(v -> Math.pow(v.doubleValue() - m, 2)).average().orElse(0.0);
    }

    private double seasonalStrength(List<BigDecimal> seasonal, List<BigDecimal> demand) {
        double vs = variance(seasonal), vd = variance(demand);
        return vd < 1e-10 ? 0.0 : Math.min(1.0, vs / vd);
    }

    private double trendSlope(List<BigDecimal> trend) {
        int n = trend.size();
        return n < 2 ? 0.0 : (trend.get(n-1).doubleValue() - trend.get(0).doubleValue()) / (n - 1.0);
    }

    private double median(double[] arr) {
        double[] s = arr.clone(); Arrays.sort(s);
        int n = s.length;
        return n % 2 == 0 ? (s[n/2-1] + s[n/2]) / 2.0 : s[n/2];
    }

    private double kthSmallest(double[] arr, int k) {
        double[] c = arr.clone(); Arrays.sort(c);
        return c[Math.min(k, c.length-1)];
    }

    // ── Type helpers ──────────────────────────────────────────────────────────

    private double[] toDoubleArr(List<BigDecimal> list) {
        double[] a = new double[list.size()];
        for (int i = 0; i < list.size(); i++) a[i] = list.get(i).doubleValue();
        return a;
    }

    private List<BigDecimal> toBdList(double[] arr) {
        List<BigDecimal> list = new ArrayList<>(arr.length);
        for (double v : arr) list.add(BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP));
        return list;
    }

    // ── STL result holder ─────────────────────────────────────────────────────

    private static final class StlResult {
        final double[] trend, seasonal, residual;
        StlResult(double[] t, double[] s, double[] r) { trend=t; seasonal=s; residual=r; }
    }
}
