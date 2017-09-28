package org.eclipse.tracecompass.incubator.coherence.ui.model;

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
    public static int INCOHERENT_VALUE = 7;

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
    public IncoherentEvent(ITimeGraphEntry entry, long time, long duration) {
        super(entry, time, duration, INCOHERENT_VALUE);
    }

}
