package com.forecast;

import com.forecast.controllers.ForecastController;
import com.forecast.models.FeatureTimeSeries;
import com.forecast.models.ForecastResult;
import com.forecast.models.LifecycleContent;
import com.forecast.services.engine.ForecastProcessor;
import com.forecast.services.engine.MLAlgorithmicExceptionSource;
import com.forecast.services.engine.lifecycle.LifeCycleManager;
import com.forecast.services.output.ForecastOutputService;
import com.scm.exceptions.SCMExceptionEvent;
import com.scm.exceptions.SCMExceptionHandler;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class TestForecastRunner {

    public static void main(String[] args) {
        String productId = "P1001";
        String storeId = "S001";

        FeatureTimeSeries features = buildSampleFeatureSeries(productId, storeId);

        LifeCycleManager lifeCycleManager = new LifeCycleManager();

        SCMExceptionHandler scmExceptionHandler = new SCMExceptionHandler() {
            @Override
            public void handle(SCMExceptionEvent event) {
                System.out.println("===== SCM EXCEPTION EVENT =====");
                System.out.println("Event received from shared exception subsystem.");
                System.out.println(event);
                System.out.println("================================");
            }
        };

        MLAlgorithmicExceptionSource exceptionSource =
            new MLAlgorithmicExceptionSource(scmExceptionHandler);

        ForecastOutputService outputService =
            new ForecastOutputService(exceptionSource);

        ForecastProcessor processor =
            new ForecastProcessor(lifeCycleManager, outputService, exceptionSource);

        ForecastController controller =
            new ForecastController(processor);

        LifecycleContent lifecycle = null;

        ForecastResult result = controller.generateForecast(
            productId,
            storeId,
            features,
            lifecycle
        );

        printResult(result);
    }

    private static FeatureTimeSeries buildSampleFeatureSeries(String productId, String storeId) {
        List<LocalDate> dates = new ArrayList<>();
        List<BigDecimal> demandValues = new ArrayList<>();
        List<BigDecimal> seasonalComponent = new ArrayList<>();

        LocalDate start = LocalDate.now().minusMonths(24);

        for (int i = 0; i < 24; i++) {
            dates.add(start.plusMonths(i));

            double base = 100 + (i * 3);
            double seasonalBoost;

            switch (i % 4) {
                case 0:
                    seasonalBoost = 0.95;
                    break;
                case 1:
                    seasonalBoost = 1.05;
                    break;
                case 2:
                    seasonalBoost = 1.15;
                    break;
                default:
                    seasonalBoost = 0.90;
                    break;
            }

            double demand = base * seasonalBoost;
            demandValues.add(BigDecimal.valueOf(demand).setScale(2, RoundingMode.HALF_UP));
        }

        seasonalComponent.add(new BigDecimal("0.95"));
        seasonalComponent.add(new BigDecimal("1.05"));
        seasonalComponent.add(new BigDecimal("1.15"));
        seasonalComponent.add(new BigDecimal("0.90"));

        FeatureTimeSeries features = new FeatureTimeSeries(productId, storeId, dates, demandValues);
        features.setSeasonalComponent(seasonalComponent);
        features.setFeatureEngineeringVersion("v1.0");
        features.setNormalized(false);

        return features;
    }

    private static void printResult(ForecastResult result) {
        if (result == null) {
            System.out.println("No forecast result returned.");
            return;
        }

        System.out.println("===== FORECAST RESULT =====");
        System.out.println("Product ID          : " + result.getProductId());
        System.out.println("Store ID            : " + result.getStoreId());
        System.out.println("Lifecycle Stage     : " + result.getLifecycleStage());
        System.out.println("Generated Date      : " + result.getForecastGeneratedDate());
        System.out.println("Forecast Start Date : " + result.getForecastStartDate());
        System.out.println("Forecast End Date   : " + result.getForecastEndDate());
        System.out.println("Model Used          : " + result.getModelUsed());
        System.out.println("Status              : " + result.getStatus());
        System.out.println("MAPE                : " + result.getMape());
        System.out.println("RMSE                : " + result.getRmse());

        System.out.println("\nForecasted Demand:");
        if (result.getForecastedDemand() != null) {
            for (int i = 0; i < result.getForecastedDemand().size(); i++) {
                String lower = result.getConfidenceIntervalLower() != null
                    && i < result.getConfidenceIntervalLower().size()
                    ? result.getConfidenceIntervalLower().get(i).toString()
                    : "-";

                String upper = result.getConfidenceIntervalUpper() != null
                    && i < result.getConfidenceIntervalUpper().size()
                    ? result.getConfidenceIntervalUpper().get(i).toString()
                    : "-";

                System.out.println(
                    "Month " + (i + 1) +
                    " -> Forecast=" + result.getForecastedDemand().get(i) +
                    ", Lower=" + lower +
                    ", Upper=" + upper
                );
            }
        }
    }
}