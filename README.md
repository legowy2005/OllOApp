# Ollo - Companion App for DIY AR Glasses

Ollo is the dedicated companion application for **Ollo DIY AR Glasses** (ESP32-S3 + RP2040 micro-OLED display). The app manages flashcard decks and performs high-speed, incremental synchronization over Bluetooth Low Energy (BLE).

---

## Hardware & System Architecture

| Subsystem | Specification / Constraint | App Implementation |
|---|---|---|
| **Microcontroller** | ESP32-S3 Super Mini (BLE 5, LittleFS) | Streaming packets directly to flash, one packet at a time |
| **Display Driver** | RP2040 with 640x480 1-bit framebuffer | 1-bit binarization & bit packing |
| **Display** | 0.23" 640x400 micro-OLED | 1-bit live preview (lit pixel = white on black) |
| **Display Buffer** | Max 320x240 pixels (RP2040 limit) | Scaled to fit (default 256x192, selectable up to 320x240) |
| **Display Font** | 5x7 built-in ASCII bitmap font | Max 100 characters per side, ASCII validator, auto-sanitize |
| **Line Breaks** | Wire encoding: literal `\n` sequence | Encoded as `\n` (backslash + n) on wire; normal newlines in UI |
| **Image Wire Format** | Rows of `ceil(width/8)` bytes, MSB first | Bit 1 = lit pixel. No headers, raw pixel data |
| **Image ID** | CRC32 of `(width u16 LE + height u16 LE + pixelData)` | Unsigned 32-bit (if 0 then 1). Deduplicated across deck |
| **Image Size Cap** | 10 KB (10,240 bytes) hard cap | Enforced in image processing pipeline |

---

## BLE Protocol Reference

- **Service UUID:** `6f6c6c6f-0001-4000-8000-00805f9b34fb`
- **Write Characteristic:** `6f6c6c6f-0002-4000-8000-00805f9b34fb` (Write Without Response)
- **Notify Characteristic:** `6f6c6c6f-0003-4000-8000-00805f9b34fb` (Notify)

### Packets (App -> ESP)
All integers are Little-Endian.

1. `0x01 BEGIN_SYNC`: `[0x01][cardCount: u16 LE]`
2. `0x02 CARD`: `[0x02][index: u16 LE][frontImgId: u32 LE][backImgId: u32 LE][frontLen: u8][backLen: u8][frontText: ASCII][backText: ASCII]`
3. `0x03 IMG_BEGIN`: `[0x03][imgId: u32 LE][width: u16 LE][height: u16 LE][dataLen: u32 LE]`
4. `0x04 IMG_CHUNK`: `[0x04][offset: u32 LE][data bytes...]` (rest of packet <= 240 bytes)
5. `0x05 IMG_END`: `[0x05][checksum: u8]` (sum of all pixel data bytes mod 256)
6. `0x06 END_SYNC`: `[0x06]`

### Notifications (ESP -> App)
`[0x80][refType: u8][status: u8]`
- **refType:** `0x01` (BEGIN_SYNC), `0x02` (CARD), `0x03` (IMG_BEGIN), `0x05` (IMG_END), `0x06` (END_SYNC)
- **status:**
  - `0`: OK
  - `1`: Error
  - `2`: Already have this image (for `IMG_BEGIN`: app skips sending chunks and `IMG_END`)
  - `3`: Storage full

---

## Build & Run Instructions

### 1. Android
1. Open the project folder in **Android Studio** (Hedgehog or newer recommended).
2. Connect your Android phone with USB Debugging enabled, or launch an Android emulator.
3. Select the `app` run configuration and click **Run** (`Shift + F10`).
4. To build debug APK via command line:
   ```bash
   ./gradlew assembleDebug
   ```
5. To run unit tests:
   ```bash
   ./gradlew :app:testDebugUnitTest
   ```

### 2. iOS (Xcode)
1. Open Xcode and open or create a SwiftUI project targeting iOS 15.0+ located in `iosApp/`.
2. Ensure the following Info.plist privacy descriptions are present:
   - `NSBluetoothAlwaysUsageDescription`
   - `NSPhotoLibraryUsageDescription`
   - `NSCameraUsageDescription`
3. Connect your iPhone via USB, select your development team for signing, and click **Run** (`Cmd + R`).

---

## Assumptions & Design Decisions

1. **Incremental Sync & Deduplication:** When multiple cards use the same image (e.g., a recurring diagram), it is stored only once locally (as `<imgId>.bin`) and transmitted only once during sync. If the ESP already has the image in LittleFS (status `2`), chunk streaming is skipped entirely.
2. **Virtual Ollo Glasses Simulator:** When testing in the browser emulator or without physical hardware, an in-app "Virtual Ollo Glasses" toggle is provided. It executes the exact firmware state machine, responds with LittleFS status codes, and logs all packets to the in-app BLE Log screen.
3. **Storage Budget:** Defaults to 1 MB (1024 KB) representing typical ESP32 LittleFS partition allocation, customizable via Settings slider (256 KB to 4096 KB).
4. **Wire Text & Line Breaks:** Newlines in the editor are converted to the literal two-character sequence `\n` (ASCII 0x5C 0x6E) and capped at 100 characters per side so the entire `CARD` packet fits within a single BLE packet under 240 bytes.
5. **Dithering & Inversion:** Floyd-Steinberg dithering is enabled by default with a live preview. An Invert toggle allows swapping lit/unlit states for dark diagrams on white backgrounds.
