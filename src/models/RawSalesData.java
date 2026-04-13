package com.forecast.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Immutable record of a single sales transaction, exactly as it arrives
 * from the source before any cleaning or validation.
 *
 * Field names map 1-to-1 with the Sales Records entity in the data
 * requirements spec (sale_id, product_id, store_id, …).
 *
 * Built with the inner Builder so callers read clearly at construction:
 *
 *   RawSalesData record = new RawSalesData.Builder()
 *       .saleId(101)
 *       .productId("PROD-042")
 *       .storeId("STORE-7")
 *       .saleDate(LocalDate.of(2024, 12, 25))
 *       .quantitySold(15)
 *       .unitPrice(new BigDecimal("29.99"))
 *       .revenue(new BigDecimal("449.85"))
 *       .region("SOUTH")
 *       .build();
 */
public final class RawSalesData {

    private final long        saleId;
    private final String      productId;
    private final String      storeId;
    private final LocalDate   saleDate;
    private final int         quantitySold;
    private final BigDecimal  unitPrice;
    private final BigDecimal  revenue;
    private final String      region;

    private RawSalesData(Builder b) {
        this.saleId       = b.saleId;
        this.productId    = b.productId;
        this.storeId      = b.storeId;
        this.saleDate     = b.saleDate;
        this.quantitySold = b.quantitySold;
        this.unitPrice    = b.unitPrice;
        this.revenue      = b.revenue;
        this.region       = b.region;
    }

    public long       getSaleId()       { return saleId; }
    public String     getProductId()    { return productId; }
    public String     getStoreId()      { return storeId; }
    public LocalDate  getSaleDate()     { return saleDate; }
    public int        getQuantitySold() { return quantitySold; }
    public BigDecimal getUnitPrice()    { return unitPrice; }
    public BigDecimal getRevenue()      { return revenue; }
    public String     getRegion()       { return region; }

    @Override
    public String toString() {
        return "RawSalesData{saleId=" + saleId +
               ", productId='" + productId + '\'' +
               ", storeId='" + storeId + '\'' +
               ", saleDate=" + saleDate +
               ", quantitySold=" + quantitySold +
               ", unitPrice=" + unitPrice +
               ", revenue=" + revenue +
               ", region='" + region + '\'' + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RawSalesData)) return false;
        RawSalesData that = (RawSalesData) o;
        return saleId == that.saleId &&
               quantitySold == that.quantitySold &&
               Objects.equals(productId, that.productId) &&
               Objects.equals(storeId, that.storeId) &&
               Objects.equals(saleDate, that.saleDate) &&
               Objects.equals(unitPrice, that.unitPrice) &&
               Objects.equals(revenue, that.revenue) &&
               Objects.equals(region, that.region);
    }

    @Override
    public int hashCode() {
        return Objects.hash(saleId, productId, storeId, saleDate,
                            quantitySold, unitPrice, revenue, region);
    }

    // ---------------------------------------------------------------
    // Builder
    // ---------------------------------------------------------------

    public static final class Builder {

        private long       saleId;
        private String     productId;
        private String     storeId;
        private LocalDate  saleDate;
        private int        quantitySold;
        private BigDecimal unitPrice;
        private BigDecimal revenue;
        private String     region;

        public Builder saleId(long val)            { this.saleId = val;       return this; }
        public Builder productId(String val)       { this.productId = val;    return this; }
        public Builder storeId(String val)         { this.storeId = val;      return this; }
        public Builder saleDate(LocalDate val)     { this.saleDate = val;     return this; }
        public Builder quantitySold(int val)       { this.quantitySold = val; return this; }
        public Builder unitPrice(BigDecimal val)   { this.unitPrice = val;    return this; }
        public Builder revenue(BigDecimal val)     { this.revenue = val;      return this; }
        public Builder region(String val)          { this.region = val;       return this; }

        public RawSalesData build() {
            Objects.requireNonNull(productId, "productId is required");
            Objects.requireNonNull(storeId,   "storeId is required");
            Objects.requireNonNull(saleDate,  "saleDate is required");
            if (quantitySold < 0) throw new IllegalStateException("quantitySold cannot be negative");
            return new RawSalesData(this);
        }
    }
}