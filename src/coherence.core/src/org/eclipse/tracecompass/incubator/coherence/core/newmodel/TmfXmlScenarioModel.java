package org.eclipse.tracecompass.incubator.coherence.core.newmodel;

import java.util.List;

import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;

public abstract class TmfXmlScenarioModel {
	
	public abstract List<String> getAttributesForEvent(ITmfEvent event);

}
