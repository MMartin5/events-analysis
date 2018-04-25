package org.eclipse.tracecompass.incubator.coherence.ui.dialogs;

import java.util.List;
import java.util.Map;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.LocalResourceManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.events.TreeEvent;
import org.eclipse.swt.events.TreeListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfInferredEvent;
import org.eclipse.tracecompass.incubator.coherence.core.newmodel.MultipleInference;
import org.eclipse.tracecompass.incubator.coherence.core.pattern.stateprovider.XmlPatternStateSystemModule;
import org.eclipse.tracecompass.incubator.coherence.ui.views.CoherenceView;
import org.eclipse.tracecompass.tmf.core.event.ITmfEventField;
import org.eclipse.tracecompass.tmf.core.event.TmfEventField;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.dialogs.TimeGraphLegend;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;

/**
 * @see TimeGraphLegend
 * 
 * @author mmartin
 *
 */
public class InferenceDialog extends TitleAreaDialog {
	
	private XmlPatternStateSystemModule fModule;
	private final LocalResourceManager fResourceManager = new LocalResourceManager(JFaceResources.getResources());
	private boolean dirty;
	
	static private String TITLE = "Inference resolution";
	static private String SUBTITLE = "Inferred events";
	
	/**
     * Constructor
     *
     * @param parent
     *            The parent shell
     * @param fModule
     *            The presentation provider
     */
	public InferenceDialog(Shell parentShell, XmlPatternStateSystemModule module) {
		super(parentShell);
		
		fModule = module;
	}
	
	/**
	 * Allow the dialog window to be resizable
	 */
	@Override
	protected boolean isResizable() {
		return true;
	}
	
	@Override
    protected Control createDialogArea(Composite parent) {		
        Composite dlgArea = (Composite) super.createDialogArea(parent);
        Composite composite = new Composite(dlgArea, SWT.NONE);

        GridLayout layout = new GridLayout();
        composite.setLayout(layout);
        GridData gd = new GridData(SWT.FILL, SWT.FILL, true, true);
        composite.setLayoutData(gd);

        createEventsGroup(composite);

        setTitle(TITLE);
        setDialogHelpAvailable(false);
        setHelpAvailable(false);
        
        dirty = false;

        composite.addDisposeListener((e) -> {
            fResourceManager.dispose();
        });
        return composite;
    }
	
	private void createEventsGroup(Composite composite) {
        ScrolledComposite sc = new ScrolledComposite(composite, SWT.V_SCROLL | SWT.H_SCROLL);
        sc.setExpandHorizontal(true);
        sc.setExpandVertical(true);
        Group gs = new Group(sc, SWT.H_SCROLL);
        sc.setContent(gs);
        GridData gd = new GridData(SWT.FILL, SWT.FILL, true, true);
        sc.setLayoutData(gd);

        StringBuffer buffer = new StringBuffer();
        buffer.append(SUBTITLE);
        gs.setText(buffer.toString());

        GridLayout layout = new GridLayout();
        layout.marginWidth = 20;
        layout.marginBottom = 10;
        gs.setLayout(layout);

        // Go through all the inferred events
        List<TmfInferredEvent> events = fModule.getMultiInferredEvents();
        for (TmfInferredEvent event : events) {
            new EventEntry(gs, event);
        }
        /* Allow scroll bar to be resized dynamically, according to the extendable content 
           (see www.codeaffine.com/2016/03/01/swt-scrolledcomposite/) */
        sc.addListener(SWT.Resize, event -> {
        	int width = sc.getClientArea().width;
        	sc.setMinSize(composite.computeSize(width, SWT.DEFAULT));
        });
    }
	
	@Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL,
                true);
    }
	
	@Override 
	protected void okPressed() {
		if (dirty) {
			/* Refresh view */
			final IWorkbench wb = PlatformUI.getWorkbench();
	        final IWorkbenchPage activePage = wb.getActiveWorkbenchWindow().getActivePage();
	    	IViewPart view = activePage.findView(CoherenceView.ID);
	    	if (view != null && view instanceof CoherenceView) {
	    		((CoherenceView) view).getGlobalInferenceViewAction().run();
	    	}
		}
    	super.okPressed();
	};
	
	private class EventEntry extends Composite {

        public EventEntry(Composite parent, TmfInferredEvent event) {
            super(parent, SWT.NONE);
            setLayout(GridLayoutFactory.swtDefaults().numColumns(2).create());
            setLayoutData(GridDataFactory.swtDefaults().grab(true, false).create());
            
            Label label = new Label(this, SWT.NONE);
            label.setText(event.getTimestamp().toString() + " " + event.getName());
            label.setLayoutData(GridDataFactory.fillDefaults().grab(true, true).align(SWT.BEGINNING, SWT.BEGINNING).create());
            
            Tree tree = new Tree(this, SWT.BORDER | SWT.NO_SCROLL); // VIRTUAL => populated on-demand ?
            tree.addTreeListener(new TreeListener() {
				
				@Override
				public void treeExpanded(TreeEvent e) {
					getDisplay().asyncExec(new Runnable() {
						
						@Override
						public void run() {
							parent.layout();
							TreeItem root = (TreeItem) e.item;
							TreeItem[] children = root.getItems();
							
							MultipleInference multipleValue = (MultipleInference) root.getData();
							TmfEventField choice = multipleValue.getChoice();
							if (choice != null) {
								for (TreeItem item : children) {
									TmfEventField possibility = (TmfEventField) item.getData();
									if (choice.equals(possibility)) {
										root.getParent().select(item); // select item in tree
									}
								}
							}
							/* Resize parent shell after item has been extended  
							   see https://stackoverflow.com/questions/20204381/swt-shell-resize-depending-on-children#20214239 */
							Shell parentShell = parent.getShell();
							parentShell.layout(true, true);
							final Point newSize = parentShell.computeSize(SWT.DEFAULT, SWT.DEFAULT, true);
							parentShell.setSize(newSize);
						}
					});
				}
				
				@Override
				public void treeCollapsed(TreeEvent e) {
					getDisplay().asyncExec(new Runnable() {
						
						@Override
						public void run() {
							parent.layout();
						}
					});
					
				}
			});
            tree.addSelectionListener(new SelectionListener() {
				
				@Override
				public void widgetSelected(SelectionEvent e) {
					TreeItem item = (TreeItem) e.item;
					TreeItem parent = item.getParentItem();
					if (parent == null) { // item is the root
						return;
					}
					
					MultipleInference multipleValue = (MultipleInference) parent.getData();
					TmfEventField possibility = (TmfEventField) item.getData();
					multipleValue.update(possibility);
					
					dirty = true;
				}
				
				@Override
				public void widgetDefaultSelected(SelectionEvent e) {
					// do nothing
				}
			});
            
            Map<ITmfEventField, MultipleInference> fields = event.getMultiValues();
            for (ITmfEventField field : fields.keySet()) {
            	String fieldName = field.getName();

            	MultipleInference multipleValue = fields.get(field);
            	TreeItem item = new TreeItem(tree, SWT.NONE);
            	item.setData(multipleValue);
            	item.setText(fieldName);
            	
				List<TmfEventField> possibilities = multipleValue.getPossibilites();
            	for (TmfEventField possibility : possibilities) {
            		TreeItem subitem = new TreeItem(item, SWT.NONE);
            		subitem.setData(possibility);
            		subitem.setText(possibility.getFormattedValue());
            	}
            }
        }
    }

}
