package org.eclipse.tracecompass.incubator.coherence.core.newmodel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.eclipse.jdt.annotation.Nullable;
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
	private List<TmfXmlFsmTransition> fInferredTransitions = new ArrayList<>();
	/* The list of inferred events computed for this incoherence, and the associated inferred transition (as key) */
	private Map<TmfXmlFsmTransition, TmfInferredEvent> fInferredEvents = new HashMap<>();

	
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

	public Collection<TmfInferredEvent> getInferredEvents() {
		return fInferredEvents.values();
	}
	
	public Map<TmfXmlFsmTransition, ITmfEvent> getInferredEventsMap() {
		Map<TmfXmlFsmTransition, ITmfEvent> map = new HashMap<>();
		for (TmfXmlFsmTransition transition : fInferredTransitions) {
			map.put(transition, getEvent(transition));
		}
		return map;
	}
	
	public void setInferences(Map<TmfXmlFsmTransition, TmfInferredEvent> localEventsMap) {
		fInferredEvents = localEventsMap;	
	}
	
	/**
	 * Find the event associated with a given inferred transition
	 * 
	 * The event could either be an inferred event, or the
	 * incoherent event.
	 * 
	 * @param transition
	 * 			An inferred transition
	 * @return
	 * 			The event associated with the transition
	 */
	public ITmfEvent getEvent(TmfXmlFsmTransition transition) {
		if (fInferredTransitions.contains(transition)) {
			TmfInferredEvent inferredEvent = fInferredEvents.get(transition);
			/* if the transition if among the list of inferred transitions, but
			 * not in the map of inferred events, it means that no inferred event
			 * is associated with this transition, and so this is the transition
			 * associated with the incoherent event
			 */
			return (inferredEvent != null) ? inferredEvent : fIncoherentEvent;
		}
		return null;
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
