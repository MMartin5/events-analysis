package org.eclipse.tracecompass.incubator.coherence.core.newmodel;

import java.util.List;
import java.util.Objects;

import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;

public class FsmStateIncoherence {
	
	private final ITmfEvent fIncoherentEvent;
	private final String fScenarioAttribute;
	private final ITmfEvent fPrevCoherentEvent;
	private final String fLastCoherentStateName;
	private List<TmfXmlFsmTransition> fInferredTransitions;

	
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
