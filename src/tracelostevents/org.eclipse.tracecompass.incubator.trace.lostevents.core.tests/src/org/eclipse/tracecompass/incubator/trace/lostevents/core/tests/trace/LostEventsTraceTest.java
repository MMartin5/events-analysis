package org.eclipse.tracecompass.incubator.trace.lostevents.core.tests.trace;

import static org.junit.Assert.assertNotNull;

import org.eclipse.tracecompass.incubator.trace.lostevents.core.trace.LostEventsTrace;
import org.junit.Test;

public class LostEventsTraceTest {

    @Test
    public void testCtfTmfTrace() {
        LostEventsTrace trace = new LostEventsTrace();

        assertNotNull(trace);
    }

}
