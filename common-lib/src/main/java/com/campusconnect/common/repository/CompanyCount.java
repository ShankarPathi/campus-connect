package com.campusconnect.common.repository;

/**
 * Aggregation projection (Story 8.5): the number of {@code OFFICIALLY_PLACED} placement records at one
 * {@code company}, within the current tenant (a per-placement count). The group key (company) is projected
 * from the pipeline's {@code _id}.
 */
public record CompanyCount(String company, long placements) {
}
