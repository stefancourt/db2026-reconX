package com.dbtraining.reconx.service;

public record ReconSummary(long total, long matched, long broken) {
    public static ReconSummary empty() { return new ReconSummary(0, 0, 0); }

    public static final class Builder {
        long total;
        long matched;
        long broken;
    }
}