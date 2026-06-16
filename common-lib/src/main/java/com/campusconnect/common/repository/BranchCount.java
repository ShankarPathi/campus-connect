package com.campusconnect.common.repository;

/**
 * Aggregation projection (Story 8.5): the number of {@code studentProfiles} in one {@code academic.branch},
 * within the current tenant. The group key (branch) is projected from the pipeline's {@code _id}.
 */
public record BranchCount(String branch, long total) {
}
