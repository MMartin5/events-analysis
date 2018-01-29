package org.eclipse.tracecompass.incubator.coherence.core.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNull;
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
        Map<String, Object> fieldPairs = new HashMap<>();
        for (String cond : conditions) {
        	TmfXmlTransitionValidator validator = testMap.get(cond);
        	ITmfXmlCondition xmlCond = validator.getCondition();
        	/*
        	 * If xmlCond is a TmfXmlTimestampCondition, we won't extract any useful information
        	 * so we don't need to consider this case
        	 */
        	if (xmlCond instanceof TmfXmlCondition) {
        		Map<String, Object> fieldsForCond = inferFromCondition(xmlCond, stateSystem, event, scenarioInfo);
        		for (String fieldName : fieldsForCond.keySet()) {
	        		if (fieldsForCond.containsKey(fieldName)) { // it could happen because the same event field could be used in several conditions
	    				// TODO handle this case
	    			}
	        		fieldPairs.put(fieldName, fieldsForCond.get(fieldName));
        		}
        	}
        }
    	// Construct fields with each pair and add them to the "content field" structure
		for (String fieldName : fieldPairs.keySet()) {
			TmfEventField field = new TmfEventField(fieldName, fieldPairs.get(fieldName), null);
			fields.add(field);
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
	private static Map<String, Object> inferFromCondition(ITmfXmlCondition xmlCond, ITmfStateSystem stateSystem, 
			ITmfEvent event, TmfXmlScenarioInfo scenarioInfo) {
		
		Map<String, Object> fields = new HashMap<>();
		List<ITmfXmlStateValue> stateValues = ((TmfXmlCondition) xmlCond).getStateValues();
		/* Get value according to what is needed for the condition to be true */
		for (ITmfXmlStateValue value : stateValues) { // add one event field for each attribute
			Map<String, Object> tentativeFields = new HashMap<>();
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
    				Pair<List<String>, List<Pair<String, Integer>>> res = getPathFromAttributes(attributes);
    				List<String> path = res.getFirst();
    				List<Pair<String, Integer>> resFields = res.getSecond();
    		
    				if (resFields.isEmpty()) {												// Case 4 (default): condition is not about an event field
    					continue;
    				}
    				else { // get value for case 3 : depends on the state value following this sequence of stateAttribute tags
    					String[] pattern = path.toArray(new String[path.size()]);
    					/* Get every possible path (where missing event field is a wildcard) */
    					List<Integer> quarks = stateSystem.getQuarks(pattern);
    					/* Get the following state value for comparison */
    					ITmfStateValue compValue = null;
						try {
							compValue = stateValue.getValue(event, scenarioInfo);
						} catch (AttributeNotFoundException e) {
							Activator.logError("Attribute not found while trying to get the value of inferred event", e); //$NON-NLS-1$
			                continue;
						}
						/* Try to find a match between a possible value and the comparison value */
    					for (Integer quark : quarks) {
    						ITmfStateValue currentValue = stateSystem.queryOngoingState(quark);
    						if (currentValue == compValue) { // we found a match for the desired value
    							for (Pair<String, Integer> resField : resFields) {
    								fieldName = resField.getFirst();
    								/* Find the missing field value in the matching path */
    								int fieldIndex = resField.getSecond();
        							fieldValue = stateSystem.getFullAttributePathArray(quark)[fieldIndex]; // FIXME several quarks could match => which one should we choose? (for now, we choose the first one)
        							tentativeFields.put(fieldName, fieldValue);
    							} 
    							break;
    						}
    					}
    				}
    			}
    			else { // get value for case 2
    				// field value is the other state value that we are trying to compare it to (2.1)
    				// or is the value of the preceding sequence of state attributes (2.2)
    				// or is the value of the preceding field tag (2.3)
    				
    				if (stateValues.size() == 2) {											// case 2.1
						try {
							/* (1 - index) because there is only 2 state values 
							 * so it's either the preceding if event field is the second 
							 * or the following if event field is the first */
							fieldValue = stateValues.get(1 - stateValues.indexOf(value)).getValue(event, scenarioInfo);
							tentativeFields.put(fieldName, fieldValue);
						} catch (AttributeNotFoundException e) {
							Activator.logError("Attribute not found while trying to get the value of inferred event", e); //$NON-NLS-1$
			                continue;
						}
    				}
    				else {																	// case 2.2
	    				/* Look for the quark, starting from root and descending until the last attribute is reached */
						int quark = IXmlStateSystemContainer.ROOT_QUARK;
						for (ITmfXmlStateAttribute attr : value.getAttributes()) {
							quark = attr.getAttributeQuark(event, quark, scenarioInfo);
						}
						/* Find value by querying the state system */
						try {
							fieldValue = stateSystem.querySingleState(event.getTimestamp().getValue(), quark).getValue();
							tentativeFields.put(fieldName, fieldValue);
						} catch (StateSystemDisposedException e) {
							Activator.logError("State system disposed while trying to get the value of inferred event", e); //$NON-NLS-1$
			                continue;
						}
    				}
					
    				// case 2.3
    				// TODO can we retrieve it? because it depends on a field tag value, which is lost too (confirm?)
    			}
			}
			else { // get value for case 1 : the state value following this field tag
				try {
					fieldValue = stateValues.get(stateValues.indexOf(value) + 1).getValue(event, scenarioInfo);
					tentativeFields.put(fieldName, fieldValue);
				} catch (AttributeNotFoundException e) {
					Activator.logError("Attribute not found while trying to get the value of inferred event", e); //$NON-NLS-1$
	                continue;
				}
			}
			for (String tentativeName : tentativeFields.keySet()) {
				if (fields.containsKey(tentativeName)) { // it could happen because the same event field could be used several times in this condition
					// TODO handle this case
				}
				fields.put(tentativeName, tentativeFields.get(tentativeName));
			}
		}
		return fields;
	}
	
	/**
	 *  Create a state system path from attribute's names, with unknown event field values replaced by wildcard '*' 
	 */
	private static Pair<List<String>, List<Pair<String, Integer>>> getPathFromAttributes(List<ITmfXmlStateAttribute> attributes) {
		List<String> path = new ArrayList<>();
		List<Pair<String, Integer>> fields = new ArrayList<>();
		for (ITmfXmlStateAttribute attribute : attributes) {
			TmfXmlStateAttribute stateAttribute = (TmfXmlStateAttribute) attribute;
			if (stateAttribute.getType() == TmfXmlStateAttribute.StateAttributeType.EVENTFIELD) { // handle event field
				// FIXME could be several event field (possibly one per attribute)
				fields.add(new Pair<String, Integer>(stateAttribute.getName(), attributes.indexOf(stateAttribute))); 
				path.add("*");
			}
			else if (stateAttribute.getType() == TmfXmlStateAttribute.StateAttributeType.LOCATION) {
				for (TmfXmlLocation location : stateAttribute.getContainer().getLocations()) {
					if (location.getId().equals(stateAttribute.getName())) { // look for the location object
						List<ITmfXmlStateAttribute> locationAttributes = location.getPath();
						Pair<List<String>, List<Pair<String, Integer>>> locationMap = getPathFromAttributes(locationAttributes);
						path.addAll(locationMap.getFirst());
						if (!locationMap.getSecond().isEmpty()) {
							fields.addAll(locationMap.getSecond()); 
						}
					}
				}
			}
			else {
				path.add(stateAttribute.getName());
			}
		}
		return new Pair<List<String>, List<Pair<String, Integer>>>(path, fields);
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
