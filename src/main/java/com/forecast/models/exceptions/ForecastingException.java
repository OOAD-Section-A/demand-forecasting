package com.forecast.models.exceptions;

/**
 * Base runtime exception for the demand forecasting subsystem.
 *
 * Every exception thrown inside the subsystem wraps an ErrorCode so
 * callers can branch on severity without inspecting message strings.
 *
 * Usage:
 *   throw new ForecastingException(ErrorCode.DATA_SOURCE_UNAVAILABLE, cause);
 *   throw new ForecastingException(ErrorCode.OUTLIER_DETECTED,
 *                                  "product=PROD-42, value=9999, capped=847");
 */
public class ForecastingException extends RuntimeException {

    private final ErrorCode errorCode;

    public ForecastingException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public ForecastingException(ErrorCode errorCode, String detail) {
        super(errorCode.getDefaultMessage() + " | " + detail);
        this.errorCode = errorCode;
    }

    public ForecastingException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getDefaultMessage(), cause);
        this.errorCode = errorCode;
    }

    public ForecastingException(ErrorCode errorCode, String detail, Throwable cause) {
        super(errorCode.getDefaultMessage() + " | " + detail, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public boolean isMajor() {
        return errorCode.isMajor();
    }
}