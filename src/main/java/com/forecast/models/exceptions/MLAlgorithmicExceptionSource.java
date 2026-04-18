package com.forecast.models.exceptions;

import com.forecast.models.exceptions.IMLAlgorithmicExceptionSource;
import com.scm.exceptions.SCMExceptionEvent;
import com.scm.exceptions.SCMExceptionHandler;
import com.scm.exceptions.Severity;

import java.util.Objects;
import java.util.logging.Logger;

/**
 * Default implementation of IMLAlgorithmicExceptionSource.
 * Bridges forecasting-side alerts to the shared SCM exception handler.
 */
public class MLAlgorithmicExceptionSource implements IMLAlgorithmicExceptionSource {

    private static final Logger LOG =
        Logger.getLogger(MLAlgorithmicExceptionSource.class.getName());

    private final SCMExceptionHandler handler;

    public MLAlgorithmicExceptionSource(SCMExceptionHandler handler) {
        this.handler = Objects.requireNonNull(
            handler,
            "SCMExceptionHandler must not be null"
        );
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
        String errorMessage =
            "Forecast model accuracy below threshold for entity [" + entityKey + "]";
        String detail =
            "source=" + source +
            ", entity=" + entityKey +
            ", expectedThreshold=" + expectedThreshold +
            ", actualValue=" + actualValue;

        LOG.warning(
            "ML Model Degradation [ID=" + exceptionId + "] " + detail
        );

        SCMExceptionEvent event = new SCMExceptionEvent(
            exceptionId,
            "MODEL_DEGRADATION",
            Severity.MAJOR,
            source,
            errorMessage,
            detail
        );

        handler.handle(event);
    }

    @Override
    public void fireMissingInputData(
        int exceptionId,
        String source,
        String fieldName,
        String context
    ) {
        String errorMessage =
            "Missing forecast input data: " + fieldName;
        String detail =
            "source=" + source +
            ", fieldName=" + fieldName +
            ", context=" + context;

        LOG.warning(
            "Missing Input Data [ID=" + exceptionId + "] " + detail
        );

        SCMExceptionEvent event = new SCMExceptionEvent(
            exceptionId,
            "MISSING_INPUT_DATA",
            Severity.WARNING,
            source,
            errorMessage,
            detail
        );

        handler.handle(event);
    }

    @Override
    public void fireAlgorithmicAlert(
        int exceptionId,
        String source,
        String entityKey,
        String detail
    ) {
        String errorMessage =
            "Forecast algorithmic alert for entity [" + entityKey + "]";

        LOG.warning(
            "Algorithmic Alert [ID=" + exceptionId + "] " +
            "source=" + source +
            ", entity=" + entityKey +
            ", detail=" + detail
        );

        SCMExceptionEvent event = new SCMExceptionEvent(
            exceptionId,
            "ALGORITHMIC_ALERT",
            Severity.MINOR,
            source,
            errorMessage,
            detail
        );

        handler.handle(event);
    }
}