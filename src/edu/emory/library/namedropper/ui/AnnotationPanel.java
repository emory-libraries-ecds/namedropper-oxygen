/**
 * file src/edu/emory/library/namedropper/ui/AnnotationPanel.java
 *
 * Copyright 2012 Emory University Library
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.emory.library.namedropper.ui;

import java.util.List;
import java.util.ArrayList;

// swing imports
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JButton;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

// awt imports
import java.awt.Color;
import java.awt.Component;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

// oxygen imports
import ro.sync.exml.workspace.api.PluginWorkspace;
import ro.sync.exml.workspace.api.editor.page.text.WSTextEditorPage;
import ro.sync.exml.workspace.api.editor.WSEditor;

// local imports
import edu.emory.library.spotlight.SpotlightAnnotation;
import edu.emory.library.namedropper.plugins.PluginOptions;


public class AnnotationPanel extends JPanel {

    public static String VIEW_ID = "AnnotationViewID";
    public static String TITLE = "NameDropper Annotations";

    private JScrollPane scrollPane;
    private JTable table;


    /**
     * Abstract table model that uses a list of annotations
     * as the basis for each row of data in a table.
     */
    class AnnotationTableModel extends AbstractTableModel {
        private String[] columnNames = {"Approve", "Recognized Name"};
        public static final int APPROVED = 0;
        public static final int NAME = 1;

        private List<SpotlightAnnotation> data = new ArrayList<SpotlightAnnotation>();

        public int getColumnCount() {
            return columnNames.length;
        }

        public int getRowCount() {
            return data.size();
        }

        public String getColumnName(int col) {
            return columnNames[col];
        }

        public Object getValueAt(int row, int col) {
            switch (col) {
                case APPROVED:
                    return false;

                case NAME:
                    // NOTE: this is a bit slow and should probably be done in the background, if possible
                    String name = data.get(row).getLabel();
                    // use recognized surface form, if query doesn't find a label
                    if (name == null || name.equals("")) {
                        return data.get(row).getSurfaceForm();
                    }
                    return name;
            }
            return null;
        }

        public Class getColumnClass(int c) {
            return getValueAt(0, c).getClass();
        }

        public boolean isCellEditable(int row, int col) {
            // FIXME: this seems to be ignored/unused; probably need to set table editable
            switch (col) {
                case APPROVED:             // selected for insert; should eventually be editable
                    return true;

                default:
                    return false;
            }
        }

        /**
         * Add a list of annotations and update the table to display them.
         */
        public void addAnnotations(List<SpotlightAnnotation> annotations) {
            int last_row = data.size();
            for (SpotlightAnnotation sa : annotations) {
                data.add(sa);
            }
            fireTableRowsInserted(last_row, data.size());
        }

        /**
         * Return the SpotlightAnnotation object for a specific row.
         */
        public SpotlightAnnotation getRowAnnotation(int row) {
            return data.get(row);
        }

        /**
         * Remove all associated annotations and update the table
         */
        public void clearAnnotations() {
            int size = data.size();
            data.clear();
            fireTableRowsDeleted(0, size);
        }

    } // end table model

    /**
     * Custom table cell renderer for annotations to add information
     * about the recognized resource via tooltip text.
     */
    public class AnnotationRenderer extends DefaultTableCellRenderer {

        public Component getTableCellRendererComponent(
                                JTable table, Object obj,
                                boolean isSelected, boolean hasFocus,
                                int row, int column) {
            // inherit all the default display logic
            super.getTableCellRendererComponent(table, obj,
                isSelected, hasFocus, row, column);

            // add a custom tool tip
            AnnotationTableModel model = (AnnotationTableModel) table.getModel();
            SpotlightAnnotation an = model.getRowAnnotation(row);

            // display the beginning of the resource description;
            // should be enough in most cases to see if it's the right thing or not
            // (eventually we'll probably want a way to expose more information)
            String description = an.getAbstract();
            // use dbpedia URI as a fallback if we can't get an abstract
            if (description == null || description.equals("")) {
                description = an.getUri();
            } else if (description.length() > 100) {
                description = description.substring(0, 100) + " ...";
            }
            setToolTipText(description);
            return this;
        }
    }

    public AnnotationPanel() {
        this.setLayout(new BorderLayout());
        this.setBackground(Color.GRAY);

        // initialize table and scroll pane
        table = new JTable(new AnnotationTableModel());

        scrollPane = new JScrollPane(table);
        table.setFillsViewportHeight(true);
        // force first column (check box) to be small
        TableColumn column = table.getColumnModel().getColumn(AnnotationTableModel.APPROVED);
        column.setPreferredWidth(5);  // this should work, but as far as I can tell Oxygen ignores it
        column.setMaxWidth(7);      // force the column to be minimal width

        // Customize tool tips for annotation text
        column = table.getColumnModel().getColumn(AnnotationTableModel.NAME);
        // DefaultTableCellRenderer renderer = new DefaultTableCellRenderer();
        // renderer.setToolTipText("test tool tip");
        column.setCellRenderer(new AnnotationRenderer());

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        ListSelectionModel rowSM = table.getSelectionModel();
        // add a row listener
        rowSM.addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                // Ignore extra messages.
                if (e.getValueIsAdjusting()) return;

                ListSelectionModel lsm = (ListSelectionModel)e.getSource();
                if (! lsm.isSelectionEmpty()) {
                    int selectedRow = lsm.getMinSelectionIndex();

                    AnnotationTableModel model = (AnnotationTableModel) table.getModel();
                    SpotlightAnnotation an = model.getRowAnnotation(selectedRow);

                    // Based on the selected annotation, highlight the corresponding
                    // recognized text where it occurs in the document.
                    PluginWorkspace ws = PluginOptions.getWorkspace();
                    WSTextEditorPage ed = null;
                    WSEditor editorAccess = ws.getCurrentEditorAccess(PluginWorkspace.MAIN_EDITING_AREA);
                    if (editorAccess != null && editorAccess.getCurrentPage() instanceof WSTextEditorPage) {
                        ed = (WSTextEditorPage)editorAccess.getCurrentPage();
                        int start = an.getOffset();
                        ed.setCaretPosition(start);
                        ed.select(start, start + an.getSurfaceForm().length());
                    }
                }
            }
        });

        this.add(scrollPane, BorderLayout.CENTER);

        // add a button to clear all current annotations
        JButton clearAll = new JButton("Clear");
        clearAll.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                AnnotationTableModel model = (AnnotationTableModel) table.getModel();
                model.clearAnnotations();
            }
        });
        this.add(clearAll, BorderLayout.SOUTH);

        // todo: eventually we will want other buttons
        // (insert all, insert selected?)
    }

    /**
     * Add a list of annotations to the table.
     */
    public void addAnnotations(List<SpotlightAnnotation> annotations) {
        AnnotationTableModel model = (AnnotationTableModel) this.table.getModel();
        model.addAnnotations(annotations);
    }


}