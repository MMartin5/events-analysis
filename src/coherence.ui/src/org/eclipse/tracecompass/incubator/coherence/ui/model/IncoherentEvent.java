package org.eclipse.tracecompass.incubator.coherence.ui.model;

import java.util.Set;

import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlStateTransition;
import org.eclipse.tracecompass.incubator.coherence.core.newmodel.TmfXmlFsmTransition;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.ITimeGraphEntry;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.TimeEvent;

/**
 * A class for incoherent events
 *
 * @author mmartin
 * @since 3.1
 *
 */
public class IncoherentEvent extends TimeEvent {

    /**
     * Static value for incoherent events
     */
    public static int INCOHERENT_VALUE = 20; // use WAIT_UNKNOWN value
    
    public static String INCOHERENT_MSG = "Incoherent";
    
    private String fIncoherence = "not set";

    /**
     * Constructor
     *
     * @param entry
     *              The associated entry to which the event belongs
     * @param time
     *              The time of the event
     * @param duration
     *              The duration of the event
     */
    public IncoherentEvent(ITimeGraphEntry entry, long time, long duration, TmfXmlFsmTransition transition) {
        super(entry, time, duration, INCOHERENT_VALUE);
    	fIncoherence = "should have taken a " + transition.toString();
    }
    
    public String getIncoherence() {
    	return fIncoherence;
    }

}
