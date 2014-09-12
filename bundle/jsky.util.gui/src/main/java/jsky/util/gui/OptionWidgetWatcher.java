// Copyright 1997 Association for Universities for Research in Astronomy, Inc.,
// Observatory Control System, Gemini Telescopes Project.
// See the file COPYRIGHT for complete details.
//
// $Id: OptionWidgetWatcher.java 6719 2005-11-08 19:35:36Z brighton $
//
/**
 * This class watches a OptionWidget object to know which node is selected.
 *
 * @author      Dayle Kotturi
 * @version     1.0, 8/8/97
 */

package jsky.util.gui;

/**
 * A class implements this interface if it wants to register itself
 * as the watcher of an OptionWidget widget.
 */
public interface OptionWidgetWatcher {
    /**
     * An option was selected.
     */
    public void optionAction(OptionWidget owe);
}
