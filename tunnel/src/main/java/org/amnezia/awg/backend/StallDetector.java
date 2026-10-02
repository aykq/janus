/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.amnezia.awg.backend;

/**
 * Flags a tunnel that keeps sending while the peer stops answering.
 */
final class StallDetector {
    enum Verdict { IDLE, HEALTHY, STALLED, RECOVERED }

    private final long stallMs;
    private final long minTxBytes;
    private long lastRx = -1;
    private long txAtLastRx;
    private long lastRxAtMs;
    private boolean stalled;

    StallDetector(final long stallMs, final long minTxBytes) {
        this.stallMs = stallMs;
        this.minTxBytes = minTxBytes;
    }

    Verdict onSample(final long nowMs, final long rx, final long tx) {
        // First sample is only a baseline: its rx may be the handshake response of a path that then died.
        if (lastRx < 0) {
            lastRx = rx;
            txAtLastRx = tx;
            lastRxAtMs = nowMs;
            return Verdict.IDLE;
        }
        if (rx > lastRx) {
            lastRx = rx;
            txAtLastRx = tx;
            lastRxAtMs = nowMs;
            if (stalled) {
                stalled = false;
                return Verdict.RECOVERED;
            }
            return Verdict.HEALTHY;
        }
        if (tx - txAtLastRx >= minTxBytes && nowMs - lastRxAtMs >= stallMs) {
            stalled = true;
            return Verdict.STALLED;
        }
        return Verdict.IDLE;
    }
}
