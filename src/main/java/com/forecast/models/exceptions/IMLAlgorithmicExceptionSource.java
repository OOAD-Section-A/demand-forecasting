package com.forecast.models.exceptions;

/**
 * Minimal outbound contract to the shared exception subsystem.
 * This project only needs a focused subset of exception-routing operations.
 */
public interface IMLAlgorithmicExceptionSource {

    void fireModelDegradation(
        int exceptionId,
        String source,
        String entityKey,
        double expectedThreshold,
        double actualValue
    );

    void fireMissingInputData(
        int exceptionId,
        String source,
        String fieldName,
        String context
    );

    void fireAlgorithmicAlert(
        int exceptionId,
        String source,
        String entityKey,
        String detail
    );
}