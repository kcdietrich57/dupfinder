package dup.browser;

import java.awt.Component;
import java.awt.Container;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.swing.JToolBar;

import dup.analyze.DetailLevel;
import dup.model.Context;
import dup.model.Database;
import dup.model.FileInfo;
import dup.util.Trace;

public class View {
	public View(Controller controller) {
		this.widgets = new Widgets(this);
		this.controller = controller;
		this.controller.setView(this);

		this.widgets.createUI();
	}

	void setDatabaseModel() {
		//Database.instance().setModelAvailable(true);
		// setModel(this.controller.browserTreeModel);
	}

	void setBrowserTreeModel(FolderTreeModel model) {
		this.controller.browserTreeModel = model;
	}

	FolderTreeModel getBrowserTreeModel() {
		return this.controller.browserTreeModel;
	}

	Object[] getBrowserTreeSelection() {
		return this.browseTreeSelection;
	}

	void setDuplicateTreeModel(FolderTreeModel dupModel) {
		this.controller.duplicateTreeModel = dupModel;
	}

	void loadDetails(Object[] objects, DetailLevel detail) {
		this.controller.loadDetails(objects, detail);
	}

	void loadDetails(List<FileInfo> files, DetailLevel detail) {
		this.controller.loadDetails(files, detail);
	}

	public void rebuildBrowseTree() {
		this.widgets.saveExpandedState();
		this.controller.browserTreeModel.structureChanged();
		this.widgets.restoreExpandedState();
	}

	public Container getUIContainer() {
		return this.widgets.mainSplit;
	}

	public JToolBar getToolBar() {
		return this.widgets.toolBar;
	}

	public Component getStatusBar() {
		return this.widgets.statusBar;
	}

	void dupTreeSelectionChanged() {
		this.dupTreeSelection = this.widgets.getSelectedDupTreeNodes();

		this.widgets.updateToolbarEnablement(this.dupTreeSelection);
	}

	void browseTreeSelectionChanged() {
		Trace.traceln(Trace.NORMAL);

		this.browseTreeSelection = this.widgets.getSelectedBrowseTreeNodes();

// TODO		Dumper.dumpSelectionDuplicateInfo(this.browseTreeSelection);

		try {
			this.dupTreeSelection = null;
			this.widgets.duplicateTree.clearSelection();

			// TODO do this once only?
			View.this.controller.duplicateTreeModel.setRoot(Database.instance());

			this.controller.duplicateTreeModel.filter.setSelection(this.browseTreeSelection);
			this.controller.duplicateTreeModel.structureChanged();

			// TODO expandAll() method
			for (int ii = 0; ii < this.widgets.duplicateTree.getRowCount(); ++ii) {
				this.widgets.duplicateTree.expandRow(ii);
			}
		} finally {
			this.widgets.updateUIState();
		}

		this.widgets.updateToolbarEnablement(this.browseTreeSelection);
	}

	void openContext() {
		File file = this.widgets.openContextDialog();

		if (file != null) {
			Database.instance().openContext(file.getPath(), file.getName());

			rebuildBrowseTree();
		}
	}

	void closeContext(Context context) {
		Database.instance().closeContext(context);
		this.browseTreeSelection = null;

		rebuildBrowseTree();
	}

	void closeAllContexts() {
		for (Context context : Database.instance().getContexts()) {
			Database.instance().closeContext(context);
		}

		this.browseTreeSelection = null;

		rebuildBrowseTree();
	}

	void saveContext(Context context) {
		Database.instance().saveContext(context);

		rebuildBrowseTree();
	}

	void recycleSelectedFiles(List<FileInfo> files) {
		recycleSelectedFiles(files, false);
	}

	void recycleGDUPSelectedFiles(List<FileInfo> files) {
		recycleSelectedFiles(files, true);
	}

	private void recycleSelectedFiles(List<FileInfo> files, boolean gdupsOnly) {
		for (FileInfo file : files) {
			if (gdupsOnly && !file.hasGlobalDuplicates()) {
				continue;
			}

			Context context = file.getContext();
			if (context == null) {
				System.err.println("No context for " + file.getRelativeName());
				continue;
			}

			File srcFile = file.getJavaFile();
			File srcRoot = context.getRootFile();
			File destRoot = new File(srcRoot.getParentFile(), srcRoot.getName() + ".recycle");
			File destFile = new File(destRoot, file.getRelativeName());
			Path srcPath = srcFile.toPath();
			Path destPath = destFile.toPath();

			Trace.traceln(Trace.NORMAL, "Recycling " + srcFile.getAbsolutePath() + " to " + destFile.getAbsolutePath());

			if (!destFile.getParentFile().isDirectory()) {
				if (!destFile.getParentFile().mkdirs()) {
					Trace.traceln(Trace.NORMAL, "Can't create directory for '" + destFile.getAbsolutePath() + "'");
					continue;
				}
			}

			try {
				Files.move(srcPath, destPath);
			} catch (Exception e) {
				Trace.traceln(Trace.NORMAL, "Can't delete '" + srcFile.getAbsolutePath() + "'");
				Trace.traceln(Trace.NORMAL, "Exception: " + e);
				continue;
			}

			if (!srcFile.isFile()) {
				context.removeFile(file);
			}
		}

		this.widgets.browserTree.clearSelection();
// TODO		Database.instance().analyzeDuplicates();

		rebuildBrowseTree();
	}

	void toggleShowFiles() {
		boolean yesno = this.widgets.showFilesButton.isSelected();

		// TODO foo();
		this.widgets.saveExpandedState();
		this.controller.browserTreeModel.filter.showFiles(yesno);
		this.widgets.restoreExpandedState();
	}

	void toggleShowLocalDups() {
		boolean yesno = this.widgets.showLocalDupsButton.isSelected();

		// TODO foo();
		this.widgets.saveExpandedState();
		this.controller.browserTreeModel.filter.showLocalDuplicateFiles(yesno);
		this.controller.duplicateTreeModel.filter.showLocalDuplicateFiles(yesno);
		this.widgets.restoreExpandedState();
	}

	void toggleShowGlobalDups() {
		boolean yesno = this.widgets.showGlobalDupsButton.isSelected();

		// TODO foo();
		this.widgets.saveExpandedState();
		this.controller.browserTreeModel.filter.showGlobalDuplicateFiles(yesno);
		this.controller.duplicateTreeModel.filter.showGlobalDuplicateFiles(yesno);
		this.widgets.restoreExpandedState();
	}

	void toggleShowEmptyFolders() {
		boolean yesno = this.widgets.showEmptyFoldersButton.isSelected();

		// TODO foo();
		this.widgets.saveExpandedState();
		this.controller.browserTreeModel.filter.showEmptyFolders(yesno);
		this.widgets.restoreExpandedState();
	}

	void toggleShowUnique() {
		boolean yesno = this.widgets.showUniqueButton.isSelected();

		// TODO foo();
		this.widgets.saveExpandedState();
		this.controller.browserTreeModel.filter.showUniqueFiles(yesno);
		this.widgets.restoreExpandedState();
	}

	public void updateProgress(int percent) {
		this.widgets.updateProgress(percent);
	}

	private Widgets widgets;
	private Controller controller;

	private Object[] browseTreeSelection = null;
	private Object[] dupTreeSelection = null;
}
