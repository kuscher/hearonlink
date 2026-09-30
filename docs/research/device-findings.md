# Device findings (HP Googlebook 14, 2026-09-30)

Checked over adb from the Terminal VM with a throwaway probe (`probe/`, run via `am instrument`).
Device: HP Googlebook 14, Android 17, SDK 37.1, build (build),
Bluetooth mainline module `com.google.android.bt` versionCode 371899999, controller HCI/LMP 5.4.

## AirPods under test

- Name "AirPods Pro", model number **A3048** (per the AAP info packet), bonded over BR/EDR
  (Secure Connections, 16-byte AES key), active for A2DP and HFP. Firmware string
  `81.2675000075000000.6421`.
- Its SDP UUID list includes Apple's AAP service `74ec2172-0bad-4d01-8f77-997b2be0722a`
  and `4715650b-5e9d-4ac2-b898-a4fc0aa5df78`, beside A2DP (110b), AVRCP (110e) and HFP (111e).
- The system Bluetooth stack reports battery via HFP only as one value (`batteryCharge` in the
  headset state machine); no L/R/case split anywhere in Settings.

## Opening the AAP channel (classic L2CAP, PSM 0x1001) from a normal app

| Try | Result |
|---|---|
| Public `BluetoothDevice.createUsingSocketSettings(BluetoothSocketSettings{TYPE_L2CAP, psm 0x1001})` | `IllegalArgumentException: invalid socketType - 3` (the public API accepts RFCOMM and LE only) |
| Reflection `BluetoothDevice.createL2capSocket(int)` without help | `NoSuchMethodException` (hidden API filtered for targetSdk 37) |
| `HiddenApiBypass.addHiddenApiExemptions("L")` (org.lsposed.hiddenapibypass 6.1, Apache-2.0), then `createL2capSocket(0x1001)` | **connect OK in ~100 ms**, maxRx 8087, maxTx 1691 |

So on this stock, unrooted Android 17 build the AAP channel opens and works. **No root and no
Xposed needed.** The only non-public piece is the hidden `createL2capSocket(int)` (also visible:
`createInsecureL2capSocket(int)` and the `BluetoothSocket` constructors).

## First AAP session (read-only: handshake + notification request)

Sent `00000400010002000000000000000000` (handshake) and `040004000f00ffffffff` (request
notifications). The AirPods answered within ~50 ms with:

| Opcode | What it carried |
|---|---|
| 0x0000 | handshake ack |
| 0x001d | device info: name "AirPods Pro", model A3048, "Apple Inc.", serials, firmware versions |
| 0x0002 | capability list |
| 0x0009 × ~21 | every control setting (id, value): e.g. `0d 03` = listening mode 3 (Transparency), plus ids 0x17, 0x18, 0x1b, 0x1f, 0x23, 0x24, 0x25, 0x26, 0x28, 0x29, 0x2c, 0x2e, 0x2f, 0x33, 0x35, 0x3e |
| 0x0004 | battery: 3 entries, left 100 % (discharging), right 100 % (discharging), case not reporting |
| 0x0006 | ear detection state (`01 01`) |
| 0x0017 | HID-over-AAP descriptors incl. **"devmotion6"** (the head-motion IMU service used for head gestures) |
| 0x000c, 0x000e, 0x002e | other hosts this pair knows (device addresses; not logged here) |
| 0x0008, 0x002b, 0x004e, 0x0053, 0x0055 | not decoded yet |

Opcode names (from librepods.md §3.4): 0x0008 bud role, 0x002b stream state, 0x004e proxcard status,
0x0053 headphone accommodation, 0x000c MAC address, 0x000e audio source, 0x002e connected devices,
0x0002 capabilities, 0x0055 unknown. Battery decode: `03 | 04 01 64 02 01 | 02 01 64 02 01 | 08 01 00 04 01`
= left 100 % discharging, right 100 % discharging, case "disconnected" (the case reports only while a bud is in it).
Firmware `version3` = 8454592 (starts with 8 → use the DEVMOTION6 sensor service for head tracking).

## Head tracking (DEVMOTION6, 25 Hz): works

Sent after the handshake:
- start `04 00 04 00 17 00 | 00 00 10 00 | 0f 00 | 08 73 42 0b 08 10 10 02 1a 05 01 40 9c 00 00`
  (RTBuddy envelope, protobuf: seq, service_settings{service 16, setting 2, config 01 ‖ 40000 µs})
- stop: the same with interval 0 (`… 1a 05 01 00 00 00 00`).

Result: start ack (`… 4a 02 08 10`, field 9 service 16) after ~30 ms, then **181 op-0x17 packets in
~7.1 s ≈ 25 Hz**, each 80 bytes (`… 3a 3e 08 10 1a 3a` + 58-byte sensor payload). The stream stopped on
the stop packet. So the head-gesture demo works on this unrooted Googlebook.

Still to probe: a listening-mode write (0x0D), reconnect/sleep behaviour, a recorded nod/shake trace
for unit tests, and the same checks on the x86_64 ASUS Googlebook.
