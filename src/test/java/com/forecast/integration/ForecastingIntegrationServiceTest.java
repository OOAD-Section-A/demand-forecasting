package com.forecast.integration;

import com.forecast.models.FeatureTimeSeries;
import com.forecast.models.ForecastResult;
import com.forecast.models.LifecycleContent;
import com.forecast.models.PatternProfile;
import com.forecast.models.PromoData;
import com.forecast.models.RawSalesData;
import com.forecast.models.exceptions.IMLAlgorithmicExceptionSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForecastingIntegrationServiceTest {

    @Test
    void buildsFeaturesAndGeneratesForecastWithoutDatabase() {
        ForecastingIntegrationService service = new ForecastingIntegrationService(
            new NoopExceptionSource()
        );

        List<RawSalesData> rawSales = sampleSales("P1001", "S001", 36);
        List<PromoData> promos = List.of(samplePromo("P1001", "S001"));
        List<LocalDate> holidays = List.of(LocalDate.of(2025, 12, 1));

        FeatureTimeSeries features = service.buildFeatures(
            "P1001",
            "S001",
            rawSales,
            promos,
            holidays
        );

        assertEquals("2.0", features.getFeatureEngineeringVersion());
        assertEquals(36, features.size());
        assertEquals(36, features.getLaggedDemand().size());
        assertEquals(36, features.getTrendComponent().size());
        assertEquals(36, features.getSeasonalComponent().size());
        assertTrue(features.getCustomFeatures().containsKey("promo_lift"));

        PatternProfile pattern = service.detectPatterns(features);
        assertNotNull(pattern.getDominantPattern());
        assertNotNull(pattern.getForecastingRecommendation());

        LifecycleContent lifecycle = new LifecycleContent(
            "P1001",
            "GROWTH",
            LocalDate.now().minusMonths(6)
        );
        lifecycle.setForecastHorizonMonths(6);

        ForecastResult result = service.generateForecast(
            "P1001",
            "S001",
            features,
            lifecycle
        );

        assertEquals("SUCCESS", result.getStatus());
        assertEquals("GROWTH", result.getLifecycleStage());
        assertTrue(result.getModelUsed().startsWith("PROPHET_LSTM"));
        assertEquals(6, result.getForecastedDemand().size());
        assertEquals(6, result.getConfidenceIntervalLower().size());
        assertEquals(6, result.getConfidenceIntervalUpper().size());
        assertFalse(result.getForecastedDemand().stream().anyMatch(v -> v.signum() < 0));
    }

    private List<RawSalesData> sampleSales(String productId, String storeId, int months) {
        List<RawSalesData> rows = new ArrayList<>();
        LocalDate start = LocalDate.of(2023, 1, 1);
        for (int i = 0; i < months; i++) {
            int seasonal = (i % 12 == 10 || i % 12 == 11) ? 35 : 0;
            int trend = i * 2;
            int quantity = 100 + trend + seasonal;

            rows.add(
                new RawSalesData.Builder()
                    .saleId(i + 1)
                    .productId(productId)
                    .storeId(storeId)
                    .saleDate(start.plusMonths(i))
                    .quantitySold(quantity)
                    .unitPrice(new BigDecimal("10.00"))
                    .revenue(BigDecimal.valueOf(quantity).multiply(new BigDecimal("10.00")))
                    .region("NORTH")
                    .build()
            );
        }
        return rows;
    }

    private PromoData samplePromo(String productId, String storeId) {
        PromoData promo = new PromoData(productId, storeId, "PROMO-1", "SEASONAL");
        promo.setPromotionStartDate(LocalDate.of(2025, 11, 1));
        promo.setPromotionEndDate(LocalDate.of(2025, 12, 31));
        promo.setExpectedDemandLift(20.0);
        return promo;
    }

    private static final class NoopExceptionSource implements IMLAlgorithmicExceptionSource {
        @Override
        public void fireModelDegradation(
            int exceptionId,
            String source,
            String entityKey,
            double expectedThreshold,
            double actualValue
        ) {
        }

        @Override
        public void fireMissingInputData(
            int exceptionId,
            String source,
            String fieldName,
            String context
        ) {
        }

        @Override
        public void fireAlgorithmicAlert(
            int exceptionId,
            String source,
            String entityKey,
            String detail
        ) {
        }
    }
}
