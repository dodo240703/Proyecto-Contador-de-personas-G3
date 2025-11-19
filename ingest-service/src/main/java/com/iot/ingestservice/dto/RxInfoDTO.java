package com.iot.ingestservice.dto;

import java.util.Map;

public record RxInfoDTO(
    String gatewayId,
    Integer uplinkId,
    String gwTime,
    String nsTime,
    String timeSinceGpsEpoch,
    Double rssi,
    Double snr,
    Integer channel,
    Integer rfChain,
    Map<String, Object> location,
    String context,
    String crcStatus
) {}
