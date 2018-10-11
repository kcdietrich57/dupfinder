package dup.browser;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import dup.model.Database;
import dup.util.Trace;
import dup.util.Utility;

// TODO features/bugs
//============== function =====================
//show progress while loading
//hide/disregard contexts without closing
//stats - false checksum matches
//save/load complete info including verified identical/different files
//empty file processing
//file/selection properties
//suggest duplicates to eliminate (size, duplicated folders)
//move/rename files
//create new context from folder
//reload context
//
//automatically delete true duplicate files between contexts
//clean up close behavior
//fix context name coupling to root directory name
//add IGNORE property for files/folders we don't care about
//activate/deactivate known contexts
//display statistics for context/folder/file
//show properties of selection in UI
//trash bin for deletes
//=============== visuals =====================
//Rework tree view - less text, more graphics
//icons for toolbar
//clean up context menus - use other UI instead
//=============== usability ===================
//remember last configuration
//=============== performance =================
//automatic background verification of duplicates
//minimal reworking of duplicate info when loading file details
// 

public class DupBrowserApp {
	private static void createAndShowGUI() {
		// Force load environment
		Database db = Database.instance();

		JFrame frame = new JFrame("Duplicate Browser");
		frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		Container c = frame.getContentPane();

		Controller controller = new Controller();
		View view = new View(controller);

		db.setView(view);

		c.add(view.getToolBar(), BorderLayout.PAGE_START);
		c.add(view.getUIContainer());
		c.add(view.getStatusBar(), BorderLayout.SOUTH);

		frame.pack();
		frame.setSize(1100, 900);

		frame.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				Database.instance().shutDown();

				super.windowClosing(e);
			}
		});

		db.analyzeDuplicates();

		frame.setVisible(true);
	}

	public static void main(String[] args) {
		int cores = Runtime.getRuntime().availableProcessors();
		Trace.traceln(Trace.VERBOSE, "Running on " + cores + " cores.");
		Trace.traceln(Trace.NORMAL, "Max memory=" //
				+ Utility.formatSize(Runtime.getRuntime().maxMemory()));

		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				createAndShowGUI();
			}
		});

		Trace.traceln(Trace.VERBOSE, "Exiting main method.");
	}
}