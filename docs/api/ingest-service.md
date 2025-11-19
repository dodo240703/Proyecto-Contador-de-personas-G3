# Ingest Service API

Base URL por defecto: `http://localhost:8081/ingest`

Registra eventos en la tabla `events`. Algunos endpoints aceptan un header `x-api-key` (ver seguridad).

## Endpoints

### POST /mock
Mock simple para insertar manualmente un evento.

Headers: none

Body (JSON — MockEventDTO):
```json
{
  "aula": "A101",
  "nodo": "nodo-1",
  "ev": "in",
  "ts": "2025-09-10T10:00:00Z",
  "rssi": -43.5,
  "snr": 8.2
}
```

Respuesta:
```json
{ "status": "ok", "savedId": 123 }
```

---

### POST /chirpstack
Webhook con el formato de "uplink" ya decodificado por tu codec en ChirpStack.

Headers:
- `x-api-key: <clave>`

Body (JSON — ChirpUplinkDTO):
```json
{
  "end_device_ids": { "device_id": "dev-1", "dev_eui": "0004A30..." },
  "uplink_message": {
    "decoded_payload": { "aula": "A101", "nodo": "sensor-1", "ev": "in", "ts": "2025-09-10T10:00:00Z" },
    "rx_metadata": [ { "rssi": -45.2, "snr": 7.1 } ],
    "received_at": 1694340000
  }
}
```

Respuesta:
```json
{ "status": "ok", "savedId": 124 }
```

---

### POST /mock-raw
Simula el formato RAW de ChirpStack actual, donde el payload llega en HEX.

Body (JSON — ChirpRawDTO):
```json
{
  "timestamp": "2025-09-10T10:00:00Z",
  "deviceName": "sensor-1",
  "devEUI": "0004A30...",
  "fCnt": 10,
  "fPort": 1,
  "payload_base64": "AQI=",
  "payload_hex": "01"
}
```

Reglas de decodificación (método `decodePayload`):
- Primer byte del HEX = tipo de evento
  - `0x01` → `in`
  - `0x02` → `out`
  - otro → `unk`
- `ts` = `timestamp` si viene; de lo contrario, `now()`.

Respuesta: igual a `/mock`.

---

### POST /chirpstack-raw
Igual a `/mock-raw`, pero protegido por API key.

Headers:
- `x-api-key: <clave>`

Body: igual a `/mock-raw`.

---

### POST /lorawan
Webhook con formato genérico de uplink LoRaWAN (bytes en Base64). Interpreta los dos primeros bytes como conteo de personas.

Headers:
- `x-api-key` (opcional; si se envía y no coincide, el request es rechazado)

Body (JSON — LoRaWANUplinkDTO, campos relevantes):
```json
{
  "time": "2025-09-10T15:00:00Z",
  "devAddr": "01AB23CD",
  "data": "AQI=",
  "rxInfo": [ { "rssi": -50.0, "snr": 6.0 } ]
}
```

Decodificación:
- `data` en Base64 → bytes
- Si `len >= 2`:
  - Byte 0 → `personasSalieron`
  - Byte 1 → `personasEntraron`
  - `ev = "count"`
- Convierte `time` UTC a zona `America/Lima` y guarda en `ts`.
- `nodoId = devAddr`

Respuesta (ejemplo):
```json
{
  "status": "ok",
  "savedId": 125,
  "decodedEvent": "count",
  "personasEntraron": 2,
  "personasSalieron": 1,
  "peruTime": "2025-09-10T10:00:00Z"
}
```

## Seguridad

- Para `/chirpstack` y `/chirpstack-raw` se exige `x-api-key`.
- Para `/lorawan` la API key es opcional: si se envía debe coincidir.
- La clave actual está hardcodeada como `secret123` en `IngestController`. Recomendado mover a configuración.

## Notas de implementación

- Conversión HEX → bytes: `hexStringToByteArray`.
- Zona horaria: se convierte a `America/Lima` solo en `/lorawan` y luego se guarda como `Instant`.
- Manejo de errores: si falla decodificación, se marca `ev = "error"` (o `unk`) y se usa `now()`.

## Ejemplos de cURL

```bash
# Inserción mock simple
curl -X POST http://localhost:8081/ingest/mock \
  -H 'Content-Type: application/json' \
  -d '{"aula":"A101","nodo":"n1","ev":"in","ts":"2025-09-10T10:00:00Z"}'

# ChirpStack (decoder)
curl -X POST http://localhost:8081/ingest/chirpstack \
  -H 'Content-Type: application/json' -H 'x-api-key: secret123' \
  -d '{"end_device_ids":{"device_id":"dev-1"},"uplink_message":{"decoded_payload":{"aula":"A101","nodo":"n1","ev":"in","ts":"2025-09-10T10:00:00Z"}}}'

# RAW (HEX)
curl -X POST http://localhost:8081/ingest/mock-raw \
  -H 'Content-Type: application/json' \
  -d '{"payload_hex":"01","timestamp":"2025-09-10T10:00:00Z","deviceName":"n1"}'

# LoRaWAN (Base64)
curl -X POST http://localhost:8081/ingest/lorawan \
  -H 'Content-Type: application/json' \
  -d '{"time":"2025-09-10T15:00:00Z","devAddr":"01AB23CD","data":"AQI=","rxInfo":[{"rssi":-50,"snr":6}]}'
```
