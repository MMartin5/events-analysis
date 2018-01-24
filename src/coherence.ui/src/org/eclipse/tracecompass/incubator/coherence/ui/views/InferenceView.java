package org.eclipse.tracecompass.incubator.coherence.ui.views;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.incubator.coherence.core.newmodel.TmfXmlFsmTransition;
import org.eclipse.tracecompass.incubator.coherence.ui.model.IncoherentEvent;
import org.eclipse.tracecompass.internal.analysis.os.linux.core.kernel.StateValues;
import org.eclipse.tracecompass.internal.analysis.os.linux.ui.views.controlflow.ControlFlowEntry;
import org.eclipse.tracecompass.statesystem.core.interval.ITmfStateInterval;
import org.eclipse.tracecompass.statesystem.core.interval.TmfStateInterval;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.ITimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.TimeEvent;

public class InferenceView extends CoherenceView {

	public static final @NonNull String ID = "org.eclipse.tracecompass.incubator.coherence.ui.views.inference";
	
	private ControlFlowEntry fEntry;
	private IncoherentEvent fIncoherence;
	
	public InferenceView() {
		super();
		
		fEntry = null;
	}
	
	public void setProperties(ControlFlowEntry entry, IncoherentEvent incoherence) {
		fEntry = entry;
		fIncoherence = incoherence;
		// Get the local entry of the same entry belonging to the control flow view
		ControlFlowEntry newEntry = this.findEntry(getTrace(), entry.getThreadId(), entry.getStartTime());
		TraceEntry traceEntry = (TraceEntry) getEntryList(getTrace()).get(0);
		// Delete every entry of the trace entry and add only the one we need
		traceEntry.clearChildren();
		newEntry.setParent(null); // reset parent in order to be able to add it again
		traceEntry.addChild(newEntry);
	}
	
	@Override
	protected List<ITimeEvent> createTimeEvents(ControlFlowEntry controlFlowEntry, Collection<ITmfStateInterval> value) {
		
		// Skip if fEntry has not been initialized yet
		if (fEntry == null || controlFlowEntry.getName() != fEntry.getName()) {
			return Collections.emptyList();
		}
		
		List<TmfXmlFsmTransition> inferredTransitions = fIncoherence.getIncoherence().getInferredTransitions(); 
		
		// Get iterator on inferred transitions
		if (inferredTransitions != null) {
			Iterator<TmfXmlFsmTransition> it = inferredTransitions.iterator();
			TmfXmlFsmTransition transition = null;
	        ITmfEvent inferredEvent = null;
	        long inferredEventTs = 0;
	        int stateValue = StateValues.PROCESS_STATUS_UNKNOWN;
	        if (it.hasNext()) {
	        	transition = it.next();
	        	inferredEvent = fIncoherence.getIncoherence().getEvent(transition);
	        	inferredEventTs = inferredEvent.getTimestamp().getValue();
	        	stateValue = handleInferredEvent(transition);
	        }
		
			// Add incoherent state intervals to the given list of intervals
			Collection<ITmfStateInterval> newValue = new ArrayList<>();
			
			Iterator<ITmfStateInterval> intervalIt = value.iterator();
			boolean getNext = true;
			ITmfStateInterval interval = null;
			while (intervalIt.hasNext()) {
				if (getNext) {
					interval = intervalIt.next();
				}
				/* the inferred event is in the middle of the current interval
				 * which means that the new state begins somewhere in the middle too
				 * (at the timestamp of the inferred event) 
				 */
				if ((inferredEvent != null) 
						&& ((inferredEventTs > interval.getStartTime()) 
								&& (inferredEventTs < interval.getEndTime()))) {
					ITmfStateInterval newInterval1 = new TmfStateInterval(
							interval.getStartTime(), 
							inferredEventTs - 1, 
							interval.getAttribute(), 
							interval.getValue());
					ITmfStateInterval newInterval2 = new TmfStateInterval(
							inferredEventTs, 
							interval.getEndTime(), 
							interval.getAttribute(), 
							stateValue);
					newValue.add(newInterval1);
					interval = newInterval2; // we cannot add it now because another inferred event could occur before the end of the original interval
					getNext = false; // we don't want to consider the next interval because we need to check the next inferred event for closing the interval 
					// Get the next incoherent event, if it exists
	        		if (it.hasNext()) {
	        			transition = it.next();
	        			inferredEvent = fIncoherence.getIncoherence().getEvent(transition);
	    	        	inferredEventTs = inferredEvent.getTimestamp().getValue();
	    	        	stateValue = handleInferredEvent(transition);
	        		}
	        		else {
	        			inferredEvent = null;
	        			inferredEventTs = 0;
	        			stateValue = StateValues.PROCESS_STATUS_UNKNOWN;
	        		}
				}
				// Default case
				else {
					newValue.add(interval);
					getNext = true;
				}
			}
			
			/* Create the time events from the new list of intervals
			 * 
			 * This is a copy of @see ControlFlowView.createTimeEvents
			 */
			List<ITimeEvent> events = new ArrayList<>(newValue.size());
	        ITimeEvent prev = null;
	        for (ITmfStateInterval newInterval : newValue) {
	            ITimeEvent event = createTimeEvent(newInterval, controlFlowEntry);
	            if (prev != null) {
	                long prevEnd = prev.getTime() + prev.getDuration();
	                if (prevEnd < event.getTime()) {
	                    // fill in the gap.
	                    events.add(new TimeEvent(controlFlowEntry, prevEnd, event.getTime() - prevEnd));
	                }
	            }
	            prev = event;
	            events.add(event);
	        }
	        return events;
		}
		else {
			return super.createTimeEvents(controlFlowEntry, value);
		}
    }

	// FIXME make it more general
	private int handleInferredEvent(TmfXmlFsmTransition inferredTransition) {
		if (inferredTransition == null) {
			return StateValues.PROCESS_STATUS_UNKNOWN;
		}
		String targetState = inferredTransition.to().getTarget();
		if (targetState.equals("unknown")) {
			return StateValues.PROCESS_STATUS_UNKNOWN;
		}
		if (targetState.equals("wait_blocked")) {
			return StateValues.PROCESS_STATUS_WAIT_BLOCKED;
		}
		if (targetState.equals("usermode")) {
			return StateValues.PROCESS_STATUS_RUN_USERMODE;
		}
		if (targetState.equals("syscall")) {
			return StateValues.PROCESS_STATUS_RUN_SYSCALL;
		}
		if (targetState.equals("interrupted")) {
			return StateValues.PROCESS_STATUS_INTERRUPTED;
		}
		if (targetState.equals("wait_cpu")) {
			return StateValues.PROCESS_STATUS_WAIT_FOR_CPU;
		}
		return StateValues.PROCESS_STATUS_UNKNOWN;
	}
}