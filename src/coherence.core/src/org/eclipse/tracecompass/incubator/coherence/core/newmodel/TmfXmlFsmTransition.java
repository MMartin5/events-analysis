package org.eclipse.tracecompass.incubator.coherence.core.newmodel;

import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlState;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlStateTransition;

public class TmfXmlFsmTransition {
	
	private final TmfXmlStateTransition fTransitionTo;
	private final TmfXmlState fFromState;
	
	public TmfXmlFsmTransition(TmfXmlStateTransition transitionTo, TmfXmlState fromState) {
		fTransitionTo = transitionTo;
		fFromState = fromState;
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
}
