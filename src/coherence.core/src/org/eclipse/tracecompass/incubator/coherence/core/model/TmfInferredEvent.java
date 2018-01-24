package org.eclipse.tracecompass.incubator.coherence.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.tracecompass.ctf.core.event.IEventDefinition;
import org.eclipse.tracecompass.incubator.coherence.core.Activator;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlStateValue.TmfXmlStateValueBase;
import org.eclipse.tracecompass.incubator.coherence.core.newmodel.FsmStateIncoherence;
import org.eclipse.tracecompass.incubator.coherence.core.newmodel.TmfXmlFsmTransition;
import org.eclipse.tracecompass.incubator.coherence.core.readwrite.TmfXmlReadWriteStateValue;
import org.eclipse.tracecompass.internal.tmf.analysis.xml.core.module.IXmlStateSystemContainer;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystem;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateSystemDisposedException;
import org.eclipse.tracecompass.statesystem.core.statevalue.ITmfStateValue;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
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
import org.eclipse.tracecompass.tmf.core.util.Pair;
import org.eclipse.tracecompass.tmf.ctf.core.event.CtfTmfEventType;

public class TmfInferredEvent extends TmfEvent {
	
	/* Interval inside which the event could have happen */
	private final ITmfTimestamp fStart;
	private final ITmfTimestamp fEnd;
	/* Rank relative to the position of this event in the sequence of inferred events between last known coherent event and incoherent event */
	private final long fLocalRank;
	private Integer fCpu;
	
	/**
	 * Instantiate a new inferred event
	 * 
	 * @param trace
	 * 				The trace
	 * @param incoherence
	 * 				The incoherence for which we want to make inferences
	 * @param inferredTransition
	 * 				The inferred transition associated with the event
	 * @param localRank
	 * 				The rank of the event (position between last coherent event and incoherent event)
	 * @param nbInferred
	 * 				The total number of inferred events for the incoherence
	 * @param testMap	
	 * 				The test map
	 * @param stateSystem
	 * 				The state system
	 * @param scenarioInfo
	 * 				The information for the scenario where the incoherence was detected
	 * 
	 * @return
	 * 				The inferred event
	 */
	public static TmfInferredEvent create(ITmfTrace trace, 
			FsmStateIncoherence incoherence, 
			TmfXmlFsmTransition inferredTransition, 
			long localRank, 
			int nbInferred, 
			Map<String, TmfXmlTransitionValidator> testMap, 
			ITmfStateSystem stateSystem, 
			TmfXmlScenarioInfo scenarioInfo) {
		
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
        TmfEventField content = findContent(inferredTransition, testMap, stateSystem, incoherence.getPrevCoherentEvent(), 
        		scenarioInfo);
        TmfEventType type = new CtfTmfEventType(inferredTransition.getEvent(), content);
        
		return new TmfInferredEvent(trace, ITmfContext.UNKNOWN_RANK, localRank, ts, tsStart, tsEnd, type, content, cpu);
	}
	
	/**
	 * Compute the content of an inferred event, given the associated inferred transition
	 * 
	 * @param inferredTransition
	 * @param testMap
	 * @param stateSystem
	 * @param event
	 * @param scenarioInfo
	 * 
	 * @return
 * 					The content of the inferred event, as an event field 
	 */
	private static TmfEventField findContent(TmfXmlFsmTransition inferredTransition, 
			Map<String, TmfXmlTransitionValidator> testMap, 
			ITmfStateSystem stateSystem, 
			ITmfEvent event, 
			TmfXmlScenarioInfo scenarioInfo) {
		
		List<TmfEventField> fields = new ArrayList<>();
        /* Get the conditions in the inferred transition */
        String conditionStr = inferredTransition.to().getCondition();
        String[] conditions = conditionStr.split(":");
        for (String cond : conditions) {
        	TmfXmlTransitionValidator validator = testMap.get(cond);
        	ITmfXmlCondition xmlCond = validator.getCondition();
        	/*
        	 * If xmlCond is a TmfXmlTimestampCondition, we won't extract any useful information
        	 * so we don't need to consider this case
        	 */
        	if (xmlCond instanceof TmfXmlCondition) {
        		List<Pair<String, Object>> fieldPairs = inferFromCondition(xmlCond, stateSystem, event, scenarioInfo);
        		// Construct fields with each pair and add them to the "content field" structure
    			for (Pair<String, Object> fieldPair : fieldPairs) {
	    			TmfEventField field = new TmfEventField(fieldPair.getFirst(), fieldPair.getSecond(), null);
	    			fields.add(field);
    			}
        	}
        }
        
		return new TmfEventField(ITmfEventField.ROOT_FIELD_ID, null, fields.toArray(new TmfEventField[fields.size()]));
	}
	
	/**
	 * Extract information from the state attributes of this XML condition
	 * We use the fact that if we know that a transition is taken, then it means
	 * that the condition labeling this transition is true.
	 * 
	 * @param xmlCond
	 * 				The XML condition that we know is true
	 * @param stateSystem
	 * @param event
	 * @param scenarioInfo
	 * 
	 * @return
	 * 				A list of pairs (field name, field value)
	 */
	private static List<Pair<String, Object>> inferFromCondition(ITmfXmlCondition xmlCond, ITmfStateSystem stateSystem, 
			ITmfEvent event, TmfXmlScenarioInfo scenarioInfo) {
		
		List<Pair<String, Object>> fields = new ArrayList<>();
		List<ITmfXmlStateValue> stateValues = ((TmfXmlCondition) xmlCond).getStateValues();
		/* Get value according to what is needed for the condition to be true */
		for (ITmfXmlStateValue value : stateValues) { // add one event field for each attribute
			/* Extract field name, then associate it with a field value */
			TmfXmlStateValue stateValue = (TmfXmlStateValue) value;							// Case 1: condition is a field								
			String fieldName = stateValue.getEventField(); // not null in case of XML tag <field ..>
			Object fieldValue = null;
			if (fieldName == null) {														// Case 2: condition is a state value with type eventField
				// TODO check that every sub-case is covered
				TmfXmlStateValueBase base = stateValue.getBaseStateValue();
    			if (base instanceof TmfXmlReadWriteStateValue.TmfXmlStateValueEventField) {
    				fieldName = ((TmfXmlReadWriteStateValue.TmfXmlStateValueEventField) base).getFieldName(); // not null in case of tag <stateValue type="eventField" ...>
    			}        				
    			if (fieldName == null) {													// Case 3: condition is a state attribute sequence with a type eventField
    				List<ITmfXmlStateAttribute> attributes = stateValue.getAttributes();
    				String[] pattern = new String[attributes.size()];
    				int fieldIndex = 0;
    				/* Create a state system path from attribute's names, with unknown event field values replaced by wildcard '*' */
    				for (ITmfXmlStateAttribute stateAttribute : attributes) {
    					TmfXmlStateAttribute attr = (TmfXmlStateAttribute) stateAttribute;
    					// FIXME we need to deal with type location, where the event field could be inside of the location definition
	    				if (attr.getType() == TmfXmlStateAttribute.StateAttributeType.EVENTFIELD) { // handle event field
	    					// FIXME could be several event field (possibly one per attribute)
	    					fieldName = attr.getName();
	    					fieldIndex = attributes.indexOf(attr); 
	    					pattern[fieldIndex] = "*";
	    				}
	    				else {
	    					pattern[attributes.indexOf(attr)] = attr.getName();
	    				}
    				}
			 
    				if (fieldName == null) {												// Case 4 (default): condition is not about an event field
    					continue;
    				}
    				else { // get value for case 3 : depends on the state value following this sequence of stateAttribute tags
    					/* Get every possible path (where missing event field is a wildcard) */
    					List<Integer> quarks = stateSystem.getQuarks(pattern);
    					/* Get the following state value for comparison */
    					ITmfStateValue compValue = null;
						try {
							compValue = stateValues.get(stateValues.indexOf(value) + 1).getValue(event, scenarioInfo);
						} catch (AttributeNotFoundException e) {
							Activator.logError("Attribute not found while trying to get the value of inferred event", e); //$NON-NLS-1$
			                continue;
						}
						/* Try to find a match between a possible value and the comparison value */
    					for (Integer quark : quarks) {
    						ITmfStateValue currentValue = stateSystem.queryOngoingState(quark);
    						if (currentValue == compValue) { // we found a match for the desired value
    							/* Find the missing field value in the matching path */
    							fieldValue = stateSystem.getFullAttributePathArray(quark)[fieldIndex];
    							break;
    						}
    					}
    				}
    			}
    			else { // get value for case 2
    				// field value is the other state value that we are trying to compare it to
    				// or is the value of the preceding sequence of state attributes
    				// or is the value of the preceding field tag
    				/* Look for the quark, starting from root and descending until the last attribute is reached */
					int quark = IXmlStateSystemContainer.ROOT_QUARK;
					for (ITmfXmlStateAttribute attr : value.getAttributes()) {
						quark = attr.getAttributeQuark(event, quark, scenarioInfo);
					}
					/* Find value by querying the state system */
					try {
						fieldValue = stateSystem.querySingleState(event.getTimestamp().getValue(), quark).getValue();
					} catch (StateSystemDisposedException e) {
						Activator.logError("State system disposed while trying to get the value of inferred event", e); //$NON-NLS-1$
		                continue;
					}
    			}
			}
			else { // get value for case 1 : the state value following this field tag
				try {
					fieldValue = stateValues.get(stateValues.indexOf(value) + 1).getValue(event, scenarioInfo);
				} catch (AttributeNotFoundException e) {
					Activator.logError("Attribute not found while trying to get the value of inferred event", e); //$NON-NLS-1$
	                continue;
				}
			}
			fields.add(new Pair<String, Object>(fieldName, fieldValue));
		}
		return fields;
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
