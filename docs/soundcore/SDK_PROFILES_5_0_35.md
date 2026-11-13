# Soundcore 5.0.35-435: newer SDK profiles

`ProductProfileFactory.registry` registers **14 product/component profiles plus one SAMPLE profile**. SAMPLE is a test scaffold and is excluded from device totals. This registry coexists with the app table; overlapping entries are not extra devices. Normalize case on the `-Box` suffix when comparing them. Some profiles are not exposed by the legacy app table. Presence in the SDK is evidence of a compiled protocol profile, not proof of a currently marketed or searchable product.

| Profile | SPP channels (declared order) | BLE service / read / write | Send profile | Receive profile |
| --- | --- | --- | --- | --- |
| D1203 | 0CF12D31-FAC3-4553-BD80-D6832E7B3956, 66666666-6666-6666-6666-666666666666 | 0205F5DA-0000-1000-8000-00805F9B34FB / 00008888-0000-1000-8000-00805F9B34FB / 00007777-0000-1000-8000-00805F9B34FB; 66666666-6666-6666-6666-666666666666 / 77777777-7777-7777-7777-777777777777 / 77777777-7777-7777-7777-777777777777 | D1203SendProfile | D1203ReceiveProfile |
| D1203-BOX | 0205F5DA-0000-1000-8000-00805F9B34FB | 0205F5DA-0000-1000-8000-00805F9B34FB / 00008888-0000-1000-8000-00805F9B34FB / 00007777-0000-1000-8000-00805F9B34FB; 66666666-6666-6666-6666-666666666666 / 77777777-7777-7777-7777-777777777777 / 77777777-7777-7777-7777-777777777777 | D1203BoxSendProfile | D1203BoxReceiveProfile |
| D1204 | 0CF12D31-FAC3-4553-BD80-D6832E7B395C, 66666666-6666-6666-6666-666666666666 | 0214F5DA-0000-1000-8000-00805F9B34FB / 00008888-0000-1000-8000-00805F9B34FB / 00007777-0000-1000-8000-00805F9B34FB; 66666666-6666-6666-6666-666666666666 / 77777777-7777-7777-7777-777777777777 / 77777777-7777-7777-7777-777777777777 | D1204SendProfile | D1204ReceiveProfile |
| D1204-BOX | 0CF12D31-FAC3-4553-BD80-D6832E7B395D, 66666666-6666-6666-6666-666666666666 | 0214F5DA-0000-1000-8000-00805F9B34FB / 00008888-0000-1000-8000-00805F9B34FB / 00007777-0000-1000-8000-00805F9B34FB | D1204BoxSendProfile | D1204BoxReceiveProfile |
| D1302 | 0CF12D31-FAC3-4553-BD80-D6832E7D1302, 66666666-6666-6666-6666-666666666666 | 021AF5DA-0000-1000-8000-00805F9B34FB / 00008888-0000-1000-8000-00805F9B34FB / 00007777-0000-1000-8000-00805F9B34FB; 66666666-6666-6666-6666-666666666666 / 77777777-7777-7777-7777-777777777777 / 77777777-7777-7777-7777-777777777777 | D1302SendProfile | D1302ReceiveProfile |
| D1302-BOX | None declared | 021CF5DA-0000-1000-8000-00805F9B34FB / 00008888-0000-1000-8000-00805F9B34FB / 00007777-0000-1000-8000-00805F9B34FB; 66666666-6666-6666-6666-666666666666 / 77777777-7777-7777-7777-777777777777 / 77777777-7777-7777-7777-777777777777 | D1302BoxSendProfile | D1302BoxReceiveProfile |
| D1303 | 0CF12D31-FAC3-4553-BD80-D6832E7D1303, 00007034-0000-1000-8000-00805F9B34FB | 0227F5DA-0000-1000-8000-00805F9B34FB / 00008888-0000-1000-8000-00805F9B34FB / 00007777-0000-1000-8000-00805F9B34FB; 66666666-6666-6666-6666-666666666666 / 77777777-7777-7777-7777-777777777777 / 77777777-7777-7777-7777-777777777777 | D1303SendProfile | D1303ReceiveProfile |
| D1303-BOX | None declared | 0228F5DA-0000-1000-8000-00805F9B34FB / 00008888-0000-1000-8000-00805F9B34FB / 00007777-0000-1000-8000-00805F9B34FB; 66666666-6666-6666-6666-666666666666 / 77777777-7777-7777-7777-777777777777 / 77777777-7777-7777-7777-777777777777 | D1303BoxSendProfile | D1303BoxReceiveProfile |
| D3200 | 0CF12D31-FAC3-4553-BD80-D6832E7D3200, 66666666-6666-6666-6666-666666666666 | 020CF5DA-0000-1000-8000-00805F9B34FB / 00008888-0000-1000-8000-00805F9B34FB / 00007777-0000-1000-8000-00805F9B34FB; 66666666-6666-6666-6666-666666666666 / 77777777-7777-7777-7777-777777777777 / 77777777-7777-7777-7777-777777777777 | D3200SendProfile | D3200ReceiveProfile |
| D5160 | 0CF12D31-FAC3-4553-BD80-D6832E7D5160, 66666666-6666-6666-6666-666666666666 | 022BF5DA-0000-1000-8000-00805F9B34FB / 00008888-0000-1000-8000-00805F9B34FB / 00007777-0000-1000-8000-00805F9B34FB; 66666666-6666-6666-6666-666666666666 / 77777777-7777-7777-7777-777777777777 / 77777777-7777-7777-7777-777777777777 | D5160SendProfile | D5160ReceiveProfile |
| D1102 | 0CF12D31-FAC3-4553-BD80-D6832E7D1102, 66666666-6666-6666-6666-666666666666 | 0220F5DA-0000-1000-8000-00805F9B34FB / 00008888-0000-1000-8000-00805F9B34FB / 00007777-0000-1000-8000-00805F9B34FB; 66666666-6666-6666-6666-666666666666 / 77777777-7777-7777-7777-777777777777 / 77777777-7777-7777-7777-777777777777 | D1102SendProfile | D1102ReceiveProfile |
| D1105 | 0CF12D31-FAC3-4553-BD80-D6832E7D1105, 00007034-0000-1000-8000-00805F9B34FB | 0221F5DA-0000-1000-8000-00805F9B34FB / 00008888-0000-1000-8000-00805F9B34FB / 00007777-0000-1000-8000-00805F9B34FB; 66666666-6666-6666-6666-666666666666 / 77777777-7777-7777-7777-777777777777 / 77777777-7777-7777-7777-777777777777 | D1105SendProfile | D1105ReceiveProfile |
| D1406 | 0CF12D31-FAC3-4553-BD80-D6832E7D1406, 66666666-6666-6666-6666-666666666666 | 0223F5DA-0000-1000-8000-00805F9B34FB / 00008888-0000-1000-8000-00805F9B34FB / 00007777-0000-1000-8000-00805F9B34FB; 66666666-6666-6666-6666-666666666666 / 77777777-7777-7777-7777-777777777777 / 77777777-7777-7777-7777-777777777777 | D1406SendProfile | D1406ReceiveProfile |
| D1206 | 0CF12D31-FAC3-4553-BD80-D6832E7D1206, 00007034-0000-1000-8000-00805F9B34FB | 0222F5DA-0000-1000-8000-00805F9B34FB / 00008888-0000-1000-8000-00805F9B34FB / 00007777-0000-1000-8000-00805F9B34FB; 66666666-6666-6666-6666-666666666666 / 77777777-7777-7777-7777-777777777777 / 77777777-7777-7777-7777-777777777777 | D1206SendProfile | D1206ReceiveProfile |

## Module composition

These are explicitly constructed modules in each profile, not feature support inferred from similarly named models. Receive profiles can declare functions that the current app does not expose. Some send modules are marker/capability classes with constants; their presence does not establish a complete callable send API in this APK.

### D1203

Send: Inherited / no explicit module constructors found.

Receive: Inherited / no explicit module constructors found.

### D1203-BOX

Send: Inherited / no explicit module constructors found.

Receive: Inherited / no explicit module constructors found.

### D1204

Send: Inherited / no explicit module constructors found.

Receive: Inherited / no explicit module constructors found.

### D1204-BOX

Send: Inherited / no explicit module constructors found.

Receive: Inherited / no explicit module constructors found.

### D1302

Send: Inherited / no explicit module constructors found.

Receive: Inherited / no explicit module constructors found.

### D1302-BOX

Send: Inherited / no explicit module constructors found.

Receive: Inherited / no explicit module constructors found.

### D1303

Send: Inherited / no explicit module constructors found.

Receive: Inherited / no explicit module constructors found.

### D1303-BOX

Send: Inherited / no explicit module constructors found.

Receive: Inherited / no explicit module constructors found.

### D3200

Send: Inherited / no explicit module constructors found.

Receive: Inherited / no explicit module constructors found.

### D5160

Send: Inherited / no explicit module constructors found.

Receive: Inherited / no explicit module constructors found.

### D1102

Send: Inherited / no explicit module constructors found.

Receive: Inherited / no explicit module constructors found.

### D1105

Send: Inherited / no explicit module constructors found.

Receive: Inherited / no explicit module constructors found.

### D1406

Send: Inherited / no explicit module constructors found.

Receive: Inherited / no explicit module constructors found.

### D1206

Send: Inherited / no explicit module constructors found.

Receive: Inherited / no explicit module constructors found.
