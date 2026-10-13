package br.com.joshua.apiflowservice.flowtracking.domain;

/**
 * Architectural role of a tracked component. Drives icons and grouping in the visualization.
 */
public enum ComponentType {
    CONTROLLER,
    USE_CASE,
    SERVICE,
    REPOSITORY,
    DATABASE
}
