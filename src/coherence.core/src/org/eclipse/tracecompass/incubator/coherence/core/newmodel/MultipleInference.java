package org.eclipse.tracecompass.incubator.coherence.core.newmodel;

import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.tmf.core.event.TmfEventField;


public class MultipleInference {
	
	private final List<TmfEventField> fPossibilities;
	private TmfEventField fChoice;
	
	public MultipleInference(List<TmfEventField> possibilities) {
		fPossibilities = possibilities;
		fChoice = null;
	}
	
	private void setChoice(int index) {
		fChoice = fPossibilities.get(index);
	}
	
	public @Nullable TmfEventField getChoice() {
		return fChoice;
	}
	
	public List<TmfEventField> getPossibilites() {
		return fPossibilities;
	}
	
	public void update(TmfEventField choice) {
		setChoice(fPossibilities.indexOf(choice));
	}

}
