package br.com.joshua.apiflowservice.orders.adapter.in.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateOrderRequest(@NotEmpty @Size(max = 20) List<@Valid @NotNull Item> items) {

    public record Item(@NotNull @Min(1) Long productId, @Min(1) @Max(10_000) int quantity) {
    }
}
