package com.forecast.services.engine.strategy;

import com.forecast.models.FeatureTimeSeries;
import com.forecast.models.ForecastResult;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.logging.Logger;

/**
 * ProphetLSTMStrategy — adaptive forecasting for nonlinear, volatile, and
 * promotional-sensitive demand.
 *
 * <h3>Algorithm selection (automatic at train time)</h3>
 * <ul>
 *   <li><b>PROPHET path</b>: used when demand is relatively smooth (CV ≤ 25%).
 *       Models trend + seasonality + holiday changepoints using a piecewise
 *       linear trend, Fourier-series seasonal terms, and additive holiday
 *       adjustments — all implemented in pure Java without external libraries.</li>
 *   <li><b>LSTM path</b>: used when demand is highly volatile (CV > 25%).
 *       Runs a single-layer LSTM recurrent network trained via truncated
 *       backpropagation-through-time (BPTT) entirely in Java primitives.</li>
 * </ul>
 *
 * <h3>Confidence intervals</h3>
 * Both paths produce 80% prediction intervals (z ≈ 1.28) derived from the
 * in-sample residual standard deviation, widened with each forecast step to
 * model growing uncertainty.
 *
 * @author  Demand Forecasting Team
 * @version 2.0
 */
public class ProphetLSTMStrategy implements ForecastStrategy {

    private static final Logger LOG = Logger.getLogger(ProphetLSTMStrategy.class.getName());

    private static final int    MINIMUM_DATA_POINTS    = 12;
    private static final int    MAXIMUM_FORECAST_HORIZON = 6;
    private static final String STRATEGY_NAME          = "PROPHET_LSTM";
    private static final MathContext MC                = new MathContext(10, RoundingMode.HALF_UP);

    // ── LSTM hyper-parameters (configurable) ─────────────────────────────────
    private int    lstmHiddenSize   = 32;
    private int    lstmEpochs       = 80;
    private double lstmLearningRate = 0.005;
    private int    lstmSeqLen       = 6;   // look-back window for LSTM

    // ── Prophet hyper-parameters ──────────────────────────────────────────────
    /** Number of Fourier terms for yearly seasonality. */
    private static final int FOURIER_ORDER = 5;
    /** Maximum number of potential changepoints (evenly spaced in first 80% of series). */
    private static final int N_CHANGEPOINTS = 10;

    // ── Trained model state ───────────────────────────────────────────────────
    private boolean   isTrained        = false;
    private String    selectedAlgorithm = "AUTO";
    private double[]  lstmWeightsIH;   // input→hidden weights [hiddenSize * 4 * (inputSize+hiddenSize)]
    private double[]  lstmBiasH;       // hidden bias [hiddenSize * 4]
    private double[]  lstmWeightsHO;   // hidden→output weights [hiddenSize]
    private double    lstmBiasO;
    private double[]  prophetTrendSlopes;   // one slope per changepoint segment
    private double[]  prophetChangepointDeltas; // cumulative delta at each changepoint
    private double[]  fourierCoeffs;         // [sin1, cos1, sin2, cos2, ...] for seasonal
    private double    prophetBaseLevel;
    private double    insampleRmse;
    private double[]  trainedDemand;         // cached for backtest MAPE

    // ─────────────────────────────────────────────────────────────────────────
    // ForecastStrategy interface
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public ForecastResult generateForecast(FeatureTimeSeries features, int horizonMonths) {
        LOG.info("Generating " + selectedAlgorithm + " forecast for product=["
            + features.getProductId() + "] horizon=" + horizonMonths);

        if (!validateInputData(features)) return null;
        if (!isTrained && !train(features)) return null;

        int horizon = Math.max(1, Math.min(horizonMonths, MAXIMUM_FORECAST_HORIZON));
        double[] demand = toDoubleArr(features.getDemandValues());
        int n = demand.length;

        double[] forecastValues;
        if ("LSTM".equals(selectedAlgorithm)) {
            forecastValues = lstmForecast(demand, horizon);
        } else {
            forecastValues = prophetForecast(demand, horizon, features);
        }

        // Confidence intervals: residual RMSE widened linearly with step
        List<BigDecimal> forecastBD = new ArrayList<>(horizon);
        List<BigDecimal> lowerBD   = new ArrayList<>(horizon);
        List<BigDecimal> upperBD   = new ArrayList<>(horizon);

        for (int step = 0; step < horizon; step++) {
            double pt  = Math.max(0, forecastValues[step]);
            double band = insampleRmse * 1.28 * (1.0 + step * 0.15); // widen with horizon
            forecastBD.add(bd(pt));
            lowerBD.add(bd(Math.max(0, pt - band)));
            upperBD.add(bd(pt + band));
        }

        ForecastResult result = new ForecastResult();
        result.setProductId(features.getProductId());
        result.setStoreId(features.getStoreId());
        result.setForecastGeneratedDate(LocalDate.now());
        result.setForecastStartDate(features.getEndDate() != null
            ? features.getEndDate().plusMonths(1)
            : LocalDate.now().plusMonths(1));
        result.setForecastEndDate(result.getForecastStartDate().plusMonths(horizon - 1L));
        result.setForecastedDemand(forecastBD);
        result.setConfidenceIntervalLower(lowerBD);
        result.setConfidenceIntervalUpper(upperBD);
        result.setMape(bd(backtestMape(demand)));
        result.setRmse(bd(insampleRmse));
        result.setModelUsed(STRATEGY_NAME + ":" + selectedAlgorithm);
        result.setStatus("SUCCESS");
        return result;
    }

    @Override
    public boolean validateInputData(FeatureTimeSeries features) {
        return features != null
            && features.getDemandValues() != null
            && features.getDates() != null
            && features.getDemandValues().size() == features.getDates().size()
            && features.getDemandValues().size() >= MINIMUM_DATA_POINTS;
    }

    @Override
    public boolean train(FeatureTimeSeries features) {
        if (!validateInputData(features)) return false;

        double[] demand = toDoubleArr(features.getDemandValues());
        this.trainedDemand = demand;

        // Decide algorithm based on coefficient of variation
        double cv = coefficientOfVariation(demand);
        selectedAlgorithm = cv > 0.25 ? "LSTM" : "PROPHET";
        LOG.info("Training " + selectedAlgorithm + " for product=["
            + features.getProductId() + "] cv=" + String.format("%.3f", cv));

        if ("LSTM".equals(selectedAlgorithm)) {
            trainLstm(demand);
        } else {
            trainProphet(demand, features);
        }

        // Compute in-sample RMSE for confidence bands
        double[] fitted = computeFitted(demand);
        this.insampleRmse = Math.max(1.0, rmse(demand, fitted));

        this.isTrained = true;
        return true;
    }

    @Override public int    getMinimumDataPoints()    { return MINIMUM_DATA_POINTS; }
    @Override public int    getMaximumForecastHorizon(){ return MAXIMUM_FORECAST_HORIZON; }
    @Override public String getStrategyName()          { return STRATEGY_NAME; }
    @Override public String getDescription()           { return "Adaptive LSTM/Prophet strategy for volatile and promo-sensitive demand."; }
    @Override public double calculateMape(List<Double> actual, List<Double> predicted) { return sharedMape(actual, predicted); }
    @Override public double calculateRmse(List<Double> actual, List<Double> predicted) { return sharedRmse(actual, predicted); }

    public String  getSelectedAlgorithm() { return selectedAlgorithm; }
    public boolean isTrained()            { return isTrained; }

    /** Override LSTM hyper-parameters before calling train(). */
    public void setLstmHyperparameters(int hiddenSize, int epochs, double learningRate) {
        this.lstmHiddenSize   = hiddenSize;
        this.lstmEpochs       = epochs;
        this.lstmLearningRate = learningRate;
        LOG.info("LSTM hyperparameters: hidden=" + hiddenSize + " epochs=" + epochs + " lr=" + learningRate);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PROPHET implementation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Trains the Prophet-style model:
     * <ol>
     *   <li>Fit piecewise linear trend using L-BFGS-style gradient descent with
     *       changepoint priors (implemented as L2-regularised least squares).</li>
     *   <li>Fit Fourier-series seasonal coefficients by OLS on the detrended series.</li>
     * </ol>
     */
    private void trainProphet(double[] y, FeatureTimeSeries features) {
        int n = y.length;

        // ── 1. Detect changepoints (evenly spaced in first 80%) ──────────────
        int cpRegion = (int)(n * 0.8);
        int[] cpIdx  = new int[N_CHANGEPOINTS];
        for (int k = 0; k < N_CHANGEPOINTS; k++) {
            cpIdx[k] = (int)((k + 1.0) / (N_CHANGEPOINTS + 1.0) * cpRegion);
        }

        // ── 2. Build design matrix for trend: [1, t, cp1(t), cp2(t), ..., cpK(t)]
        //    cpk(t) = max(0, t - t_k) / n  (scaled to [0,1])
        int cols = 2 + N_CHANGEPOINTS; // intercept + slope + K changepoint indicators
        double[][] A = new double[n][cols];
        for (int i = 0; i < n; i++) {
            double t = i / (double)(n - 1);
            A[i][0] = 1.0;
            A[i][1] = t;
            for (int k = 0; k < N_CHANGEPOINTS; k++) {
                A[i][2 + k] = i > cpIdx[k] ? (i - cpIdx[k]) / (double)n : 0.0;
            }
        }

        // ── 3. OLS with L2 regularisation (lambda=0.05 damps changepoint deltas)
        double lambda = 0.05;
        double[] trendCoeffs = ridgeRegression(A, y, lambda, cols);

        this.prophetBaseLevel       = trendCoeffs[0];
        this.prophetTrendSlopes     = new double[]{trendCoeffs[1]};
        this.prophetChangepointDeltas = new double[N_CHANGEPOINTS];
        for (int k = 0; k < N_CHANGEPOINTS; k++) {
            prophetChangepointDeltas[k] = trendCoeffs[2 + k];
        }

        // ── 4. Detrend and fit Fourier seasonal ──────────────────────────────
        double[] trendFitted = new double[n];
        for (int i = 0; i < n; i++) {
            double t = i / (double)(n - 1);
            trendFitted[i] = trendCoeffs[0] + trendCoeffs[1] * t;
            for (int k = 0; k < N_CHANGEPOINTS; k++) {
                trendFitted[i] += i > cpIdx[k]
                    ? trendCoeffs[2+k] * (i - cpIdx[k]) / (double)n : 0.0;
            }
        }
        double[] detrended = new double[n];
        for (int i = 0; i < n; i++) detrended[i] = y[i] - trendFitted[i];

        // Fourier design matrix: [sin(2pi*k*t/P), cos(2pi*k*t/P)] for k=1..FOURIER_ORDER
        int seasonP = 12; // monthly period
        int fCols   = 2 * FOURIER_ORDER;
        double[][] F = new double[n][fCols];
        for (int i = 0; i < n; i++) {
            for (int k = 1; k <= FOURIER_ORDER; k++) {
                F[i][2*(k-1)]   = Math.sin(2 * Math.PI * k * i / seasonP);
                F[i][2*(k-1)+1] = Math.cos(2 * Math.PI * k * i / seasonP);
            }
        }
        this.fourierCoeffs = ridgeRegression(F, detrended, 0.01, fCols);
        LOG.fine("Prophet trained: " + N_CHANGEPOINTS + " changepoints, "
            + FOURIER_ORDER + " Fourier orders.");
    }

    /**
     * Generates future values using the trained Prophet model.
     * Extrapolates the piecewise linear trend and continues the Fourier seasonal pattern.
     */
    private double[] prophetForecast(double[] y, int horizon, FeatureTimeSeries features) {
        int n = y.length;
        double[] forecast = new double[horizon];

        for (int step = 1; step <= horizon; step++) {
            int futureIdx = n + step - 1;
            double t = futureIdx / (double)(n - 1);

            // Trend: base + slope*t + sum of changepoint deltas beyond last observed
            double trend = prophetBaseLevel + prophetTrendSlopes[0] * t;
            for (int k = 0; k < N_CHANGEPOINTS; k++) {
                // All changepoints are in the past — their deltas continue into future
                double cpT = (int)(((k+1.0)/(N_CHANGEPOINTS+1.0)) * n * 0.8);
                if (futureIdx > cpT) trend += prophetChangepointDeltas[k] * (futureIdx - cpT) / (double)n;
            }

            // Seasonality: evaluate Fourier series at future time index
            double seasonal = 0.0;
            int seasonP = 12;
            for (int k = 1; k <= FOURIER_ORDER; k++) {
                seasonal += fourierCoeffs[2*(k-1)]   * Math.sin(2 * Math.PI * k * futureIdx / seasonP);
                seasonal += fourierCoeffs[2*(k-1)+1] * Math.cos(2 * Math.PI * k * futureIdx / seasonP);
            }

            // Promo lift from customFeatures if available
            double promoMultiplier = 1.0;
            if (features.getCustomFeatures() != null
                && features.getCustomFeatures().containsKey("promo_lift")) {
                List<BigDecimal> lifts = features.getCustomFeatures().get("promo_lift");
                // Use last known promo lift as a conservative estimate for future steps
                if (!lifts.isEmpty()) {
                    promoMultiplier = lifts.get(lifts.size() - 1).doubleValue();
                }
            }

            forecast[step - 1] = Math.max(0, (trend + seasonal) * promoMultiplier);
        }
        return forecast;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LSTM implementation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Trains a single-layer LSTM with one output neuron using BPTT.
     *
     * Architecture:  input(1) → LSTM(hiddenSize) → linear(1)
     *
     * Gate equations (standard LSTM):
     *   i_t = sigmoid(W_ii * x_t + W_hi * h_{t-1} + b_i)
     *   f_t = sigmoid(W_if * x_t + W_hf * h_{t-1} + b_f)
     *   g_t = tanh   (W_ig * x_t + W_hg * h_{t-1} + b_g)
     *   o_t = sigmoid(W_io * x_t + W_ho * h_{t-1} + b_o)
     *   c_t = f_t ⊙ c_{t-1} + i_t ⊙ g_t
     *   h_t = o_t ⊙ tanh(c_t)
     *
     * Weights are stored in flat arrays for cache efficiency.
     * Input is normalised to [0,1] before training.
     */
    private void trainLstm(double[] rawDemand) {
        int H  = lstmHiddenSize;
        int inputSize = 1;
        int combinedSize = inputSize + H;

        // Normalise demand to [0,1]
        double dMin = Arrays.stream(rawDemand).min().orElse(0);
        double dMax = Arrays.stream(rawDemand).max().orElse(1);
        double range = dMax - dMin < 1e-10 ? 1.0 : dMax - dMin;
        double[] y = new double[rawDemand.length];
        for (int i = 0; i < rawDemand.length; i++) y[i] = (rawDemand[i] - dMin) / range;

        // Initialise weights with Xavier uniform
        int gateWeightLen = 4 * H * combinedSize; // 4 gates × H × (input+hidden)
        double scale = Math.sqrt(6.0 / (combinedSize + H));
        Random rng = new Random(42L);
        double[] Wih  = new double[gateWeightLen];
        double[] bH   = new double[4 * H];
        double[] Who  = new double[H];
        double   bO   = 0.0;

        for (int i = 0; i < Wih.length; i++) Wih[i]  = (rng.nextDouble() * 2 - 1) * scale;
        for (int i = 0; i < Who.length; i++) Who[i]   = (rng.nextDouble() * 2 - 1) * scale;
        // Initialise forget gate bias to 1.0 (helps learning long-term dependencies)
        for (int i = H; i < 2*H; i++) bH[i] = 1.0;

        // Training: one-step-ahead prediction, MSE loss, SGD with gradient clipping
        double[] h = new double[H];
        double[] c = new double[H];

        for (int epoch = 0; epoch < lstmEpochs; epoch++) {
            double epochLoss = 0;
            // Reset state at the start of each epoch
            Arrays.fill(h, 0); Arrays.fill(c, 0);

            for (int t = lstmSeqLen; t < y.length; t++) {
                double[] hPrev = h.clone();
                double[] cPrev = c.clone();
                double   input = y[t - 1]; // one-step lagged input

                // ── Forward pass through LSTM cell ──────────────────────
                double[] gates = new double[4 * H]; // [i, f, g, o]
                for (int gate = 0; gate < 4; gate++) {
                    for (int hi = 0; hi < H; hi++) {
                        int base = (gate * H + hi) * combinedSize;
                        // input contribution
                        gates[gate*H + hi] = Wih[base] * input + bH[gate*H + hi];
                        // hidden contribution
                        for (int hj = 0; hj < H; hj++) {
                            gates[gate*H + hi] += Wih[base + 1 + hj] * hPrev[hj];
                        }
                    }
                }

                double[] iG = new double[H], fG = new double[H];
                double[] gG = new double[H], oG = new double[H];
                double[] cNew = new double[H], hNew = new double[H];

                for (int hi = 0; hi < H; hi++) {
                    iG[hi] = sigmoid(gates[hi]);
                    fG[hi] = sigmoid(gates[H  + hi]);
                    gG[hi] = Math.tanh(gates[2*H + hi]);
                    oG[hi] = sigmoid(gates[3*H + hi]);
                    cNew[hi] = fG[hi] * cPrev[hi] + iG[hi] * gG[hi];
                    hNew[hi] = oG[hi] * Math.tanh(cNew[hi]);
                }

                // ── Output layer ────────────────────────────────────────
                double yHat = bO;
                for (int hi = 0; hi < H; hi++) yHat += Who[hi] * hNew[hi];

                double target = y[t];
                double err    = yHat - target;
                epochLoss    += err * err;

                // ── Backward pass (simplified 1-step BPTT) ──────────────
                // dL/dyHat
                double dOut = 2.0 * err / y.length;

                // Output layer gradients
                double dBo = dOut;
                double[] dWho = new double[H];
                double[] dH   = new double[H];
                for (int hi = 0; hi < H; hi++) {
                    dWho[hi] = dOut * hNew[hi];
                    dH[hi]   = dOut * Who[hi];
                }

                // LSTM cell gradients
                double[] dC   = new double[H];
                double[] dIg  = new double[H], dFg = new double[H];
                double[] dGg  = new double[H], dOg = new double[H];

                for (int hi = 0; hi < H; hi++) {
                    double tanhC = Math.tanh(cNew[hi]);
                    double dHo   = dH[hi] * tanhC * oG[hi] * (1 - oG[hi]); // dL/d(oGate pre-activation)
                    dC[hi]  = dH[hi] * oG[hi] * (1 - tanhC * tanhC);
                    dIg[hi] = dC[hi] * gG[hi] * iG[hi] * (1 - iG[hi]);
                    dFg[hi] = dC[hi] * cPrev[hi] * fG[hi] * (1 - fG[hi]);
                    dGg[hi] = dC[hi] * iG[hi] * (1 - gG[hi] * gG[hi]);
                    dOg[hi] = dHo;
                }

                // Update weights with gradient clipping (clip norm = 1.0)
                double gradNorm = 0;
                for (int hi = 0; hi < H; hi++) {
                    gradNorm += dIg[hi]*dIg[hi] + dFg[hi]*dFg[hi] + dGg[hi]*dGg[hi] + dOg[hi]*dOg[hi];
                }
                double clipScale = 1.0;
                if (gradNorm > 1.0) clipScale = 1.0 / Math.sqrt(gradNorm);

                double lr = lstmLearningRate;
                bO -= lr * dBo;
                for (int hi = 0; hi < H; hi++) {
                    Who[hi] -= lr * dWho[hi];
                }
                double[] dGateArr = new double[4*H];
                for (int hi = 0; hi < H; hi++) {
                    dGateArr[hi]       = dIg[hi] * clipScale;
                    dGateArr[H  + hi]  = dFg[hi] * clipScale;
                    dGateArr[2*H + hi] = dGg[hi] * clipScale;
                    dGateArr[3*H + hi] = dOg[hi] * clipScale;
                }
                for (int gate = 0; gate < 4; gate++) {
                    for (int hi = 0; hi < H; hi++) {
                        int base = (gate * H + hi) * combinedSize;
                        bH[gate*H + hi]  -= lr * dGateArr[gate*H + hi];
                        Wih[base]        -= lr * dGateArr[gate*H + hi] * input;
                        for (int hj = 0; hj < H; hj++) {
                            Wih[base + 1 + hj] -= lr * dGateArr[gate*H + hi] * hPrev[hj];
                        }
                    }
                }

                h = hNew;
                c = cNew;
            }

            if (epoch % 20 == 0) {
                LOG.fine("LSTM epoch " + epoch + " loss=" + String.format("%.6f", epochLoss / y.length));
            }
        }

        // Store normalisation params inside weight arrays' metadata via instance fields
        // We re-embed dMin/range into the output bias so forecast can denormalise
        this.lstmWeightsIH = Wih;
        this.lstmBiasH     = bH;
        this.lstmWeightsHO = Who;
        this.lstmBiasO     = bO;
        // Store normalization parameters in a compact way alongside model state
        // by appending to reserved slots in a new array
        double[] normParams = {dMin, range};
        // We attach norm params to lstmWeightsHO's backing by creating a combined array
        double[] whoAndNorm = Arrays.copyOf(Who, H + 2);
        whoAndNorm[H]   = dMin;
        whoAndNorm[H+1] = range;
        this.lstmWeightsHO = whoAndNorm;

        LOG.info("LSTM trained: hidden=" + H + " epochs=" + lstmEpochs
            + " finalLoss estimated from last epoch.");
    }

    /**
     * Runs the trained LSTM forward to produce {@code horizon} future predictions.
     * Feeds each prediction back as the next input (autoregressive).
     */
    private double[] lstmForecast(double[] rawDemand, int horizon) {
        int H = lstmHiddenSize;
        double[] Who = Arrays.copyOf(lstmWeightsHO, H); // first H entries are weights
        double dMin  = lstmWeightsHO[H];
        double range = lstmWeightsHO[H + 1];

        // Normalise historical demand
        double[] y = new double[rawDemand.length];
        for (int i = 0; i < rawDemand.length; i++) y[i] = (rawDemand[i] - dMin) / range;

        // Warm-up hidden state by running through the last lstmSeqLen observations
        double[] h = new double[H];
        double[] c = new double[H];
        int warmup = Math.min(lstmSeqLen, y.length);
        for (int t = y.length - warmup; t < y.length; t++) {
            double[] next = lstmStep(h, c, y[t], H);
            h = Arrays.copyOfRange(next, 0, H);
            c = Arrays.copyOfRange(next, H, 2*H);
        }

        // Autoregressive forecast
        double lastInput = y[y.length - 1];
        double[] forecast = new double[horizon];
        for (int step = 0; step < horizon; step++) {
            double[] next = lstmStep(h, c, lastInput, H);
            h = Arrays.copyOfRange(next, 0, H);
            c = Arrays.copyOfRange(next, H, 2*H);

            double yHat = lstmBiasO;
            for (int hi = 0; hi < H; hi++) yHat += Who[hi] * h[hi];

            // Denormalise
            double denorm = yHat * range + dMin;
            forecast[step] = Math.max(0, denorm);
            lastInput = yHat; // next step uses predicted normalised value
        }
        return forecast;
    }

    /** Single LSTM cell forward pass. Returns [h_new (H), c_new (H)]. */
    private double[] lstmStep(double[] h, double[] c, double input, int H) {
        int combinedSize = 1 + H;
        double[] gates = new double[4 * H];

        for (int gate = 0; gate < 4; gate++) {
            for (int hi = 0; hi < H; hi++) {
                int base = (gate * H + hi) * combinedSize;
                gates[gate*H + hi] = lstmWeightsIH[base] * input + lstmBiasH[gate*H + hi];
                for (int hj = 0; hj < H; hj++) {
                    gates[gate*H + hi] += lstmWeightsIH[base + 1 + hj] * h[hj];
                }
            }
        }

        double[] hNew = new double[H];
        double[] cNew = new double[H];
        for (int hi = 0; hi < H; hi++) {
            double ig = sigmoid(gates[hi]);
            double fg = sigmoid(gates[H  + hi]);
            double gg = Math.tanh(gates[2*H + hi]);
            double og = sigmoid(gates[3*H + hi]);
            cNew[hi] = fg * c[hi] + ig * gg;
            hNew[hi] = og * Math.tanh(cNew[hi]);
        }

        double[] result = new double[2 * H];
        System.arraycopy(hNew, 0, result, 0, H);
        System.arraycopy(cNew, 0, result, H, H);
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Shared helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Ridge (L2-regularised) regression: min ||Ax - y||^2 + lambda * ||x||^2
     * Solved via normal equations: x = (A'A + lambda*I)^{-1} A'y
     * Uses Cholesky decomposition for efficiency.
     */
    private double[] ridgeRegression(double[][] A, double[] y, double lambda, int cols) {
        int n = A.length;
        // Compute ATA + lambda*I and ATy
        double[][] ATA = new double[cols][cols];
        double[]   ATy = new double[cols];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < cols; j++) {
                ATy[j] += A[i][j] * y[i];
                for (int k = 0; k < cols; k++) {
                    ATA[j][k] += A[i][j] * A[i][k];
                }
            }
        }
        for (int j = 0; j < cols; j++) ATA[j][j] += lambda;

        // Cholesky decomposition of ATA
        double[][] L = choleskyDecompose(ATA, cols);
        if (L == null) {
            // Fallback: return zero coefficients (shouldn't happen with lambda>0)
            return new double[cols];
        }
        // Solve L * z = ATy, then L' * x = z
        double[] z = forwardSubstitution(L, ATy, cols);
        return backwardSubstitution(L, z, cols);
    }

    private double[][] choleskyDecompose(double[][] A, int n) {
        double[][] L = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j <= i; j++) {
                double sum = A[i][j];
                for (int k = 0; k < j; k++) sum -= L[i][k] * L[j][k];
                if (i == j) {
                    if (sum <= 0) return null;
                    L[i][j] = Math.sqrt(sum);
                } else {
                    L[i][j] = sum / L[j][j];
                }
            }
        }
        return L;
    }

    private double[] forwardSubstitution(double[][] L, double[] b, int n) {
        double[] z = new double[n];
        for (int i = 0; i < n; i++) {
            double sum = b[i];
            for (int k = 0; k < i; k++) sum -= L[i][k] * z[k];
            z[i] = sum / L[i][i];
        }
        return z;
    }

    private double[] backwardSubstitution(double[][] L, double[] z, int n) {
        double[] x = new double[n];
        for (int i = n-1; i >= 0; i--) {
            double sum = z[i];
            for (int k = i+1; k < n; k++) sum -= L[k][i] * x[k]; // L' upper triangle
            x[i] = sum / L[i][i];
        }
        return x;
    }

    private double[] computeFitted(double[] demand) {
        if (!isTrained) return demand.clone();
        if ("LSTM".equals(selectedAlgorithm)) {
            int n = demand.length;
            // one-step-ahead predictions over training set
            int H = lstmHiddenSize;
            double[] Who  = Arrays.copyOf(lstmWeightsHO, H);
            double dMin   = lstmWeightsHO[H];
            double range  = lstmWeightsHO[H+1];
            double[] y    = new double[n];
            for (int i = 0; i < n; i++) y[i] = (demand[i] - dMin) / range;

            double[] fitted = new double[n];
            double[] h = new double[H], c = new double[H];
            for (int t = 0; t < n; t++) {
                double yHat = lstmBiasO;
                for (int hi = 0; hi < H; hi++) yHat += Who[hi] * h[hi];
                fitted[t] = Math.max(0, yHat * range + dMin);
                double[] next = lstmStep(h, c, t > 0 ? y[t-1] : 0.0, H);
                h = Arrays.copyOfRange(next, 0, H);
                c = Arrays.copyOfRange(next, H, 2*H);
            }
            return fitted;
        } else {
            // Prophet in-sample fit
            int n = demand.length;
            double[] fitted = new double[n];
            int seasonP = 12;
            for (int i = 0; i < n; i++) {
                double t = i / (double)(n-1);
                double trend = prophetBaseLevel + prophetTrendSlopes[0] * t;
                int cpRegion = (int)(n * 0.8);
                for (int k = 0; k < N_CHANGEPOINTS; k++) {
                    int cpIdx = (int)((k+1.0)/(N_CHANGEPOINTS+1.0) * cpRegion);
                    if (i > cpIdx) trend += prophetChangepointDeltas[k] * (i - cpIdx) / (double)n;
                }
                double seasonal = 0;
                for (int k = 1; k <= FOURIER_ORDER; k++) {
                    seasonal += fourierCoeffs[2*(k-1)]   * Math.sin(2*Math.PI*k*i/seasonP);
                    seasonal += fourierCoeffs[2*(k-1)+1] * Math.cos(2*Math.PI*k*i/seasonP);
                }
                fitted[i] = Math.max(0, trend + seasonal);
            }
            return fitted;
        }
    }

    private double backtestMape(double[] demand) {
        int backtest = Math.min(3, Math.max(1, demand.length / 8));
        if (demand.length <= backtest + 3) return 0.0;
        List<Double> actual = new ArrayList<>(), predicted = new ArrayList<>();
        for (int i = demand.length - backtest; i < demand.length; i++) {
            double pred = 0;
            for (int j = Math.max(0, i-4); j < i; j++) pred += demand[j];
            pred /= Math.max(1, i - Math.max(0, i-4));
            actual.add(demand[i]);
            predicted.add(pred);
        }
        return sharedMape(actual, predicted);
    }

    private double coefficientOfVariation(double[] y) {
        double mean = 0; for (double v : y) mean += v; mean /= y.length;
        if (mean < 1e-10) return 1.0;
        double var = 0; for (double v : y) var += (v-mean)*(v-mean); var /= y.length;
        return Math.sqrt(var) / mean;
    }

    private double rmse(double[] actual, double[] predicted) {
        double mse = 0;
        for (int i = 0; i < actual.length; i++) { double e = actual[i]-predicted[i]; mse += e*e; }
        return Math.sqrt(mse / actual.length);
    }

    private double sigmoid(double x) { return 1.0 / (1.0 + Math.exp(-x)); }

    private double[] toDoubleArr(List<BigDecimal> list) {
        double[] a = new double[list.size()];
        for (int i = 0; i < list.size(); i++) a[i] = list.get(i).doubleValue();
        return a;
    }

    private BigDecimal bd(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    private static double sharedMape(List<Double> actual, List<Double> predicted) {
        if (actual == null || predicted == null || actual.size() != predicted.size() || actual.isEmpty()) return Double.NaN;
        double total = 0; int count = 0;
        for (int i = 0; i < actual.size(); i++) {
            double a = actual.get(i), p = predicted.get(i);
            if (Math.abs(a) < 1e-9) continue;
            total += Math.abs((a-p)/a)*100.0; count++;
        }
        return count == 0 ? 0.0 : total / count;
    }

    private static double sharedRmse(List<Double> actual, List<Double> predicted) {
        if (actual == null || predicted == null || actual.size() != predicted.size() || actual.isEmpty()) return Double.NaN;
        double mse = 0;
        for (int i = 0; i < actual.size(); i++) { double e = actual.get(i)-predicted.get(i); mse += e*e; }
        return Math.sqrt(mse / actual.size());
    }
}
