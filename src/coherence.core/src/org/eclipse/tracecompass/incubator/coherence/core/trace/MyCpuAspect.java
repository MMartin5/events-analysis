package org.eclipse.tracecompass.incubator.coherence.core.trace;

import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.aspect.TmfCpuAspect;

public class MyCpuAspect extends TmfCpuAspect {

    @Override
    public Integer resolve(ITmfEvent event) {
        if (!(event instanceof CtfInferredEvent)) {
            return null;
        }
        int cpu = ((CtfInferredEvent) event).getCpu();
        return cpu;
    }
}