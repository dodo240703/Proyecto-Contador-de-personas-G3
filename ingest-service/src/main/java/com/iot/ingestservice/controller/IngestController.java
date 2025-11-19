package com.iot.ingestservice.controller;

import com.iot.ingestservice.dto.*;
import com.iot.ingestservice.model.Event;
import com.iot.ingestservice.repository.EventRepository;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;

@RestController
@RequestMapping("/ingest")
public class IngestController {

    private final EventRepository repo;
    private static final String API_KEY = "secret123"; // muévelo a config/env en serio

    public IngestController(EventRepository repo) { this.repo = repo; }

    @PostMapping("/mock")
    public Map<String,Object> ingestMock(@Valid @RequestBody MockEventDTO dto){
        Event e = new Event();
        e.setAulaId(dto.aula());
        e.setNodoId(dto.nodo());
        e.setEv(dto.ev());
        e.setTs(dto.ts() != null ? dto.ts() : Instant.now());
        e.setRssi(dto.rssi());
        e.setSnr(dto.snr());
        repo.save(e);
        return Map.of("status","ok","savedId", e.getId());
    }

    @PostMapping("/chirpstack")
    public Map<String,Object> ingestChirpstack(
            @RequestHeader("x-api-key") String apiKey,
            @RequestBody ChirpUplinkDTO uplink){

        if (!API_KEY.equals(apiKey)) {
            return Map.of("status","forbidden");
        }

        // Extrae datos del decoder (ajusta si usas otro esquema)
        var decoded = uplink.uplink_message().decoded_payload();
        var meta = uplink.uplink_message().rx_metadata() != null && !uplink.uplink_message().rx_metadata().isEmpty()
                ? uplink.uplink_message().rx_metadata().get(0) : null;

        Event e = new Event();
        e.setAulaId(decoded != null ? decoded.aula() : "UNK");
        e.setNodoId(decoded != null ? decoded.nodo() : uplink.end_device_ids().device_id());
        e.setEv(decoded != null ? decoded.ev() : "in");
        e.setTs(decoded != null && decoded.ts()!=null ? Instant.parse(decoded.ts()) : Instant.now());
        if (meta != null) { e.setRssi(meta.rssi()); e.setSnr(meta.snr()); }

        repo.save(e);
        return Map.of("status","ok","savedId", e.getId());
    }

    // =====================================================
    // 3️⃣ MOCK-RAW (formato actual de ChirpStack, simulado)
    // =====================================================
    @PostMapping("/mock-raw")
    public Map<String, Object> ingestMockRaw(@RequestBody ChirpRawDTO dto) {
        Event e = decodePayload(dto.payload_hex(), dto.timestamp(), dto.deviceName());
        repo.save(e);
        return Map.of("status", "ok", "savedId", e.getId());
    }

    // =====================================================
    // 4️⃣ CHIRPSTACK-RAW (Webhook real actual)
    // =====================================================
    @PostMapping("/chirpstack-raw")
    public Map<String, Object> ingestChirpstackRaw(
            @RequestHeader("x-api-key") String apiKey,
            @RequestBody ChirpRawDTO dto) {

        if (!API_KEY.equals(apiKey)) {
            return Map.of("status", "forbidden");
        }

        Event e = decodePayload(dto.payload_hex(), dto.timestamp(), dto.deviceName());
        repo.save(e);
        return Map.of("status", "ok", "savedId", e.getId());
    }

    // =====================================================
    // DECODIFICADOR genérico de payload HEX
    // =====================================================
    private Event decodePayload(String payloadHex, String timestamp, String deviceName) {
        Event e = new Event();
        e.setNodoId(deviceName != null ? deviceName : "UNK");
        e.setAulaId("A101");

        try {
            byte[] bytes = hexStringToByteArray(payloadHex);
            int eventType = bytes[0] & 0xFF;
            String ev = switch (eventType) {
                case 0x01 -> "in";
                case 0x02 -> "out";
                default -> "unk";
            };
            e.setEv(ev);
            e.setTs(timestamp != null ? Instant.parse(timestamp) : Instant.now());
        } catch (Exception ex) {
            e.setEv("unk");
            e.setTs(Instant.now());
        }

        return e;
    }

    // =====================================================
    // 5️⃣ LORAWAN UPLINK (Nuevo formato)
    // =====================================================
    @PostMapping("/lorawan")
    public Map<String, Object> ingestLoRaWAN(
            @RequestHeader(value = "x-api-key", required = false) String apiKey,
            @RequestBody LoRaWANUplinkDTO uplink) {

        // Validación de API key (opcional, puedes quitarla si no la necesitas)
        if (apiKey != null && !API_KEY.equals(apiKey)) {
            return Map.of("status", "forbidden");
        }

        Event e = new Event();
        
        // Device Address como nodoId
        e.setNodoId(uplink.devAddr() != null ? uplink.devAddr() : "UNK");
        e.setAulaId("A101"); // Puedes ajustar esto según tu lógica
        
        // Decodificar data de Base64
        try {
            byte[] decodedBytes = Base64.getDecoder().decode(uplink.data());
            
            // Interpretar los bytes: [salieron, entraron]
            // Ejemplo: [1, 2] -> Byte 0 (1) = salieron, Byte 1 (2) = entraron
            if (decodedBytes.length >= 2) {
                int salieron = decodedBytes[0] & 0xFF;  // Byte 0: personas que salieron
                int entraron = decodedBytes[1] & 0xFF;  // Byte 1: personas que entraron
                
                e.setPersonasEntraron(entraron);
                e.setPersonasSalieron(salieron);
                e.setEv("count"); // Tipo de evento: conteo de personas
            } else {
                e.setEv("unknown");
            }
        } catch (Exception ex) {
            e.setEv("error");
        }
        
        // Convertir time de UTC a hora de Perú (GMT-5)
        try {
            Instant utcTime = Instant.parse(uplink.time());
            ZonedDateTime peruTime = utcTime.atZone(ZoneId.of("America/Lima"));
            e.setTs(peruTime.toInstant());
        } catch (Exception ex) {
            e.setTs(Instant.now());
        }
        
        // Extraer RSSI y SNR del primer gateway
        if (uplink.rxInfo() != null && !uplink.rxInfo().isEmpty()) {
            RxInfoDTO firstRx = uplink.rxInfo().get(0);
            e.setRssi(firstRx.rssi());
            e.setSnr(firstRx.snr());
        }
        
        repo.save(e);
        return Map.of(
            "status", "ok", 
            "savedId", e.getId(),
            "decodedEvent", e.getEv(),
            "personasEntraron", e.getPersonasEntraron() != null ? e.getPersonasEntraron() : 0,
            "personasSalieron", e.getPersonasSalieron() != null ? e.getPersonasSalieron() : 0,
            "peruTime", e.getTs().toString()
        );
    }

    private static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2)
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        return data;
    }
}
