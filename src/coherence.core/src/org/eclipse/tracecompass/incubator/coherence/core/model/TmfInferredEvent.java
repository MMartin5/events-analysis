package org.eclipse.tracecompass.incubator.coherence.core.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelAnalysisEventLayout;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelTrace;
import org.eclipse.tracecompass.ctf.core.event.IEventDefinition;
import org.eclipse.tracecompass.incubator.coherence.core.Activator;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlStateValue.TmfXmlStateValueBase;
import org.eclipse.tracecompass.incubator.coherence.core.newmodel.FsmStateIncoherence;
import org.eclipse.tracecompass.incubator.coherence.core.newmodel.MultipleInference;
import org.eclipse.tracecompass.incubator.coherence.core.newmodel.TmfXmlFsmTransition;
import org.eclipse.tracecompass.incubator.coherence.core.readwrite.TmfXmlReadWriteStateValue;
import org.eclipse.tracecompass.internal.analysis.os.linux.core.kernel.Attributes;
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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;

public class TmfInferredEvent extends TmfEvent {
	
	/* Interval inside which the event could have happen */
	private final ITmfTimestamp fStart;
	private final ITmfTimestamp fEnd;
	/* Rank relative to the position of this event in the sequence of inferred events between last known coherent event and incoherent event */
	private final long fLocalRank;
	private Integer fCpu;
	
	private final boolean fIsMulti;
	private Map<ITmfEventField, MultipleInference> fMultiValues;
	
	
	public static int MULTI_VALUE = -15;
	
	private static final String WILDCARD = "*"; //$NON-NLS-1$
	
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
        
        IKernelAnalysisEventLayout layout = ((IKernelTrace) trace).getKernelEventLayout();
        
        SetMultimap<String, TmfEventField> contentCandidates = findContent(inferredTransition, testMap, stateSystem, 
        		incoherence.getPrevCoherentEvent(), scenarioInfo, layout);
        boolean multi = false;
        List<TmfEventField> fields = new ArrayList<>();
        Map<ITmfEventField, MultipleInference> multiValues = new HashMap<>();
        for (String fieldName : contentCandidates.keySet()) {
        	Set<TmfEventField> candidateFields = contentCandidates.get(fieldName);
        	TmfEventField choice;
        	if (candidateFields.size() > 1) {
        		choice = new TmfEventField(fieldName, MULTI_VALUE, null);
        		multi = true;
        		multiValues.put(choice, new MultipleInference(new ArrayList<>(candidateFields)));
        	}
        	else {
        		choice = candidateFields.iterator().next(); // get user choice or get the first and only element
        	}
        	fields.add(choice);
        }        
        TmfEventField content = new TmfEventField(ITmfEventField.ROOT_FIELD_ID, null, fields.toArray(new TmfEventField[fields.size()]));
        TmfEventType type = new CtfTmfEventType(inferredTransition.getEvent(), content);
        
		return new TmfInferredEvent(trace, ITmfContext.UNKNOWN_RANK, localRank, ts, tsStart, tsEnd, type, content, cpu, multi, multiValues);
	}

	/**
	 * Compute the content of an inferred event, given the associated inferred transition
	 * 
	 * @param inferredTransition
	 * @param testMap
	 * @param stateSystem
	 * @param event
	 * @param scenarioInfo
	 * @param layout 
	 * 
	 * @return
 * 					The content of the inferred event, as an event field 
	 */
	private static SetMultimap<String, TmfEventField> findContent(TmfXmlFsmTransition inferredTransition, 
			Map<String, TmfXmlTransitionValidator> testMap, 
			ITmfStateSystem stateSystem, 
			ITmfEvent event, 
			TmfXmlScenarioInfo scenarioInfo, 
			IKernelAnalysisEventLayout layout) {
		
		SetMultimap<String, TmfEventField> fields = HashMultimap.create();
		SetMultimap<String, Object> candidateValues = HashMultimap.create();
		Map<String, Object> certainValues = new HashMap<>();
		
		/* Add the fields that can be inferred from the event name */
		candidateValues.putAll(inferFromEvent(event.getName(), layout, stateSystem));
		
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
        		SetMultimap<String, Object> fieldsForCond = inferFromCondition(xmlCond, stateSystem, event, scenarioInfo);
        		for (String fieldName : fieldsForCond.keySet()) {
        			if (fieldsForCond.get(fieldName).size() == 1) { // there is exactly one possible value
        				certainValues.put(fieldName, fieldsForCond.get(fieldName).iterator().next()); // remember this certain value
        			}
        			else { // there is multiple values, add them all
        				candidateValues.put(fieldName, fieldsForCond.get(fieldName));
        			}
        		}
        	}
        }
        
        // Construct fields with each pair and add them to the "content field" structure
        for(Entry<String, Object> entry : candidateValues.entries()) {
        	TmfEventField field = new TmfEventField(entry.getKey(), entry.getValue(), null);
			fields.put(entry.getKey(), field);
        }
        for(Entry<String, Object> entry : certainValues.entrySet()) {
        	TmfEventField field = new TmfEventField(entry.getKey(), entry.getValue(), null);
        	fields.removeAll(entry.getKey());
			fields.put(entry.getKey(), field);
        }
		
		return fields;
	}
	
	/**
	 * 
	 * FIXME 
	 * instead of doing this hardcoded stuff, we should create a map of 
	 * (event names, associated event definition), that would be populated
	 * when a ctf trace is first read (ie. when each ctf tmf event is being 
	 * created)
	 * but we need access to the event definition in the ctf tmf event, which
	 * is not possible for now
	 * 
	 * @param eventName
	 * @param layout
	 * @param stateSystem
	 * @return
	 */
	private static SetMultimap<String, Object> inferFromEvent(String eventName, IKernelAnalysisEventLayout layout, ITmfStateSystem stateSystem) {
		SetMultimap<String, Object> candidateFields = HashMultimap.create();
		
		if (eventName.equals(layout.eventSchedSwitch())) {
			// @see ControlFlowView.buildEntryList
			for (Integer quark : stateSystem.getQuarks(Attributes.THREADS, WILDCARD)) { // add every possible value for this field (every existing tid)
				String threadAttributeName = stateSystem.getAttributeName(quark);
                Pair<Integer, Integer> entryKey = Attributes.parseThreadAttributeName(threadAttributeName);
                long threadId = entryKey.getFirst();
				candidateFields.put(layout.fieldPrevTid(), threadId);
				candidateFields.put(layout.fieldNextTid(), threadId);
			}
			// FIXME temporary fix hardcoded
			candidateFields.put(layout.fieldPrevComm(), "unknown");
			candidateFields.put(layout.fieldPrevState(), 0l);
			candidateFields.put(layout.fieldPrevPrio(), 0l);
			candidateFields.put(layout.fieldNextComm(), "unknown");
			candidateFields.put(layout.fieldNextPrio(), 0l);
													
		}
		
		// TODO handle other event names
				
		return candidateFields;		
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
	private static SetMultimap<String, Object> inferFromCondition(ITmfXmlCondition xmlCond, ITmfStateSystem stateSystem, 
			ITmfEvent event, TmfXmlScenarioInfo scenarioInfo) {
		
		SetMultimap<String, Object> candidateFields = HashMultimap.create();
		List<ITmfXmlStateValue> stateValues = ((TmfXmlCondition) xmlCond).getStateValues();
		/* Get value according to what is needed for the condition to be true */
		for (ITmfXmlStateValue value : stateValues) { // add one event field for each attribute
			/* Extract field name, then associate it with a field value */
			TmfXmlStateValue stateValue = (TmfXmlStateValue) value;							// Case 1: condition is a field
			String fieldName = stateValue.getEventField(); // not null in case of XML tag <field ..>
			Object fieldValue = null;
			if (fieldName == null) {														// Case 2: condition is a state value with type eventField
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
        							fieldValue = Long.valueOf(stateSystem.getFullAttributePathArray(quark)[fieldIndex]); // convert to Long
        							candidateFields.put(fieldName, fieldValue);
    							} 
    							// we do not break from the loop because there can be more than one match
    						}
    					}
    				}
    			}
    			else { // get value for case 2
    				// field value is the other state value that we are trying to compare it to (2.1)
    				// or is the value of the preceding sequence of state attributes (2.2)
    				// or is the value of the preceding field tag (2.3) but in this case we do not retrieve
    				// it because it depends on a field tag value, which is lost too
    				
    				if (stateValues.size() == 2) {											// case 2.1
						try {
							/* (1 - index) because there is only 2 state values 
							 * so it's either the preceding if event field is the second 
							 * or the following if event field is the first */
							fieldValue = stateValues.get(1 - stateValues.indexOf(value)).getValue(event, scenarioInfo);
							candidateFields.put(fieldName, fieldValue);
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
							fieldValue = ((Integer) stateSystem.querySingleState(event.getTimestamp().getValue(), quark).getValue()).longValue();
							candidateFields.put(fieldName, fieldValue);
						} catch (StateSystemDisposedException e) {
							Activator.logError("State system disposed while trying to get the value of inferred event", e); //$NON-NLS-1$
			                continue;
						}
    				}
    			}
			}
			else { // get value for case 1 : the state value following this field tag
				try {
					fieldValue = stateValues.get(stateValues.indexOf(value) + 1).getValue(event, scenarioInfo);
					candidateFields.put(fieldName, fieldValue);
				} catch (AttributeNotFoundException e) {
					Activator.logError("Attribute not found while trying to get the value of inferred event", e); //$NON-NLS-1$
	                continue;
				}
			}
		}
		return candidateFields;
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
	
	protected TmfInferredEvent(final ITmfTrace trace,
            final long rank,
            final long localRank,
            final ITmfTimestamp ts,
            final ITmfTimestamp tsStart,
            final ITmfTimestamp tsEnd,
            final ITmfEventType type,
            final ITmfEventField content, 
            Integer cpu, 
            boolean multi, 
            Map<ITmfEventField, MultipleInference> multiValues) {
		super(trace, rank, ts, type, content);
		
		fStart = tsStart;
		fEnd = tsEnd;
		fLocalRank = localRank;
		fCpu = cpu;
		fIsMulti = multi;
		fMultiValues = multiValues;
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
	    if ((this.getTimestamp().getValue() > other.getTimestamp().getValue()) ||
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
	
	public boolean isMulti() {
		return fIsMulti;
	}
	
	public Map<ITmfEventField, MultipleInference> getMultiValues() {
		return fMultiValues;
	}
	
	@Override
    public ITmfEventField getContent() {
		ITmfEventField content = super.getContent();
		if (!fIsMulti) {
			return content; // we can return the content field directly if there is no multiple values
		}		
		// Return content based on user-choice for multiple values
		List<ITmfEventField> fields = new ArrayList<>();
        for (ITmfEventField field : content.getFields()) {
        	if (field.getValue().equals(MULTI_VALUE)) {
        		TmfEventField choice = fMultiValues.get(field).getChoice();
        		if (choice == null) {
        			// TODO here compute best choice, according to some probabilities
        			fields.add(fMultiValues.get(field).getPossibilites().iterator().next()); // return first value if the choice has not been set yet
        		}
        		else {
        			fields.add(choice);
        		}
        	}
        	else {
        		fields.add(field); // return the single value field
        	}
        }
        
        return new TmfEventField(ITmfEventField.ROOT_FIELD_ID, null, fields.toArray(new TmfEventField[fields.size()]));
	}

}
