package org.eclipse.tracecompass.incubator.coherence.core.newmodel;

import java.util.List;
import java.util.Objects;

import org.eclipse.tracecompass.incubator.coherence.core.model.TmfInferredEvent;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;

public class FsmStateIncoherence {
	
	/* The incoherent event */
	private final ITmfEvent fIncoherentEvent;
	/* The attribute of the scenario on which the incoherent event was detected */
	private final String fScenarioAttribute;
	/* The last coherent event before the incoherent one */
	private final ITmfEvent fPrevCoherentEvent;
	/* The name of the state the scenario was in when the coherent event happened */
	private final String fLastCoherentStateName;
	/* The list of inferred transitions computed for this incoherence */
	private List<TmfXmlFsmTransition> fInferredTransitions;
	/* The list of inferred events computed for this incoherence */
	private List<TmfInferredEvent> fInferredEvents;

	
	public FsmStateIncoherence(ITmfEvent incoherentEvent, String scenarioAttribute, ITmfEvent prevCoherentEvent, String currentStateName) {
		fIncoherentEvent = incoherentEvent;
		fScenarioAttribute = scenarioAttribute;
		fPrevCoherentEvent = prevCoherentEvent;
		fLastCoherentStateName = currentStateName;
	}

	
	public ITmfEvent getIncoherentEvent() {
		return fIncoherentEvent;
	}

	public String getScenarioAttribute() {
		return fScenarioAttribute;
	}

	public ITmfEvent getPrevCoherentEvent() {
		return fPrevCoherentEvent;
	}
	
	public String getLastCoherentStateName() {
		return fLastCoherentStateName;
	}

	public List<TmfXmlFsmTransition> getInferredTransitions() {
		return fInferredTransitions;
	}
	
	public void setInferredTransitions(List<TmfXmlFsmTransition> inferredTransitions) {
		fInferredTransitions = inferredTransitions;
	}

	public List<TmfInferredEvent> getInferredEvents() {
		return fInferredEvents;
	}
	
	public void setInferredEvents(List<TmfInferredEvent> localEventsList) {
		fInferredEvents = localEventsList;		
	}

	/**
	 * Two FsmStateIncoherence are equal if the incoherent event is the same and it happened on the same scenario
	 * (because no event can be twice incoherent for a given scenario)
	 */
	@Override
	public boolean equals(Object obj) {
		if (! (obj instanceof FsmStateIncoherence)) {
			return false;
		}
		
		FsmStateIncoherence other = (FsmStateIncoherence) obj;
		if ((other.fIncoherentEvent == fIncoherentEvent) && (other.fScenarioAttribute == fScenarioAttribute)) {
			return true;
		}
		
		return false;
	}


	@Override
	public int hashCode() {
		return Objects.hash(fIncoherentEvent, fScenarioAttribute);
	}

}
