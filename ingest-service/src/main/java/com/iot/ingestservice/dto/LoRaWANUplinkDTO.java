package com.iot.ingestservice.dto;

import java.util.List;
import java.util.Map;

public record LoRaWANUplinkDTO(
    String deduplicationId,
    String time,
    Map<String, Object> deviceInfo,
    String devAddr,
    Boolean adr,
    Integer dr,
    Integer fCnt,
    Integer fPort,
    Boolean confirmed,
    String data, // Base64
    List<RxInfoDTO> rxInfo,
    TxInfoDTO txInfo,
    String regionConfigId
) {}
