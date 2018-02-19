package org.eclipse.tracecompass.incubator.coherence.core.trace;

import org.eclipse.tracecompass.incubator.coherence.core.model.TmfInferredEvent;
import org.eclipse.tracecompass.tmf.core.event.ITmfEventField;
import org.eclipse.tracecompass.tmf.core.event.TmfEvent;
import org.eclipse.tracecompass.tmf.core.event.TmfEventField;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.ctf.core.event.CtfTmfEvent;

public class CtfInferredEvent extends TmfEvent {
	
	@Override
	public ITmfEventField getContent() {
		// TODO Auto-generated method stub
		return super.getContent();
	}

	private final int fCpu;
	
	public CtfInferredEvent(CtfTmfEvent event, ITmfTrace trace) {
		super(trace, event.getRank(), event.getTimestamp(), event.getType(), event.getContent()); 
		fCpu = event.getCPU();
	}
	
	public CtfInferredEvent(TmfInferredEvent event, ITmfTrace trace) {
		super(trace, event.getRank(), event.getTimestamp(), event.getType(), event.getContent());
		fCpu = event.getCpu();
	}
	
	public int getCpu() {
		return fCpu;
	}
}