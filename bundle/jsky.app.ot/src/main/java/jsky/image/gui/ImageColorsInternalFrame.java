/*
 * ESO Archive
 *
 * $Id: ImageColorsInternalFrame.java 4414 2004-02-03 16:21:36Z brighton $
 *
 * who             when        what
 * --------------  ----------  ----------------------------------------
 * Allan Brighton  1999/05/03  Created
 */

package jsky.image.gui;


import java.awt.BorderLayout;

import javax.swing.JInternalFrame;

import jsky.util.I18N;
import jsky.util.Preferences;


/**
 * Provides a top level window for an ImageColors panel.
 *
 * @version $Revision: 4414 $
 * @author Allan Brighton
 */
public class ImageColorsInternalFrame extends JInternalFrame {

    // Used to access internationalized strings (see i18n/gui*.proprties)
    private static final I18N _I18N = I18N.getInstance(ImageColorsInternalFrame.class);

    // The GUI panel
    private ImageColors imageColors;


    /**
     * Create a top level window containing an ImageColors panel.
     */
    public ImageColorsInternalFrame(BasicImageDisplay imageDisplay) {
        super(_I18N.getString("imageColors"), true, false, true, true);
        imageColors = new ImageColors(this, imageDisplay);
        getContentPane().add(imageColors, BorderLayout.CENTER);
        pack();
        setClosable(true);
        setIconifiable(false);
        setMaximizable(false);
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        Preferences.manageLocation(this);
        setVisible(true);
    }


    /** Return the internal panel object */
    public ImageColors getImageColors() {
        return imageColors;
    }
}
