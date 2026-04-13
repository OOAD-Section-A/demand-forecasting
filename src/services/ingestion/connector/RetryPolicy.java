package com.forecast.ingestion.connector;

/**
 * Immutable retry configuration used by DataSourceConnector.
 *
 * Implements exponential backoff: each attempt waits
 *   initialDelayMs * (2 ^ attemptIndex)
 * up to a ceiling of maxDelayMs.
 *
 * Example:
 *   RetryPolicy policy = new RetryPolicy.Builder()
 *       .maxAttempts(3)
 *       .initialDelayMs(500)
 *       .maxDelayMs(8_000)
 *       .build();
 */
public final class RetryPolicy {

    public static final RetryPolicy DEFAULT = new Builder().build();
    public static final RetryPolicy NO_RETRY = new Builder().maxAttempts(1).build();

    private final int  maxAttempts;
    private final long initialDelayMs;
    private final long maxDelayMs;

    private RetryPolicy(Builder b) {
        this.maxAttempts    = b.maxAttempts;
        this.initialDelayMs = b.initialDelayMs;
        this.maxDelayMs     = b.maxDelayMs;
    }

    public int  getMaxAttempts()    { return maxAttempts; }
    public long getInitialDelayMs() { return initialDelayMs; }
    public long getMaxDelayMs()     { return maxDelayMs; }

    /**
     * Computes the delay before the next attempt using exponential backoff.
     *
     * @param attemptIndex zero-based index of the attempt that just failed
     * @return milliseconds to sleep before retrying
     */
    public long delayForAttempt(int attemptIndex) {
        long delay = initialDelayMs * (1L << attemptIndex);   // 2^attemptIndex
        return Math.min(delay, maxDelayMs);
    }

    @Override
    public String toString() {
        return "RetryPolicy{maxAttempts=" + maxAttempts +
               ", initialDelayMs=" + initialDelayMs +
               ", maxDelayMs=" + maxDelayMs + '}';
    }

    // ---------------------------------------------------------------
    // Builder
    // ---------------------------------------------------------------

    public static final class Builder {

        private int  maxAttempts    = 3;       // spec: "retry … 3 attempts"
        private long initialDelayMs = 500;
        private long maxDelayMs     = 8_000;

        public Builder maxAttempts(int val) {
            if (val < 1) throw new IllegalArgumentException("maxAttempts must be >= 1");
            this.maxAttempts = val;
            return this;
        }

        public Builder initialDelayMs(long val) {
            if (val < 0) throw new IllegalArgumentException("initialDelayMs must be >= 0");
            this.initialDelayMs = val;
            return this;
        }

        public Builder maxDelayMs(long val) {
            if (val < initialDelayMs) throw new IllegalArgumentException(
                    "maxDelayMs must be >= initialDelayMs");
            this.maxDelayMs = val;
            return this;
        }

        public RetryPolicy build() {
            return new RetryPolicy(this);
        }
    }
}
