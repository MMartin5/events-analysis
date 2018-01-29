package org.eclipse.tracecompass.incubator.coherence.core.tests;

import org.eclipse.tracecompass.incubator.coherence.core.model.TmfInferredEvent;
import org.eclipse.tracecompass.tmf.core.event.ITmfEventField;
import org.eclipse.tracecompass.tmf.core.event.ITmfEventType;
import org.eclipse.tracecompass.tmf.core.event.TmfEventField;
import org.eclipse.tracecompass.tmf.core.timestamp.ITmfTimestamp;
import org.eclipse.tracecompass.tmf.core.trace.ITmfContext;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;

public class TmfInferredEventTest extends TmfInferredEvent {
	
	private final String fName;
	private final ITmfTimestamp fStart;
	private final ITmfTimestamp fEnd;
	private final long fLocalRank;
	
	public TmfInferredEventTest(String name, ITmfTimestamp start, ITmfTimestamp end, long localRank, ITmfEventField content) {
		super(null, ITmfContext.UNKNOWN_RANK, localRank, null, start, end, null, content, 0);
		
		fName = name;
		fStart = start;
		fEnd = end;
		fLocalRank = localRank;
	}
	
	/*
	 * Overridden methods created to match the methods used in 
	 * @see TmfInferredEvent.equals() 
	 */
	
	@Override
	public String getName() {
		return fName;
	}
	
	@Override
	public long getStartTime() {
		return fStart.getValue();
	}
	
	@Override
	public long getEndTime() {
		return fEnd.getValue();
	}
	
	@Override
	public long getLocalRank() {
		return fLocalRank;
	}

}
