package com.forecast.models.exceptions;

import com.scm.subsystems.DemandForecastingSubsystem;

import java.util.logging.Logger;

public class MLAlgorithmicExceptionSource implements IMLAlgorithmicExceptionSource {

    private static final Logger LOG =
        Logger.getLogger(MLAlgorithmicExceptionSource.class.getName());

    private final DemandForecastingSubsystem exceptions;

    public MLAlgorithmicExceptionSource() {
        this.exceptions = DemandForecastingSubsystem.INSTANCE;
        LOG.info("MLAlgorithmicExceptionSource initialized.");
    }

    @Override
    public void fireModelDegradation(
        int exceptionId,
        String source,
        String entityKey,
        double expectedThreshold,
        double actualValue
    ) {
        LOG.warning(
            "Model degradation routed to shared handler: " +
            "source=" + source +
            ", entity=" + entityKey +
            ", threshold=" + expectedThreshold +
            ", actual=" + actualValue
        );

        exceptions.onModelAccuracyBelowThreshold(
            source,
            expectedThreshold,
            actualValue
        );
    }

    @Override
    public void fireMissingInputData(
        int exceptionId,
        String source,
        String fieldName,
        String context
    ) {
        LOG.warning(
            "Missing input data routed to shared handler: " +
            "source=" + source +
            ", field=" + fieldName +
            ", context=" + context
        );

        if ("ForecastedDemandValues".equals(fieldName)) {
            exceptions.onReplenishmentSignalNotGenerated(context);
        } else if ("promotional_data".equalsIgnoreCase(fieldName)) {
            exceptions.onMissingPromotionalData(context);
        } else if ("holiday_data".equalsIgnoreCase(fieldName)) {
            exceptions.onHolidayDataStale(context);
        } else {
            LOG.warning(
                "No public shared exception method available for field=" +
                fieldName +
                ", context=" +
                context
            );
        }
    }

    @Override
    public void fireAlgorithmicAlert(
        int exceptionId,
        String source,
        String entityKey,
        String detail
    ) {
        LOG.warning(
            "Algorithmic alert routed to shared handler: " +
            "source=" + source +
            ", entity=" + entityKey +
            ", detail=" + detail
        );

        try {
            double outlierValue = extractNumericValue(detail);
            exceptions.onOutlierDetected(entityKey, outlierValue);
        } catch (Exception e) {
            LOG.warning(
                "Could not map algorithmic alert to a public shared exception method. " +
                "entity=" + entityKey +
                ", detail=" + detail
            );
        }
    }

    private double extractNumericValue(String detail) {
        String cleaned = detail.replaceAll("[^0-9.\\-]", " ").trim();
        String[] parts = cleaned.split("\\s+");
        for (String p : parts) {
            if (!p.isEmpty()) {
                return Double.parseDouble(p);
            }
        }
        throw new IllegalArgumentException("No numeric value found in detail");
    }
}