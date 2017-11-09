package org.eclipse.tracecompass.incubator.coherence.core.newmodel;

import java.util.Objects;

import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlState;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlStateTransition;

public class TmfXmlFsmTransition {
	
	private final TmfXmlStateTransition fTransitionTo;
	private final TmfXmlState fFromState;
	private final String fEvent;
	
	public TmfXmlFsmTransition(TmfXmlStateTransition transitionTo, TmfXmlState fromState, String eventName) {
		fTransitionTo = transitionTo;
		fFromState = fromState;
		fEvent = eventName;
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(fTransitionTo.getTarget(), fFromState.toString(), fEvent);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof TmfXmlFsmTransition) {
			TmfXmlFsmTransition other = (TmfXmlFsmTransition) obj;
			if ((other.toString().equals(this.toString())) && (other.fEvent.equals(this.fEvent))) {
				return true;
			}
		}
		return false;
	}

	@Override
	public String toString() {
		return "TmfXmlFsmTransition to " + fTransitionTo.getTarget() + ", from " + fFromState.toString();
	}

	public TmfXmlStateTransition to() {
		return fTransitionTo;
	}
	
	public TmfXmlState from() {
		return fFromState;
	}
	
	public String getEvent() {
		return fEvent;
	}
	
}
