package com.iot.ingestservice.dto;

public record LoRaDTO(
    Integer bandwidth,
    Integer spreadingFactor,
    String codeRate
) {}
