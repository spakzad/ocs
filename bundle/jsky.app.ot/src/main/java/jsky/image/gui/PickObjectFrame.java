/*
 * Copyright 2000 Association for Universities for Research in Astronomy, Inc.,
 * Observatory Control System, Gemini Telescopes Project.
 *
 * $Id: PickObjectFrame.java 4414 2004-02-03 16:21:36Z brighton $
 */

package jsky.image.gui;

import java.awt.BorderLayout;
import javax.swing.JFrame;

import jsky.util.Preferences;


/**
 * Provides a top level window for a PickObject panel.
 *
 * @version $Revision: 4414 $
 * @author Allan Brighton
 */
public class PickObjectFrame extends JFrame {

    /** The main panel */
    private PickObject pickObject;


    /**
     * Create a top level window containing an PickObject panel.
     */
    public PickObjectFrame(MainImageDisplay imageDisplay) {
        super("Pick Objects");
        pickObject = new PickObject(this, imageDisplay);
        getContentPane().add(pickObject, BorderLayout.CENTER);
        pack();
        Preferences.manageLocation(this);
        setVisible(true);
        setDefaultCloseOperation(HIDE_ON_CLOSE);
    }

    /** Return the internal panel object */
    public PickObject getPickObject() {
        return pickObject;
    }
}
