package br.com.joshua.apiflowservice.flowtracking.api;

import br.com.joshua.apiflowservice.flowtracking.domain.ComponentType;

/** How a tracked method is presented in the flow graph. */
public record ComponentIdentity(String component, String operation, ComponentType type) {
}
