package com.forecast.exception;

/**
 * Canonical error codes for the demand forecasting subsystem.
 * Maps directly to the exception table in the project specification.
 *
 * MAJOR   — system cannot proceed; forecast halted or fallback invoked.
 * MINOR   — forecast proceeds with degraded input; output flagged.
 * WARNING — non-critical anomaly; logged and handled automatically.
 */
public enum ErrorCode {

    // ── MAJOR ───────────────────────────────────────────────────────
    DATA_SOURCE_UNAVAILABLE(Severity.MAJOR,
        "Unable to connect to the data source. Connection timed out or credentials are invalid."),

    FORECAST_MODEL_FAILURE(Severity.MAJOR,
        "Selected forecasting model failed to converge or threw a runtime error during execution."),

    INSUFFICIENT_HISTORY(Severity.MAJOR,
        "Product has fewer than the minimum required data points to generate a reliable forecast."),

    LIFECYCLE_STAGE_CONFLICT(Severity.MAJOR,
        "Conflicting lifecycle stage records found for the same product with overlapping effective dates."),

    DB_WRITE_FAILURE(Severity.MAJOR,
        "Failed to persist forecast output or performance metadata to the shared database."),

    // ── MINOR ───────────────────────────────────────────────────────
    MISSING_PROMOTIONAL_DATA(Severity.MINOR,
        "No promotional calendar entries found for the forecast period. Promo features will be absent."),

    HOLIDAY_DATA_STALE(Severity.MINOR,
        "Holiday calendar data has not been updated within the expected refresh window."),

    PARTIAL_INVENTORY_DATA(Severity.MINOR,
        "Inventory or supply data is incomplete for one or more stores."),

    MODEL_ACCURACY_BELOW_THRESHOLD(Severity.MINOR,
        "MAPE or RMSE for a product exceeds the acceptable accuracy threshold after evaluation."),

    FEATURE_ENGINEERING_SKIPPED(Severity.MINOR,
        "One or more feature transformations could not be applied due to missing input data."),

    // ── WARNING ─────────────────────────────────────────────────────
    LIFECYCLE_STAGE_NOT_SET(Severity.WARNING,
        "Product does not have a lifecycle stage assigned in the database."),

    DUPLICATE_SALES_RECORDS(Severity.WARNING,
        "Duplicate entries detected for the same product, store, and date during data ingestion."),

    OUTLIER_DETECTED(Severity.WARNING,
        "Anomalous sales value detected that is more than 3 standard deviations from the product mean."),

    REPLENISHMENT_SIGNAL_NOT_GENERATED(Severity.WARNING,
        "Replenishment signal could not be computed due to missing stock level or lead time data."),

    FORECAST_HORIZON_EXCEEDED(Severity.WARNING,
        "Requested forecast horizon exceeds the model's reliable prediction window for this lifecycle stage.");

    // ----------------------------------------------------------------

    private final Severity severity;
    private final String   defaultMessage;

    ErrorCode(Severity severity, String defaultMessage) {
        this.severity       = severity;
        this.defaultMessage = defaultMessage;
    }

    public Severity getSeverity()      { return severity; }
    public String   getDefaultMessage(){ return defaultMessage; }
    public boolean  isMajor()          { return severity == Severity.MAJOR; }

    public enum Severity { MAJOR, MINOR, WARNING }
}