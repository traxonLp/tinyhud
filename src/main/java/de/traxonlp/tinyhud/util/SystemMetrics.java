package de.traxonlp.tinyhud.util;

import com.sun.management.OperatingSystemMXBean;
import de.traxonlp.tinyhud.TinyHUD;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SystemMetrics {

    private static final OperatingSystemMXBean OS =
            (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

    private static final AtomicBoolean GPU_POLL_STARTED = new AtomicBoolean(false);
    private static volatile boolean  gpuAvailable = true;
    private static volatile Integer  gpuPercent   = null;

    private SystemMetrics() {}

    // Public API

    public static double cpuLoad() {
        double load = OS.getCpuLoad();
        if (Double.isNaN(load) || load < 0) load = OS.getProcessCpuLoad();
        return Math.max(0.0, Math.min(1.0, load));
    }

    public static double memoryLoad() {
        long total = OS.getTotalMemorySize();
        long free  = OS.getFreeMemorySize();
        if (total <= 0) return 0.0;
        return (total - free) / (double) total;
    }

    public static long totalMemoryBytes() {
        return OS.getTotalMemorySize();
    }

    public static long usedMemoryBytes() {
        return OS.getTotalMemorySize() - OS.getFreeMemorySize();
    }

    public static Integer gpuPercent() {
        ensureGpuPolling();
        return gpuAvailable ? gpuPercent : null;
    }

    // GPU polling

    private static void ensureGpuPolling() {
        if (!GPU_POLL_STARTED.compareAndSet(false, true)) return;
        Thread t = new Thread(SystemMetrics::pollGpuLoop, "TinyHUD-GPU-Poll");
        t.setDaemon(true);
        t.start();
    }

    private static void pollGpuLoop() {
        // Probe phase: try each backend in priority order, pick first that responds.
        GpuBackend active = null;
        for (GpuBackend b : GPU_BACKENDS) {
            try {
                Integer v = b.query();
                if (v != null) {
                    active = b;
                    gpuPercent = v;
                    TinyHUD.LOGGER.info("TinyHUD GPU: using backend '{}'.", b.name());
                    break;
                }
            } catch (Exception ignored) {}
        }
        if (active == null) {
            gpuAvailable = false;
            TinyHUD.LOGGER.info("TinyHUD GPU: no backend available; element will display '?'.");
            return;
        }

        // Poll phase: repeat indefinitely with the winning backend.
        final GpuBackend backend = active;
        while (true) {
            try {
                Thread.sleep(backend.pollIntervalMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            try {
                Integer v = backend.query();
                if (v == null) { gpuAvailable = false; return; }
                gpuPercent = v;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (IOException e) {
                gpuAvailable = false;
                return;
            }
        }
    }

    // Backend interface

    private interface GpuBackend {
        /** Human-readable backend name used in log messages. */
        String name();

        /**
         * Returns GPU utilisation 0-100 %, or {@code null} if this backend is
         * not available on the current system.  May throw on transient I/O errors.
         */
        Integer query() throws IOException, InterruptedException;

        /**
         * How long the poll loop should sleep <em>between</em> query() calls.
         * Backends that are self-timing (e.g. typeperf -sc 1 ≈ 1 s) can return
         * a shorter value here so the effective polling period stays near 2 s.
         */
        default long pollIntervalMs() { return 2000L; }
    }

    /** Backends are tried in declaration order; first non-null result wins. */
    private static final GpuBackend[] GPU_BACKENDS = {
            new NvidiaBackend(),
            new WindowsPdhBackend(),
            new LinuxAmdSysfsBackend(),
            new MacIoregBackend(),
    };

    // Backend: NVIDIA nvidia-smi (all platforms)

    private static final class NvidiaBackend implements GpuBackend {
        @Override public String name() { return "nvidia-smi"; }

        @Override
        public Integer query() throws IOException, InterruptedException {
            ProcessBuilder pb = new ProcessBuilder(
                    "nvidia-smi", "--query-gpu=utilization.gpu",
                    "--format=csv,noheader,nounits");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            if (!p.waitFor(2, TimeUnit.SECONDS)) { p.destroyForcibly(); return null; }
            if (p.exitValue() != 0) return null;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line = r.readLine();
                if (line == null) return null;
                try { return Integer.parseInt(line.trim()); }
                catch (NumberFormatException ex) { return null; }
            }
        }
    }

    // Backend: Windows PDH typeperf - covers NVIDIA + AMD + Intel
    //
    // Uses the built-in typeperf.exe with the "GPU Engine" performance counter
    // that Windows 10 version 1903+ exposes for every GPU vendor.
    //
    // Counter: \GPU Engine(*engintype_3D)\Utilization Percentage
    //   • One instance per (process x engine x adapter).
    //   • Summing all instances for a single GPU equals Task Manager's GPU 3D %.
    //   • With multiple GPUs the sum is clamped to 100 (shows busiest GPU's share).
    //
    // typeperf -sc 1 blocks for ~1 s; pollIntervalMs() returns 1000 so the
    // effective polling interval is roughly 2 s total.

    private static final class WindowsPdhBackend implements GpuBackend {
        private static final String COUNTER =
                "\\GPU Engine(*engintype_3D)\\Utilization Percentage";

        @Override public String name() { return "typeperf (Windows GPU PDH)"; }
        @Override public long pollIntervalMs() { return 1000L; }

        @Override
        public Integer query() throws IOException, InterruptedException {
            if (!isWindows()) return null;

            ProcessBuilder pb = new ProcessBuilder("typeperf", COUNTER, "-sc", "1");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            if (!p.waitFor(6, TimeUnit.SECONDS)) { p.destroyForcibly(); return null; }
            if (p.exitValue() != 0) return null;

            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                // Line 0: CSV header  "(PDH-CSV 4.0)","\\host\GPU Engine(...)\...",...
                // Line 1: data        "timestamp","val1","val2",...
                // Lines 2+: status messages from typeperf (ignored)
                String header = r.readLine();
                if (header == null || !header.startsWith("\"(PDH-CSV")) return null;

                String data = null;
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty() && line.startsWith("\"")) { data = line; break; }
                }
                if (data == null) return null;

                // Simplest correct CSV split for this output:
                // strip outer quotes, split on ","
                if (data.startsWith("\"")) data = data.substring(1);
                if (data.endsWith("\""))   data = data.substring(0, data.length() - 1);
                String[] fields = data.split("\",\"");

                double sum = 0;
                int count = 0;
                for (int i = 1; i < fields.length; i++) { // skip timestamp at [0]
                    try { sum += Double.parseDouble(fields[i].trim()); count++; }
                    catch (NumberFormatException ignored) {}
                }
                // count == 0 means counter exists but no 3D engine instances -> idle (0%)
                return count == 0 ? 0 : (int) Math.min(100, Math.round(sum));
            }
        }

        private static boolean isWindows() {
            return System.getProperty("os.name", "").toLowerCase().contains("windows");
        }
    }

    // Backend: Linux AMD via amdgpu sysfs
    //
    // The amdgpu kernel driver (default on Linux for all recent AMD GPUs) exposes
    // GPU busy percentage at:
    //   /sys/class/drm/card{N}/device/gpu_busy_percent
    // No subprocess - file read is instant.

    private static final class LinuxAmdSysfsBackend implements GpuBackend {
        @Override public String name() { return "amdgpu sysfs"; }

        @Override
        public Integer query() throws IOException {
            if (!isLinux()) return null;
            // Try card0 … card3; index may shift on multi-GPU systems.
            for (int i = 0; i <= 3; i++) {
                Path f = Path.of("/sys/class/drm/card" + i + "/device/gpu_busy_percent");
                if (!Files.exists(f)) continue;
                try { return Integer.parseInt(Files.readString(f).trim()); }
                catch (NumberFormatException ignored) {}
            }
            return null;
        }

        private static boolean isLinux() {
            return System.getProperty("os.name", "").toLowerCase().contains("linux");
        }
    }

    // Backend: macOS ioreg - covers Intel iGPU, AMD dGPU, Apple Silicon
    //
    // Runs `ioreg -r -c <class> -w 0` and searches for utilisation keys inside
    // the PerformanceStatistics dictionary:
    //
    //   IOAccelerator  -> Intel / AMD discrete -> "Device Utilization %"
    //   AGXAccelerator -> Apple Silicon (M1/M2/M3) -> "Renderer Utilization %"
    //
    // Returns the maximum value across all accelerator instances (handles multi-GPU).

    private static final class MacIoregBackend implements GpuBackend {
        private static final String[][] CLASS_KEYS = {
                // { ioreg class,       utilisation key prefix }
                { "IOAccelerator",  "\"Device Utilization %\"="   },
                { "IOAccelerator",  "\"GPU Activity\"="            },  // older fallback
                { "AGXAccelerator", "\"Renderer Utilization %\"="  },
        };

        @Override public String name() { return "ioreg (macOS IOAccelerator/AGXAccelerator)"; }

        @Override
        public Integer query() throws IOException, InterruptedException {
            if (!isMac()) return null;
            // Try every (class, key) pair; return the first non-null result.
            String lastClass = null;
            String[] lastLines = null;
            for (String[] ck : CLASS_KEYS) {
                String cls = ck[0], key = ck[1];
                // Avoid running ioreg twice for the same class
                String[] lines;
                if (cls.equals(lastClass) && lastLines != null) {
                    lines = lastLines;
                } else {
                    lines = runIoreg(cls);
                    if (lines == null) continue;
                    lastClass = cls;
                    lastLines = lines;
                }
                Integer v = parseKey(lines, key);
                if (v != null) return v;
            }
            return null;
        }

        private static String[] runIoreg(String className)
                throws IOException, InterruptedException {
            ProcessBuilder pb = new ProcessBuilder("ioreg", "-r", "-c", className, "-w", "0");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            if (!p.waitFor(4, TimeUnit.SECONDS)) { p.destroyForcibly(); return null; }
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                return r.lines().toArray(String[]::new);
            }
        }

        /** Scans the ioreg output lines for {@code key} and returns the max integer found. */
        private static Integer parseKey(String[] lines, String key) {
            int maxUtil = -1;
            for (String line : lines) {
                int idx = line.indexOf(key);
                if (idx < 0) continue;
                // The value immediately follows the key: key + digits + (,|})
                String rest = line.substring(idx + key.length()).trim();
                int end = 0;
                while (end < rest.length() && Character.isDigit(rest.charAt(end))) end++;
                if (end == 0) continue;
                try { maxUtil = Math.max(maxUtil, Integer.parseInt(rest.substring(0, end))); }
                catch (NumberFormatException ignored) {}
            }
            return maxUtil >= 0 ? maxUtil : null;
        }

        private static boolean isMac() {
            String os = System.getProperty("os.name", "").toLowerCase();
            return os.contains("mac") || os.contains("darwin");
        }
    }
}
