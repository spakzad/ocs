// Copyright 1997 Association for Universities for Research in Astronomy, Inc.,
// Observatory Control System, Gemini Telescopes Project.
// See the file COPYRIGHT for complete details.
//
// $Id: EdIterFlatObs.java 47001 2012-07-26 19:40:02Z swalker $
//
package jsky.app.ot.gemini.editor;

import edu.gemini.pot.sp.ISPSeqComponent;
import edu.gemini.spModel.gemini.calunit.CalUnitParams.Diffuser;
import edu.gemini.spModel.gemini.calunit.CalUnitParams.Filter;
import edu.gemini.spModel.gemini.calunit.CalUnitParams.Lamp;
import edu.gemini.spModel.gemini.calunit.CalUnitParams.Shutter;
import edu.gemini.spModel.gemini.seqcomp.SeqRepeatFlatObs;
import edu.gemini.spModel.obsclass.ObsClass;
import jsky.app.ot.OTOptions;
import jsky.app.ot.editor.OtItemEditor;
import jsky.app.ot.editor.SpinnerEditor;
import jsky.app.ot.editor.type.SpTypeUIUtil;
import jsky.util.gui.DialogUtil;
import jsky.util.gui.DropDownListBoxWidget;
import jsky.util.gui.TextBoxWidget;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;


/**
 * This is the editor for the Flat Observation iterator.
 */
public final class EdIterFlatObs extends OtItemEditor<ISPSeqComponent, SeqRepeatFlatObs>
        implements jsky.util.gui.DropDownListBoxWidgetWatcher, jsky.util.gui.TextBoxWidgetWatcher {

    // the GUI layout panel
    private final IterFlatObsForm _w = new IterFlatObsForm();

    // If true, ignore action events
    private boolean ignoreActions = false;

    // lamp choices
    private final JRadioButton[] _lampButtons = new JRadioButton[]{
        _w.lamp1, _w.lamp2, _w.lamp3
    };

    // arc lamp choices (exclusive of lamp: see OT-360)
    private final JCheckBox[] _arcButtons = new JCheckBox[]{
        _w.arc1, _w.arc2, _w.arc3, _w.arc4
    };

    private static final String LAMP_PROPERTY = "Lamp";

    private final ActionListener lampListener = new ActionListener() {
        public void actionPerformed(ActionEvent e) { _lampSelected(); }
    };

    private final ItemListener arcListener = new ItemListener() {
        public void itemStateChanged(ItemEvent e) { _arcSelected(); }
    };

    private final SpinnerEditor sped;

    /**
     * The constructor initializes the user interface.
     */
    public EdIterFlatObs() {
        _w.repeatSpinner.setModel(new SpinnerNumberModel(1, 1, null, 1));
        sped = new SpinnerEditor(_w.repeatSpinner, new SpinnerEditor.Functions() {
            @Override public int getValue() {
                return getDataObject().getStepCount();
            }
            @Override public void setValue(int newValue) {
                if (!ignoreActions) getDataObject().setStepCount(newValue);
            }
        });

        final ButtonGroup group = new ButtonGroup();
        final List<Lamp> flatLamps = Lamp.flatLamps();
        for (int i = 0; i < _lampButtons.length; i++) {
            final Lamp l = flatLamps.get(i);
            _lampButtons[i].putClientProperty(LAMP_PROPERTY, l);
            _lampButtons[i].setText(l.displayValue());
            group.add(_lampButtons[i]);
            _lampButtons[i].addActionListener(lampListener);
        }
        final List<Lamp> arcLamps = Lamp.arcLamps();
        for (int i = 0; i < _arcButtons.length; i++) {
            final Lamp l = arcLamps.get(i);
            _arcButtons[i].putClientProperty(LAMP_PROPERTY, l);
            _arcButtons[i].setText(l.displayValue());
            _arcButtons[i].addItemListener(arcListener);
        }

        _w.shutter.setChoices(Shutter.values());

        // Set up the filter editor.
        SpTypeUIUtil.initListBox(_w.filter, Filter.class, new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                final Filter f = (Filter) _w.filter.getSelectedItem();
                getDataObject().setFilter(f);
            }
        });


        _w.diffuser.setChoices(Diffuser.values());
        _w.obsClass.setChoices(ObsClass.values());

        _w.exposureTime.addWatcher(this);
        _w.coadds.addWatcher(this);
        _w.shutter.addWatcher(this);

        _w.diffuser.addWatcher(this);
        _w.obsClass.addWatcher(this);
    }

    /**
     * Return the window containing the editor
     */
    public JPanel getWindow() {
        return _w;
    }

    @Override public void init() {
        _update();
        sped.init();
    }

    @Override public void cleanup() {
        sped.cleanup();
    }

    // Update the widgets to reflect the model settings
    private void _update() {
        ignoreActions = true;
        try {
            _showLamps();
            _w.shutter.setValue(getDataObject().getShutter());

            // Set the selected item directly on the model to allow for
            // obsolete types to be displayed. If set on the widget itself,
            // it will not be displayed in the combo box.
            _w.filter.getModel().setSelectedItem(getDataObject().getFilter());

            _w.diffuser.setValue(getDataObject().getDiffuser());
            _w.obsClass.setValue(getDataObject().getObsClass());
            _w.exposureTime.setValue(getDataObject().getExposureTime());
            _w.coadds.setValue(getDataObject().getCoaddsCount());

            _updateEnabledStates();
        } catch (Exception e) {
            DialogUtil.error(e);
        }
        ignoreActions = false;
    }

    // update the lamp display to reflect the data object
    private void _showLamps() {
        final Set<Lamp> lamps = getDataObject().getLamps();
        for (JCheckBox b : _arcButtons) {
            final Lamp l = (Lamp) b.getClientProperty(LAMP_PROPERTY);
            b.removeItemListener(arcListener);
            b.setSelected(lamps.contains(l));
            b.addItemListener(arcListener);
        }
        for (JRadioButton b : _lampButtons) {
            final Lamp l = (Lamp) b.getClientProperty(LAMP_PROPERTY);
            b.removeActionListener(lampListener);
            b.setSelected(lamps.contains(l));
            b.addActionListener(lampListener);
        }
    }

    /**
     * Watch changes to text box widgets.
     */
    public void textBoxKeyPress(TextBoxWidget tbwe) {
        if (tbwe == _w.exposureTime) {
            getDataObject().setExposureTime(tbwe.getDoubleValue(1.));
        } else if (tbwe == _w.coadds) {
            getDataObject().setCoaddsCount(tbwe.getIntegerValue(1));
        }
    }

    /**
     * Text box action.
     */
    public void textBoxAction(TextBoxWidget tbwe) {
    }

    /**
     * Called when an item in a DropDownListBoxWidget is selected.
     */
    public void dropDownListBoxAction(DropDownListBoxWidget ddlbw, int index, String val) {
        if (ddlbw == _w.shutter) {
            getDataObject().setShutter(Shutter.getShutterByIndex(index));
        } else if (ddlbw == _w.diffuser) {
            getDataObject().setDiffuser(Diffuser.getDiffuserByIndex(index));
        } else if (ddlbw == _w.obsClass) {
            getDataObject().setObsClass(ObsClass.values()[index]);
        }
        _updateEnabledStates();
    }

    private boolean isIrGreyBody() {
        final Set<Lamp> lamps = getDataObject().getLamps();
        return lamps.contains(Lamp.IR_GREY_BODY_HIGH) || lamps.contains(Lamp.IR_GREY_BODY_LOW);
    }

    /**
     * Update the enabled states of the widgets based on the current values
     */
    private void _updateEnabledStates() {
        // disable lamp radiobuttons if an arc was selected
        final boolean isLamp = !getDataObject().isArc();
        for (JRadioButton _lampButton : _lampButtons) {
            _lampButton.setEnabled(isLamp);
        }

        final boolean editable = OTOptions.areRootAndCurrentObsIfAnyEditable(getProgram(), getContextObservation());
        _w.shutter.setEnabled(isIrGreyBody() && editable);
    }

    // Called when one of the lamp radiobuttons is selected
    private void _lampSelected() {
        if (ignoreActions) {
            return;
        }
        for (int i = 0; i < _lampButtons.length; i++) {
            if (_lampButtons[i].isSelected()) {
                final Lamp lamp = Lamp.flatLamps().get(i);
                getDataObject().setLamp(lamp);
                getDataObject().setDiffuser(lamp == Lamp.QUARTZ ? Diffuser.VISIBLE : Diffuser.IR);  // See OT-426
                final boolean b = (lamp == Lamp.IR_GREY_BODY_HIGH ||
                        lamp == Lamp.IR_GREY_BODY_LOW);
                if (b) {
                    getDataObject().setShutter(Shutter.OPEN);
                } else {
                    getDataObject().setShutter(Shutter.CLOSED);
                }
//                getDataObject().setObsClass(ObsClass.PARTNER_CAL); // see OT-411
                getDataObject().setObsClass(getDataObject().getDefaultObsClass());
                _update();
                break;
            }
        }
        _update();
    }

    // Called when one of the arc checkboxes changes state
    private void _arcSelected() {
        if (ignoreActions) {
            return;
        }
        final ArrayList<Lamp> arcs = new ArrayList<Lamp>(_arcButtons.length);
        boolean foundCuAR = false;  // See OT-426
        for (int i = 0; i < _arcButtons.length; i++) {
            if (_arcButtons[i].isSelected()) {
            	final Lamp lamp = Lamp.arcLamps().get(i);
                arcs.add(lamp);
                foundCuAR |= lamp == Lamp.CUAR_ARC;  // See OT-426
            }
        }
        if (arcs.size() != 0) {
            getDataObject().setLamps(arcs);
            getDataObject().setShutter(Shutter.CLOSED);
//            getDataObject().setObsClass(ObsClass.PROG_CAL); // see OT-411
            getDataObject().setObsClass(getDataObject().getDefaultObsClass());
            getDataObject().setDiffuser(foundCuAR ? Diffuser.VISIBLE : Diffuser.IR); // See OT-426
        } else {
            _lampSelected();
        }
        _update();
    }
}
