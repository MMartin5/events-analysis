package org.eclipse.tracecompass.incubator.coherence.core.newmodel;

import org.eclipse.tracecompass.tmf.core.event.ITmfEventField;
import org.eclipse.tracecompass.tmf.core.event.ITmfEventType;
import org.eclipse.tracecompass.tmf.core.event.TmfEvent;
import org.eclipse.tracecompass.tmf.core.timestamp.ITmfTimestamp;
import org.eclipse.tracecompass.tmf.core.timestamp.TmfTimestamp;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;

public class TmfInferredEvent extends TmfEvent {
	
	/* Interval inside which the event could have happen */
	private final ITmfTimestamp fStart;
	private final ITmfTimestamp fEnd;
	/* Rank relative to the position of this event in the sequence of inferred events between last known coherent event and incoherent event */
	private final long fLocalRank;
	/* Transition labelled by this event */
	private TmfXmlFsmTransition fTransition;
	
	public TmfInferredEvent(final ITmfTrace trace,
            final long rank,
            final long localRank,
            final ITmfTimestamp tsStart,
            final ITmfTimestamp tsEnd,
            final ITmfEventType type,
            final ITmfEventField content,
            final TmfXmlFsmTransition transition) {
		super(trace, rank, null, type, content);
		
		fStart = tsStart;
		fEnd = tsEnd;
		fLocalRank = localRank;
		fTransition = transition;
	}
	
	@Override
	public boolean equals(Object obj) {
		TmfInferredEvent other = (TmfInferredEvent) obj;
		if ((other.getName().equals(this.getName())) &&
				(other.getStartTime() == this.getStartTime()) &&
				(other.getEndTime() == this.getEndTime()) &&
				(other.getLocalRank() == this.getLocalRank())) {
			return true;
		}
		return false;
	}
	
	public boolean greaterThan(TmfInferredEvent other) {
		if ((this.getStartTime() > other.getEndTime()) ||
				((this.getStartTime() == other.getStartTime()) && (this.getEndTime() == other.getEndTime()) && 
						(this.getLocalRank() > other.getLocalRank()))) {
			return true;
		}
		return false;
	}

	/**
	 * Returns a timestamp in the middle of the possible interval 
	 */
	@Override
    public ITmfTimestamp getTimestamp() {
        return TmfTimestamp.create(fStart.getValue() + (fEnd.getValue() - fStart.getValue()), fStart.getScale());
    }
	
	public long getStartTime() {
		return fStart.getValue();
	}
	
	public long getEndTime() {
		return fEnd.getValue();
	}
	
	public long getLocalRank() {
		return fLocalRank;
	}
	
	public TmfXmlFsmTransition getTransition() {
		return fTransition;
	}

}
