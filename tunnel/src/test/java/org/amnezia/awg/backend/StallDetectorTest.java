/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.amnezia.awg.backend;

import static org.junit.Assert.assertEquals;

import org.amnezia.awg.backend.StallDetector.Verdict;
import org.junit.Test;

public class StallDetectorTest {
    private static final long STALL_MS = 20_000;
    private static final long MIN_TX = 600;

    private static StallDetector detector() {
        return new StallDetector(STALL_MS, MIN_TX);
    }

    @Test
    public void firstSampleIsOnlyABaseline() {
        assertEquals(Verdict.IDLE, detector().onSample(0, 92, 148));
    }

    @Test
    public void growingRxIsHealthy() {
        final StallDetector d = detector();
        d.onSample(0, 100, 100);
        assertEquals(Verdict.HEALTHY, d.onSample(2_000, 5_000, 4_000));
        assertEquals(Verdict.HEALTHY, d.onSample(4_000, 9_000, 7_000));
    }

    @Test
    public void keepaliveOnlyIdleTunnelIsNotStalled() {
        final StallDetector d = detector();
        d.onSample(0, 92, 148);
        long tx = 148;
        for (long t = 25_000; t <= 300_000; t += 25_000) {
            tx += 32;
            assertEquals(Verdict.IDLE, d.onSample(t, 92, tx));
        }
    }

    @Test
    public void sendingWithoutAnswerBecomesStalledAfterTimeout() {
        final StallDetector d = detector();
        d.onSample(0, 92, 148);
        assertEquals(Verdict.IDLE, d.onSample(10_000, 92, 5_000));
        assertEquals(Verdict.STALLED, d.onSample(20_000, 92, 9_000));
        assertEquals(Verdict.STALLED, d.onSample(22_000, 92, 9_500));
    }

    @Test
    public void bigTxBeforeTimeoutIsNotStalled() {
        final StallDetector d = detector();
        d.onSample(0, 92, 148);
        assertEquals(Verdict.IDLE, d.onSample(19_000, 92, 90_000));
    }

    @Test
    public void rxAfterStallIsRecoveredOnce() {
        final StallDetector d = detector();
        d.onSample(0, 92, 148);
        d.onSample(21_000, 92, 9_000);
        assertEquals(Verdict.RECOVERED, d.onSample(23_000, 400, 9_200));
        assertEquals(Verdict.HEALTHY, d.onSample(25_000, 900, 9_400));
    }

    @Test
    public void stallTimerRestartsFromLastRx() {
        final StallDetector d = detector();
        d.onSample(0, 92, 148);
        d.onSample(15_000, 500, 700);
        assertEquals(Verdict.IDLE, d.onSample(30_000, 500, 6_000));
        assertEquals(Verdict.STALLED, d.onSample(35_000, 500, 7_000));
    }
}
