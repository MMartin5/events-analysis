package org.eclipse.tracecompass.incubator.trace.lostevents.core.trace;

import java.util.Map;

import org.eclipse.tracecompass.ctf.core.CTFStrings;
import org.eclipse.tracecompass.ctf.core.event.IEventDeclaration;
import org.eclipse.tracecompass.ctf.core.event.scope.IDefinitionScope;
import org.eclipse.tracecompass.ctf.core.event.scope.ILexicalScope;
import org.eclipse.tracecompass.ctf.core.event.types.IDefinition;
import org.eclipse.tracecompass.ctf.core.event.types.IntegerDeclaration;
import org.eclipse.tracecompass.ctf.core.event.types.IntegerDefinition;
import org.eclipse.tracecompass.ctf.core.event.types.StructDeclaration;
import org.eclipse.tracecompass.ctf.core.event.types.StructDefinition;
import org.eclipse.tracecompass.incubator.trace.lostevents.core.LostEventsStrings;
import org.eclipse.tracecompass.internal.ctf.core.event.EventDefinition;
import org.eclipse.tracecompass.internal.ctf.core.event.LostEventDeclaration;
import org.eclipse.tracecompass.lttng2.kernel.core.trace.LttngKernelTrace;
import org.eclipse.tracecompass.tmf.core.trace.ITmfContext;
import org.eclipse.tracecompass.tmf.ctf.core.context.CtfLocation;
import org.eclipse.tracecompass.tmf.ctf.core.context.CtfTmfContext;
import org.eclipse.tracecompass.tmf.ctf.core.event.CtfTmfEvent;

/**
 * Wrapper trace for inserting 'Lost event' manually
 * @author mmartin
 *
 */
public class LostEventsTrace extends LttngKernelTrace implements IDefinitionScope {
    private long fTsBegin;
    private long fTsEnd;
    private long fLostEvents;
    private long fPrevTs;
    private boolean needRewind; // true if we need to look at the previous event again

    CtfTmfEvent fLostEvent = null;

    public LostEventsTrace() {
        super();
        fTsBegin = 0;
        fTsEnd = 0;
        fLostEvents = 0;
        fPrevTs = 0;
        needRewind = false;

//        fLostTsBegin = (Long) fPacketContext.getAttributes().get(LostEventsStrings.LOST_EVENTS_BEGIN);
//        fLostTsEnd = (Long) fPacketContext.getAttributes().get(LostEventsStrings.LOST_EVENTS_END);
    }


    private EventDefinition createLostEvent(int targetId) {
        IEventDeclaration lostEventDeclaration = LostEventDeclaration.INSTANCE;
        StructDeclaration lostFields = lostEventDeclaration.getFields();
        // this is a hard coded map, we know it's not null
        IntegerDeclaration lostFieldsDecl = (IntegerDeclaration) lostFields.getField(CTFStrings.LOST_EVENTS_FIELD);
        if (lostFieldsDecl == null) {
            throw new IllegalStateException("Lost events count not declared!"); //$NON-NLS-1$
        }
        IntegerDeclaration lostEventsDurationDecl = (IntegerDeclaration) lostFields.getField(CTFStrings.LOST_EVENTS_DURATION);
        if (lostEventsDurationDecl == null) {
            throw new IllegalStateException("Lost events duration not declared!"); //$NON-NLS-1$
        }


        long lostEventsTimestamp = fTsBegin;
        long lostEventsDuration = fTsEnd - fTsBegin;


        IntegerDefinition lostDurationDef = new IntegerDefinition(lostFieldsDecl, null, CTFStrings.LOST_EVENTS_DURATION, lostEventsDuration);
        IntegerDefinition lostCountDef = new IntegerDefinition(lostEventsDurationDecl, null, CTFStrings.LOST_EVENTS_FIELD, fLostEvents);
        IntegerDefinition[] fields = new IntegerDefinition[] { lostCountDef, lostDurationDef };
        int cpu = targetId;
        return new EventDefinition(
                lostEventDeclaration,
                cpu,
                lostEventsTimestamp,
                null,
                null,
                null,
                null,
                new StructDefinition(
                        lostFields,
                        this, "fields", //$NON-NLS-1$
                        fields),
                        null /*fPacketContext*/);
    }

    @Override
    public synchronized CtfTmfEvent getNext(final ITmfContext context) {
        if (getUUID() == null) { // we need to check if fTrace is null, and getUUID returns null if fTrace == null
            return null;
        }
        CtfTmfEvent event = null;
        if (context instanceof CtfTmfContext) {
            if (context.getLocation() == null || CtfLocation.INVALID_LOCATION.equals(context.getLocation().getLocationInfo())) {
                return null;
            }
            CtfTmfContext ctfContext = (CtfTmfContext) context;
            event = ctfContext.getCurrentEvent();

            if (event != null) {
                // Try to get information about the lost event
                Map<String, Object> packetAttr = event.getPacketAttributes();
                if (packetAttr.containsKey(LostEventsStrings.LOST_EVENTS_BEGIN) && fTsBegin == 0) { // set only one time
                    Long begin = (Long) packetAttr.get(LostEventsStrings.LOST_EVENTS_BEGIN);
                    if (begin != null) {
                        fTsBegin = begin.longValue();
                    }
                    Long end = (Long) packetAttr.get(LostEventsStrings.LOST_EVENTS_END);
                    if (end != null) {
                        fTsEnd = end.longValue();
                    }
                    Long lostEvents = (Long) packetAttr.get(CTFStrings.EVENTS_DISCARDED);
                    if (lostEvents != null) {
                        fLostEvents = lostEvents.longValue();
                    }
                }

                long ts_begin = this.timestampCyclesToNanos(fTsBegin); // convert timestamp

                /* We need to check :
                 *  - needRewind so that we don't generate 'Lost event' indefinitely
                 *  - fLostEvents because we don't need the rest if there is no lost event
                 *  - fPrevTs to put the lost event (if it applies) at the right place
                 */
                if (!needRewind && (fLostEvents > 0) && (fPrevTs == 0 || fPrevTs == event.getTimestamp().getValue())
                        && (event.getTimestamp().getValue() >= ts_begin)) {
                    if (fLostEvent == null) { // create the lost event only once
                        int targetId = event.getCPU(); // arbitrary selection of the target cpu
                        EventDefinition lostEvent = createLostEvent(targetId);
                        fLostEvent = getEventFactory().createEvent(this, lostEvent, this.getPath());
                        fPrevTs = event.getTimestamp().getValue();
                    }
                    updateAttributes(context, event);
                    needRewind = true; // indicate a need to handle the current "real" event that would be erased by 'Lost event' otherwise
                    return fLostEvent; // return the 'Lost event' without calling advance() on the context
                }

                needRewind = false;
                updateAttributes(context, event);
                ctfContext.advance();
                ctfContext.increaseRank();
            }
        }

        return event;
    }



    @Override
    public ILexicalScope getScopePath() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public IDefinition lookupDefinition(String lookupPath) {
        // TODO Auto-generated method stub
        return null;
    }

}
