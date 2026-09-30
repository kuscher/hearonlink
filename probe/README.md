# PodLink probe (throwaway)

Feasibility probe: opens the AirPods' AAP channel (classic L2CAP PSM 0x1001) from a normal,
unrooted app and hex-dumps the packets. Built without Gradle (`./build.sh`, aapt2 + javac + d8),
run as instrumentation so output comes straight back over adb:

    adb install -r podprobe.apk && adb shell pm grant local.podlink.probe android.permission.BLUETOOTH_CONNECT
    adb shell am instrument -w -e bypass 1 -e method hidden -e secs 6 \
      -e send "'00000400010002000000000000000000;040004000f00ffffffff'" local.podlink.probe/.Probe

Args: `bypass=1` (LSPosed HiddenApiBypass, Apache-2.0, fetched by build.sh), `method`
settings|settings-secure|hidden|ctor, `psm`, `send`/`late`/`stopSend` (hex packets, `;`-separated),
`secs`, `lateAt`, `max` (full dumps per opcode). Serial-like strings are masked in the output.
Findings: ../docs/research/device-findings.md
