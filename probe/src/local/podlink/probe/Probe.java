package local.podlink.probe;

import android.app.Activity;
import android.app.Instrumentation;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.BluetoothSocketSettings;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.os.SystemClock;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Feasibility probe, run with `am instrument -w -e method settings ... local.podlink.probe/.Probe`.
 * Opens the AirPods' AAP channel (classic L2CAP, PSM 0x1001), sends the given packets and
 * hex-dumps what comes back. Sends only what the caller passes; changes no settings by itself.
 */
public class Probe extends Instrumentation {
    static final UUID AAP = UUID.fromString("74ec2172-0bad-4d01-8f77-997b2be0722a");
    Bundle args;
    final StringBuilder log = new StringBuilder();
    final long t0 = SystemClock.elapsedRealtime();

    @Override public void onCreate(Bundle arguments) { args = arguments; start(); }

    @Override public void onStart() {
        try { run(); } catch (Throwable t) { say("FATAL " + t); }
        Bundle out = new Bundle();
        out.putString(Instrumentation.REPORT_KEY_STREAMRESULT, log.toString());
        finish(Activity.RESULT_OK, out);
    }

    void say(String s) {
        log.append(String.format(Locale.US, "%6d ms  ", SystemClock.elapsedRealtime() - t0)).append(s).append('\n');
    }

    String arg(String k, String d) { String v = args.getString(k); return v == null ? d : v; }

    void run() throws Exception {
        BluetoothAdapter ad = getContext().getSystemService(BluetoothManager.class).getAdapter();
        BluetoothDevice dev = null;
        for (BluetoothDevice d : ad.getBondedDevices()) {
            boolean aap = false;
            ParcelUuid[] u = d.getUuids();
            if (u != null) for (ParcelUuid p : u) if (AAP.equals(p.getUuid())) aap = true;
            say("bonded: " + d.getName() + " type=" + d.getType() + " aap=" + aap);
            if (aap && dev == null) dev = d;
        }
        if (dev == null) { say("no bonded device with the AAP uuid"); return; }

        if (arg("bypass", "0").equals("1"))
            say("HiddenApiBypass.addHiddenApiExemptions(L) = " + org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions("L"));
        String method = arg("method", "settings");
        int psm = Integer.decode(arg("psm", "0x1001"));
        BluetoothSocket s = open(dev, method, psm);
        if (s == null) return;

        AtomicReference<Throwable> err = new AtomicReference<>();
        Thread c = new Thread(() -> { try { s.connect(); } catch (Throwable t) { err.set(t); } });
        c.start(); c.join(12000);
        if (c.isAlive()) { say("connect: timed out after 12 s"); s.close(); return; }
        if (err.get() != null) { say("connect FAILED: " + err.get()); s.close(); return; }
        say("connect OK (" + method + ", psm " + Integer.toHexString(psm) + ")"
                + " maxRx=" + s.getMaxReceivePacketSize() + " maxTx=" + s.getMaxTransmitPacketSize());

        OutputStream os = s.getOutputStream();
        InputStream is = s.getInputStream();
        Map<String, int[]> counts = new TreeMap<>();
        Thread reader = new Thread(() -> {
            byte[] buf = new byte[2048];
            try {
                int n;
                while ((n = is.read(buf)) > 0) {
                    String op = n >= 6 ? String.format("%02x%02x", buf[5], buf[4]) : "short";
                    int[] cnt = counts.computeIfAbsent(op, k -> new int[1]);
                    cnt[0]++;
                    int full = Integer.parseInt(arg("max", "3"));
                    if (cnt[0] <= full) say("rx op=" + op + " len=" + n + "  " + hex(buf, Math.min(n, 96)) + ascii(buf, n));
                }
            } catch (Throwable t) { say("read ended: " + t.getClass().getSimpleName() + " " + t.getMessage()); }
        });
        reader.start();

        send(os, arg("send", ""));
        int secs = Integer.parseInt(arg("secs", "12"));
        int lateAt = Integer.parseInt(arg("lateAt", "0"));
        if (lateAt > 0 && lateAt < secs) {
            Thread.sleep(lateAt * 1000L);
            send(os, arg("late", ""));
            Thread.sleep((secs - lateAt) * 1000L);
        } else {
            Thread.sleep(secs * 1000L);
        }
        send(os, arg("stopSend", ""));
        Thread.sleep(400);
        s.close();
        reader.join(2000);
        StringBuilder sum = new StringBuilder("packets by opcode:");
        for (Map.Entry<String, int[]> e : counts.entrySet()) sum.append(' ').append(e.getKey()).append('=').append(e.getValue()[0]);
        say(sum.toString());
    }

    BluetoothSocket open(BluetoothDevice dev, String method, int psm) {
        try {
            switch (method) {
                case "settings":
                case "settings-secure": {
                    boolean sec = method.equals("settings-secure");
                    BluetoothSocketSettings st = new BluetoothSocketSettings.Builder()
                            .setSocketType(BluetoothSocket.TYPE_L2CAP).setL2capPsm(psm)
                            .setAuthenticationRequired(sec).setEncryptionRequired(sec).build();
                    say("settings: " + st);
                    return dev.createUsingSocketSettings(st);
                }
                case "hidden": {
                    Method m = BluetoothDevice.class.getMethod("createL2capSocket", int.class);
                    return (BluetoothSocket) m.invoke(dev, psm);
                }
                case "ctor": {
                    for (Constructor<?> k : BluetoothSocket.class.getDeclaredConstructors()) say("ctor: " + k);
                    for (Method m : BluetoothDevice.class.getDeclaredMethods())
                        if (m.getName().toLowerCase(Locale.US).contains("l2cap") || m.getName().contains("Socket")) say("dev: " + m);
                    return null;
                }
            }
            say("unknown method " + method);
        } catch (Throwable t) {
            Throwable c = t.getCause() != null ? t.getCause() : t;
            say("open(" + method + ") FAILED: " + c);
        }
        return null;
    }

    void send(OutputStream os, String packets) throws Exception {
        for (String p : packets.split("[;,]")) {
            p = p.replaceAll("[^0-9a-fA-F]", "");
            if (p.isEmpty()) continue;
            byte[] b = new byte[p.length() / 2];
            for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(p.substring(2 * i, 2 * i + 2), 16);
            os.write(b);
            os.flush();
            say("tx " + hex(b, b.length));
            Thread.sleep(150);
        }
    }

    static String hex(byte[] b, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(String.format("%02x", b[i])).append(i % 2 == 1 ? " " : "");
        return sb.toString().trim();
    }

    /** Printable runs of 3+ chars; serial-number-like tokens are masked. */
    static String ascii(byte[] b, int n) {
        StringBuilder all = new StringBuilder(), run = new StringBuilder();
        for (int i = 0; i <= n; i++) {
            char ch = i < n ? (char) (b[i] & 0xff) : 0;
            if (ch >= 0x20 && ch < 0x7f) { run.append(ch); continue; }
            if (run.length() >= 3) all.append(" | ").append(run.toString().replaceAll("\\b[A-Z0-9]{10,14}\\b", "<serial>"));
            run.setLength(0);
        }
        return all.length() == 0 ? "" : "   ascii:" + all;
    }
}
