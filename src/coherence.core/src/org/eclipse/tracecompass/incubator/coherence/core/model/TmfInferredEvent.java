package org.eclipse.tracecompass.incubator.coherence.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.tracecompass.ctf.core.event.IEventDefinition;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlStateValue.TmfXmlStateValueBase;
import org.eclipse.tracecompass.incubator.coherence.core.newmodel.FsmStateIncoherence;
import org.eclipse.tracecompass.incubator.coherence.core.newmodel.TmfXmlFsmTransition;
import org.eclipse.tracecompass.incubator.coherence.core.readwrite.TmfXmlReadWriteStateValue;
import org.eclipse.tracecompass.tmf.core.event.ITmfEventField;
import org.eclipse.tracecompass.tmf.core.event.ITmfEventType;
import org.eclipse.tracecompass.tmf.core.event.TmfEvent;
import org.eclipse.tracecompass.tmf.core.event.TmfEventField;
import org.eclipse.tracecompass.tmf.core.event.TmfEventType;
import org.eclipse.tracecompass.tmf.core.event.aspect.TmfCpuAspect;
import org.eclipse.tracecompass.tmf.core.timestamp.ITmfTimestamp;
import org.eclipse.tracecompass.tmf.core.timestamp.TmfTimestamp;
import org.eclipse.tracecompass.tmf.core.trace.ITmfContext;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;
import org.eclipse.tracecompass.tmf.ctf.core.event.CtfTmfEventType;

public class TmfInferredEvent extends TmfEvent {
	
	/* Interval inside which the event could have happen */
	private final ITmfTimestamp fStart;
	private final ITmfTimestamp fEnd;
	/* Rank relative to the position of this event in the sequence of inferred events between last known coherent event and incoherent event */
	private final long fLocalRank;
	private Integer fCpu;
	
	public static TmfInferredEvent create(ITmfTrace trace, 
			FsmStateIncoherence incoherence, 
			TmfXmlFsmTransition inferredTransition, 
			long localRank, 
			int nbInferred, 
			Map<String, TmfXmlTransitionValidator> testMap) {
		ITmfTimestamp tsStart = incoherence.getPrevCoherentEvent().getTimestamp();
		ITmfTimestamp tsEnd = incoherence.getIncoherentEvent().getTimestamp();
		// set the timestamp to be in the middle of the possible interval + some factor given the local rank
		ITmfTimestamp ts = TmfTimestamp.create(
				tsStart.getValue() + ((tsEnd.getValue() - tsStart.getValue()) / (nbInferred + 1)) * localRank, 
				tsStart.getScale());
		Integer cpu = TmfTraceUtils.resolveIntEventAspectOfClassForEvent(incoherence.getIncoherentEvent().getTrace(),
                TmfCpuAspect.class, incoherence.getIncoherentEvent());
        if (cpu == null) {
        	cpu = IEventDefinition.UNKNOWN_CPU;
        }
        
        List<TmfEventField> fields = new ArrayList<>();
        
        /* use the conditions in the inferred transition */
        String conditionStr = inferredTransition.to().getCondition();
        String[] conditions = conditionStr.split(":");
        for (String cond : conditions) {
        	TmfXmlTransitionValidator validator = testMap.get(cond);
        	// get XmlTransition
        	ITmfXmlCondition xmlCond = validator.getCondition();
        	/* extract state attributes
        	 * if xmlCond is a TmfXmlTimestampCondition, we won't extract any useful information
        	 * so we don't need to consider this case
        	 */
        	if (xmlCond instanceof TmfXmlCondition) {
        		List<ITmfXmlStateValue> stateValues = ((TmfXmlCondition) xmlCond).getStateValues();
        		// get value according to what is needed for the condition to be true
        		for (ITmfXmlStateValue value : stateValues) { // add one event field for each attribute
        			/* Extract field name, then associate it with a field value */
        			// Case 1: condition is a field
        			TmfXmlStateValue stateValue = (TmfXmlStateValue) value;
    				String eventField = stateValue.getEventField(); // not null in case of <field ..>
    				Object fieldValue = null;
    				if (eventField == null) {
					// Case 2: condition is a state value with type eventField
    					// TODO check that every sub-case is covered
        				TmfXmlStateValueBase base = stateValue.getBaseStateValue();
            			if (base instanceof TmfXmlReadWriteStateValue.TmfXmlStateValueEventField) {
            				eventField = ((TmfXmlReadWriteStateValue.TmfXmlStateValueEventField) base).getFieldName();
            			}        				
            			if (eventField == null) {
    				// Case 3: condition is a state attribute sequence with a type eventField
	        				List<ITmfXmlStateAttribute> attributes = stateValue.getAttributes();
	        				for (ITmfXmlStateAttribute stateAttribute : attributes) {
	        					TmfXmlStateAttribute attr = (TmfXmlStateAttribute) stateAttribute;
	        					// FIXME we need to deal with type location, where the event field could be inside of the location definition
	    	    				if (attr.getType() == TmfXmlStateAttribute.StateAttributeType.EVENTFIELD) {
	    	    					// FIXME could be several event field (possibly one per attribute)
	    	    					eventField = attr.getName();	
	    	    				}
	        				}
    				// Case 4 (default): condition is not about an event field 
	        				if (eventField == null) {
	        					continue;
	        				}
	        				else { // get value for case 3
	            				// field value is the state value following this stateAttribute tag
	        					// TODO
	        					System.out.println("debug");
	        					
	        				}
            			}
            			else { // get value for case 2
            				// field value is the other state value that we are trying to compare it to
            				// or is the value of the preceding sequence of state attributes
            				// or is the value of the preceding field tag
            				// TODO
            				System.out.println("debug");
            				
            			}
    				}
    				else { // get value for case 1
    					// field value is the state value following this field tag
    					// TODO
    					System.out.println("debug");
    				}
    				
        			TmfEventField field = new TmfEventField(eventField, fieldValue, null);
        			fields.add(field);
        		}
        	}
        }
		TmfEventField content = new TmfEventField(ITmfEventField.ROOT_FIELD_ID, null, fields.toArray(new TmfEventField[fields.size()]));
		TmfEventType type = new CtfTmfEventType(inferredTransition.getEvent(), content);
		return new TmfInferredEvent(trace, ITmfContext.UNKNOWN_RANK, localRank, ts, tsStart, tsEnd, type, content, cpu);
	}
	
	private TmfInferredEvent(final ITmfTrace trace,
            final long rank,
            final long localRank,
            final ITmfTimestamp ts,
            final ITmfTimestamp tsStart,
            final ITmfTimestamp tsEnd,
            final ITmfEventType type,
            final ITmfEventField content, 
            Integer cpu) {
		super(trace, rank, ts, type, content);
		
		fStart = tsStart;
		fEnd = tsEnd;
		fLocalRank = localRank;
		fCpu = cpu;
	}
	
	@Override
	public boolean equals(Object obj) {
		TmfInferredEvent other = (TmfInferredEvent) obj;
		if ((other.getName().equals(this.getName())) &&
				(other.getStartTime() == this.getStartTime()) &&
				(other.getEndTime() == this.getEndTime()) &&
				(other.getLocalRank() == this.getLocalRank())) {
			return true;
		}
		return false;
	}
	
	public boolean greaterThan(TmfInferredEvent other) {
		if ((this.getStartTime() > other.getEndTime()) ||
				((this.getStartTime() == other.getStartTime()) && (this.getEndTime() == other.getEndTime()) && 
						(this.getLocalRank() > other.getLocalRank()))) {
			return true;
		}
		return false;
	}
	
	public long getStartTime() {
		return fStart.getValue();
	}
	
	public long getEndTime() {
		return fEnd.getValue();
	}
	
	public long getLocalRank() {
		return fLocalRank;
	}
	
	public Integer getCpu() {
		return fCpu;
	}

}
