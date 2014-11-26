/*
 * Copyright (C) 2010-2014 Serge Rieder
 * serge@jkiss.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.jkiss.dbeaver.ui.search.data;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.viewers.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.core.CoreMessages;
import org.jkiss.dbeaver.core.DBeaverCore;
import org.jkiss.dbeaver.model.navigator.*;
import org.jkiss.dbeaver.model.runtime.DBRProcessListener;
import org.jkiss.dbeaver.model.struct.DBSEntity;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.model.struct.DBSObjectContainer;
import org.jkiss.dbeaver.model.struct.DBSWrapper;
import org.jkiss.dbeaver.ui.UIUtils;
import org.jkiss.dbeaver.ui.search.AbstractSearchPage;
import org.jkiss.dbeaver.ui.views.navigator.database.DatabaseNavigatorTree;
import org.jkiss.dbeaver.ui.views.navigator.database.load.TreeLoadNode;
import org.jkiss.utils.CommonUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class SearchDataPage extends AbstractSearchPage {

    private static final String PROP_MASK = "search.data.mask"; //$NON-NLS-1$
    private static final String PROP_CASE_SENSITIVE = "search.data.case-sensitive"; //$NON-NLS-1$
    private static final String PROP_MAX_RESULT = "search.data.max-results"; //$NON-NLS-1$
    private static final String PROP_FAST_SEARCH = "search.data.fast-search"; //$NON-NLS-1$
    private static final String PROP_SEARCH_NUMBERS = "search.data.search-numbers"; //$NON-NLS-1$
    private static final String PROP_SEARCH_LOBS = "search.data.search-lobs"; //$NON-NLS-1$
    private static final String PROP_HISTORY = "search.data.history"; //$NON-NLS-1$
    private static final String PROP_SOURCES = "search.data.object-source"; //$NON-NLS-1$

    private Combo searchText;
    private DatabaseNavigatorTree dataSourceTree;

    private SearchDataParams params = new SearchDataParams();
    private Set<String> searchHistory = new LinkedHashSet<String>();
    private List<DBNNode> checkedNodes;

    public SearchDataPage() {
		super("Database objects search");
    }

	@Override
	public void createControl(Composite parent) {
        initializeDialogUnits(parent);

        Composite searchGroup = new Composite(parent, SWT.NONE);
        searchGroup.setLayoutData(new GridData(GridData.FILL_BOTH));
        searchGroup.setLayout(new GridLayout(3, false));
        setControl(searchGroup);
        UIUtils.createControlLabel(searchGroup, "String");
        searchText = new Combo(searchGroup, SWT.DROP_DOWN);
        searchText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        if (params.searchString != null) {
            searchText.setText(params.searchString);
        }
        for (String history : searchHistory) {
            searchText.add(history);
        }
        searchText.addModifyListener(new ModifyListener() {
            @Override
            public void modifyText(ModifyEvent e)
            {
                params.searchString = searchText.getText();
                updateEnablement();
            }
        });

        Composite optionsGroup = new SashForm(searchGroup, SWT.NONE);
        GridLayout layout = new GridLayout(2, true);
        layout.marginHeight = 0;
        layout.marginWidth = 0;
        optionsGroup.setLayout(layout);
        GridData gd = new GridData(GridData.FILL_BOTH);
        gd.horizontalSpan = 3;
        optionsGroup.setLayoutData(gd);

        {
            final DBeaverCore core = DBeaverCore.getInstance();

            Group databasesGroup = UIUtils.createControlGroup(optionsGroup, "Databases", 1, GridData.FILL_BOTH, 0);
            gd = new GridData(GridData.FILL_BOTH);
            gd.heightHint = 300;
            databasesGroup.setLayoutData(gd);
            final DBNProject projectNode = core.getNavigatorModel().getRoot().getProject(core.getProjectRegistry().getActiveProject());
            DBNNode rootNode = projectNode == null ? core.getNavigatorModel().getRoot() : projectNode.getDatabases();
            dataSourceTree = new DatabaseNavigatorTree(databasesGroup, rootNode, SWT.SINGLE | SWT.CHECK);
            dataSourceTree.setLayoutData(new GridData(GridData.FILL_BOTH));
            final CheckboxTreeViewer viewer = (CheckboxTreeViewer) dataSourceTree.getViewer();
            viewer.addFilter(new ViewerFilter() {
                @Override
                public boolean select(Viewer viewer, Object parentElement, Object element) {
                    if (element instanceof TreeLoadNode) {
                        return true;
                    }
                    if (element instanceof DBNNode) {
                        if (element instanceof DBNDatabaseFolder) {
                            DBNDatabaseFolder folder = (DBNDatabaseFolder) element;
                            Class<? extends DBSObject> folderItemsClass = folder.getChildrenClass();
                            return folderItemsClass != null &&
                                (DBSObjectContainer.class.isAssignableFrom(folderItemsClass) ||
                                    DBSEntity.class.isAssignableFrom(folderItemsClass));
                        }
                        if (element instanceof DBNLocalFolder ||
                            element instanceof DBNProjectDatabases ||
                            element instanceof DBNDataSource ||
                            (element instanceof DBSWrapper && (((DBSWrapper) element).getObject() instanceof DBSObjectContainer) ||
                                ((DBSWrapper) element).getObject() instanceof DBSEntity))
                        {
                            return true;
                        }
                    }
                    return false;
                }
            });
            viewer.addCheckStateListener(new ICheckStateListener() {
                @Override
                public void checkStateChanged(CheckStateChangedEvent event) {
                    if (event.getChecked()) {
                        final DBNNode node = (DBNNode) event.getElement();
                        if (node instanceof DBNDataSource) {
                            DBNDataSource dsNode = (DBNDataSource) node;
                            dsNode.initializeNode(null, new DBRProcessListener() {
                                @Override
                                public void onProcessFinish(IStatus status)
                                {
                                    if (status.isOK()) {
                                        Display.getDefault().asyncExec(new Runnable() {
                                            @Override
                                            public void run()
                                            {
                                                if (!dataSourceTree.isDisposed()) {
                                                    updateEnablement();
                                                }
                                            }
                                        });
                                    }
                                }
                            });
                        }
                    }
                    updateEnablement();
                }
            });
        }
        {
            //new Label(searchGroup, SWT.NONE);
            Composite optionsGroup2 = UIUtils.createControlGroup(optionsGroup, "Settings", 2, GridData.FILL_BOTH, 0);
            optionsGroup2.setLayoutData(new GridData(GridData.FILL_HORIZONTAL | GridData.HORIZONTAL_ALIGN_BEGINNING | GridData.VERTICAL_ALIGN_BEGINNING));

            if (params.maxResults <= 0) {
                params.maxResults = 100;
            }

            final Spinner maxResultsSpinner = UIUtils.createLabelSpinner(optionsGroup2, CoreMessages.dialog_search_objects_spinner_max_results, params.maxResults, 1, 10000);
            maxResultsSpinner.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING));
            maxResultsSpinner.addModifyListener(new ModifyListener() {
                @Override
                public void modifyText(ModifyEvent e)
                {
                    params.maxResults = maxResultsSpinner.getSelection();
                }
            });

            final Button caseCheckbox = UIUtils.createLabelCheckbox(optionsGroup2, CoreMessages.dialog_search_objects_case_sensitive, params.caseSensitive);
            caseCheckbox.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING));
            caseCheckbox.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e)
                {
                    params.caseSensitive = caseCheckbox.getSelection();
                }
            });

            final Button fastSearchCheckbox = UIUtils.createLabelCheckbox(optionsGroup2, "Fast search (indexed)", params.fastSearch);
            fastSearchCheckbox.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING));
            fastSearchCheckbox.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e)
                {
                    params.fastSearch = fastSearchCheckbox.getSelection();
                }
            });


            final Button searchNumbersCheckbox = UIUtils.createLabelCheckbox(optionsGroup2, "Search in numbers", params.searchNumbers);
            searchNumbersCheckbox.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING));
            searchNumbersCheckbox.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e)
                {
                    params.searchNumbers = searchNumbersCheckbox.getSelection();
                }
            });

            final Button searchLOBCheckbox = UIUtils.createLabelCheckbox(optionsGroup2, "Search in LOBs", params.searchLOBs);
            searchLOBCheckbox.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING));
            searchLOBCheckbox.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e)
                {
                    params.searchLOBs = searchNumbersCheckbox.getSelection();
                }
            });
        }

        if (!checkedNodes.isEmpty()) {
            for (DBNNode node : checkedNodes) {
                ((CheckboxTreeViewer)dataSourceTree.getViewer()).setChecked(node, true);
            }
        }
        updateEnablement();
    }

    @Override
    public SearchDataQuery createQuery() throws DBException
    {
        params.sources = getCheckedSources();

        // Save search query
        if (!searchHistory.contains(params.searchString)) {
            searchHistory.add(params.searchString);
            searchText.add(params.searchString);
        }

        return SearchDataQuery.createQuery(params);

    }

    @Override
    public void loadState(IPreferenceStore store)
    {
        params.searchString = store.getString(PROP_MASK);
        params.caseSensitive = store.getBoolean(PROP_CASE_SENSITIVE);
        params.fastSearch = store.getBoolean(PROP_FAST_SEARCH);
        params.searchNumbers = store.getBoolean(PROP_SEARCH_NUMBERS);
        params.searchLOBs = store.getBoolean(PROP_SEARCH_LOBS);
        params.maxResults = store.getInt(PROP_MAX_RESULT);
        for (int i = 0; ;i++) {
            String history = store.getString(PROP_HISTORY + "." + i); //$NON-NLS-1$
            if (CommonUtils.isEmpty(history)) {
                break;
            }
            searchHistory.add(history);
        }
        checkedNodes = loadTreeState(store, PROP_SOURCES);
    }

    @Override
    public void saveState(IPreferenceStore store)
    {
        store.setValue(PROP_MASK, params.searchString);
        store.setValue(PROP_CASE_SENSITIVE, params.caseSensitive);
        store.setValue(PROP_MAX_RESULT, params.maxResults);
        store.setValue(PROP_FAST_SEARCH, params.fastSearch);
        store.setValue(PROP_SEARCH_NUMBERS, params.searchNumbers);
        store.setValue(PROP_SEARCH_LOBS, params.searchLOBs);
        saveTreeState(store, PROP_SOURCES, dataSourceTree);

        {
            // Search history
            int historyIndex = 0;
            for (String history : searchHistory) {
                if (historyIndex >= 20) {
                    break;
                }
                store.setValue(PROP_HISTORY + "." + historyIndex, history); //$NON-NLS-1$
                historyIndex++;
            }
        }
    }

    protected List<DBSObject> getCheckedSources()
    {
        List<DBSObject> result = new ArrayList<DBSObject>();
        for (Object sel : ((CheckboxTreeViewer)dataSourceTree.getViewer()).getCheckedElements()) {
            if (sel instanceof DBSWrapper) {
                DBSObject object = ((DBSWrapper) sel).getObject();
                if (object != null && object.getDataSource() != null) {
                    result.add(object);
                }
            }
        }
        return result;
    }

    protected void updateEnablement()
    {
        boolean enabled = false;
        if (!getCheckedSources().isEmpty()) {
            enabled = true;
        }
        container.setSearchEnabled(enabled);
    }

}