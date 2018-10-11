package dup.browser;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Action;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.JTree;
import javax.swing.SwingConstants;
import javax.swing.border.BevelBorder;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;

import dup.model.Context;
import dup.model.DetailLevel;
import dup.model.FileInfo;
import dup.model.FolderInfo;
import dup.model.ModelUtil;
import dup.util.Trace;
import dup.util.Utility;

class Widgets {
	public Widgets(View view) {
		this.view = view;

		this.actions = new Actions(view);
	}

	public Object[] getSelectedBrowseTreeNodes() {
		int selcount = this.browserTree.getSelectionCount();
		Object[] sel = new Object[selcount];

		if (selcount > 0) {
			int idx = 0;
			for (TreePath path : this.browserTree.getSelectionPaths()) {
				sel[idx++] = path.getLastPathComponent();
			}
		}

		return sel;
	}

	public Object[] getSelectedDupTreeNodes() {
		int selcount = this.duplicateTree.getSelectionCount();
		Object[] selobjs = new Object[selcount];

		if (selcount > 0) {
			int idx = 0;
			for (TreePath path : this.duplicateTree.getSelectionPaths()) {
				selobjs[idx++] = path.getLastPathComponent();
			}
		}

		return selobjs;
	}

	public File openContextDialog() {
		if (this.openDlg == null) {
			this.openDlg = new JFileChooser("c:/data");

			this.openDlg.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		}

		int returnVal = this.openDlg.showOpenDialog(this.mainSplit);

		return (returnVal == JFileChooser.APPROVE_OPTION) ? this.openDlg.getSelectedFile() : null;
	}

	private JPopupMenu createContextPopup(Object[] selobjs) {
		DetailLevel minDetail = DetailLevel.Sample;

		minDetail = ModelUtil.getMinimumDetailLevel(selobjs);

		List<Action> items = new ArrayList<Action>();

		JPopupMenu popup = new JPopupMenu();

		items.add(this.actions.new LoadDetailAction(selobjs, DetailLevel.Size, minDetail));
		items.add(this.actions.new LoadDetailAction(selobjs, DetailLevel.Prefix, minDetail));
		items.add(this.actions.new LoadDetailAction(selobjs, DetailLevel.Sample, minDetail));

		for (Action item : items) {
			popup.add(item);
		}

		popup.add(this.actions.new CloseAction(selobjs));

		return popup;
	}

	private JPopupMenu createFilePopup(Object[] selobjs) {
		DetailLevel minDetail = ModelUtil.getMinimumDetailLevel(selobjs);

		List<Action> items = new ArrayList<Action>();

		JPopupMenu popup = new JPopupMenu();

		items.add(this.actions.new LoadDetailAction(selobjs, DetailLevel.Size, minDetail));
		items.add(this.actions.new LoadDetailAction(selobjs, DetailLevel.Prefix, minDetail));
		items.add(this.actions.new LoadDetailAction(selobjs, DetailLevel.Sample, minDetail));

		if (!items.isEmpty()) {
			for (Action item : items) {
				popup.add(item);
			}

			popup.add(new JSeparator());
		}

		List<FileInfo> selfiles = Utility.gatherFilesFromSelection(selobjs);

		popup.add(this.actions.new RecycleAction(selfiles));
		popup.add(this.actions.new RecycleGDUPAction(selfiles));

		return popup;
	}

	public void updateProgress(int percent) {
		if (percent < 0) {
			this.progressBar.setVisible(false);
		} else {
			this.progressBar.setValue(percent);
			this.progressBar.setVisible(true);

			this.progressBar.repaint();
		}
	}

	public void createUI() {
		createToolBar();
		createStatusBar();

		createBrowserTree();
		this.view.setDatabaseModel();
		createDuplicateTree();

		this.preview = new JLabel("Preview");
		this.preview.setFont(this.preview.getFont().deriveFont(Font.ITALIC));
		this.preview.setHorizontalAlignment(SwingConstants.CENTER);

		layOutComponents();

		updateUIState();
	}

	private void createStatusBar() {
		this.progressBar = new JProgressBar(0, 100);
		this.progressBar.setValue(0);
		this.progressBar.setStringPainted(true);

		this.statusBar = new JPanel();
		this.statusBar.setBorder(new BevelBorder(BevelBorder.LOWERED));
	}

	class BrowseTreeSelectionListener implements TreeSelectionListener {
		public void valueChanged(TreeSelectionEvent e) {
			Widgets.this.view.browseTreeSelectionChanged();
		}
	}

	class DupTreeSelectionListener implements TreeSelectionListener {
		public void valueChanged(TreeSelectionEvent e) {
			Widgets.this.view.dupTreeSelectionChanged();
		}
	}

	private void createBrowserTree() {
		this.browserTree = new JTree();
		this.browserTree.setShowsRootHandles(true);

		FolderTreeModel model = new FolderTreeModel(this.browserTree);

		this.view.setBrowserTreeModel(model);

		this.browserTree.setModel(model);
		this.browserTree.setCellRenderer(new FoldersTreeCellRenderer());
		this.browserTree.setSelectionRow(0);

		this.browserTree.addTreeSelectionListener( //
				new BrowseTreeSelectionListener());
		this.browserTree.addMouseListener( //
				new TreePopupListener(this.browserTree));
	}

	private void layOutComponents() {
		this.treeSplit1 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, //
				new JScrollPane(this.browserTree), //
				new JScrollPane(this.duplicateTree));
		this.treeSplit1.setDividerLocation(250);

		this.detailSplit2 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, //
				new JLabel("File List"), //
				new JLabel("Duplicate List"));
		this.detailSplit1 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, //
				new JScrollPane(this.preview), //
				this.detailSplit2);

		this.mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, //
				this.treeSplit1, //
				this.detailSplit1);
		this.mainSplit.setOneTouchExpandable(true);
		this.mainSplit.setDividerLocation(750);

		this.statusBar.setPreferredSize(new Dimension(100, 16));
		this.statusBar.setLayout(//
				new BoxLayout(Widgets.this.statusBar, BoxLayout.X_AXIS));

		this.statusBar.add("Progress", this.progressBar);
	}

	private void addToolBarButtons() {
		this.recycleButton = new JButton(this.actions.recycleAction);
		this.recycleButton.setHideActionText(true);
		this.recycleButton.setToolTipText("Recycle selected file(s)");

		this.recycleGDUPButton = new JButton(this.actions.recycleGDUPAction);
		this.recycleGDUPButton.setHideActionText(true);
		this.recycleGDUPButton.setToolTipText("Recycle global duplicates for selected file(s)");

		this.toolBar.addSeparator();

		this.loadPrefixChecksumButton = new JButton( //
				this.actions.loadPrefixChecksumAction);
		this.loadPrefixChecksumButton.setHideActionText(true);
		this.loadPrefixChecksumButton.setToolTipText("Load prefix checksum for selected file(s)");

		this.loadSampleChecksumButton = new JButton( //
				this.actions.loadSampleChecksumAction);
		this.loadSampleChecksumButton.setHideActionText(true);
		this.loadSampleChecksumButton.setToolTipText("Load sample checksum for selected file(s)");

		this.toolBar.addSeparator();

		this.showFilesButton = new JToggleButton( //
				this.actions.showFilesAction);
		this.showFilesButton.setHideActionText(true);
		this.showFilesButton.setToolTipText("Show files in browse tree");

		this.showEmptyFoldersButton = new JToggleButton( //
				this.actions.showEmptyFoldersAction);
		this.showEmptyFoldersButton.setHideActionText(true);
		this.showEmptyFoldersButton.setToolTipText("Show empty folders");

		this.showUniqueButton = new JToggleButton( //
				this.actions.showUniqueAction);
		this.showUniqueButton.setHideActionText(true);
		this.showUniqueButton.setToolTipText("Show unique files");

		this.showLocalDupsButton = new JToggleButton( //
				this.actions.showLocalDupsAction);
		this.showLocalDupsButton.setToolTipText("Show context duplicate files");

		this.showGlobalDupsButton = new JToggleButton( //
				this.actions.showGlobalDupsAction);
		this.showGlobalDupsButton.setToolTipText("Show globally duplicate files");

		this.toolBar.add(this.loadPrefixChecksumButton);
		this.toolBar.add(this.loadSampleChecksumButton);

		this.toolBar.addSeparator();

		this.toolBar.add(this.actions.openContextAction);
		this.toolBar.add(this.actions.saveContextAction);
		this.toolBar.add(this.actions.closeContextAction);
		this.toolBar.add(this.actions.closeAllContextsAction);

		this.toolBar.addSeparator();

		this.toolBar.add(this.recycleButton);
		this.toolBar.add(this.recycleGDUPButton);

		this.toolBar.addSeparator();

		this.toolBar.add(this.showFilesButton);
		this.toolBar.add(this.showEmptyFoldersButton);

		this.toolBar.addSeparator();

		this.toolBar.add(this.showUniqueButton);
		this.toolBar.add(this.showLocalDupsButton);
		this.toolBar.add(this.showGlobalDupsButton);

		this.toolBar.addSeparator();
	}

	public void updateToolbarEnablement(Object[] selection) {
		List<FileInfo> selectedFiles = Utility.gatherFilesFromSelection(selection);

		this.actions.recycleAction.setSelection(selection);
		this.actions.recycleGDUPAction.setSelection(selection);

		this.actions.loadPrefixChecksumAction.setSelectedFiles(selectedFiles);
		this.actions.loadSampleChecksumAction.setSelectedFiles(selectedFiles);
	}

	private List<TreePath> getExpandedNodes(JTree tree) {
		List<TreePath> expandedNodes = new ArrayList<TreePath>();

		int rowCount = tree.getRowCount();

		for (int ii = 0; ii < rowCount; ++ii) {
			TreePath path = tree.getPathForRow(ii);

			if (tree.isExpanded(path)) {
				expandedNodes.add(path);
			}
		}

		return expandedNodes;
	}

	private void createDuplicateTree() {
		this.duplicateTree = new JTree();
		this.duplicateTree.setShowsRootHandles(true);

		FolderTreeModel dupModel = new FolderTreeModel(this.duplicateTree);

		this.view.setDuplicateTreeModel(dupModel);

		dupModel.setRoot(null);
		dupModel.filter.showEmptyFolders(false);

		this.duplicateTree.setModel(dupModel);
		this.duplicateTree.setCellRenderer(new FoldersTreeCellRenderer());

		this.duplicateTree.addTreeSelectionListener(new DupTreeSelectionListener());
		this.duplicateTree.addMouseListener(new TreePopupListener(this.duplicateTree));
	}

	private void createToolBar() {
		this.toolBar = new JToolBar("Main");
		this.toolBar.setFloatable(false);
		this.toolBar.setRollover(true);

		addToolBarButtons();
	}

	public void updateUIState() {
		FolderTreeModel model = this.view.getBrowserTreeModel();
		Object[] selobjs = this.view.getBrowserTreeSelection();
		TreeModelFilter filter = model.getModelFilter();

		this.showFilesButton.setSelected(filter.showFiles());
		this.showEmptyFoldersButton.setSelected(filter.showEmptyFolders());
		this.showUniqueButton.setSelected(filter.showUniqueFiles());
		this.showLocalDupsButton.setSelected(filter.showLocalDuplicateFiles());
		this.showGlobalDupsButton.setSelected(filter.showGlobalDuplicateFiles());

		Context selectedContext = getSelectedContext(selobjs);

		this.actions.closeContextAction.setSelection(selectedContext);
		this.actions.saveContextAction.setSelection(selectedContext);

		this.actions.recycleAction.setSelection(selobjs);
		this.actions.recycleGDUPAction.setSelection(selobjs);
	}

	private Context getSelectedContext(Object[] selobjs) {
		return ((selobjs != null) //
				&& (selobjs.length > 0) //
				&& (selobjs[0] instanceof Context)) ? ((Context) selobjs[0]) : null;
	}

	class TreePopupListener extends MouseAdapter {
		JTree tree;

		public TreePopupListener(JTree tree) {
			this.tree = tree;
		}

		public void mousePressed(MouseEvent e) {
			maybeShowPopup(e);
		}

		public void mouseReleased(MouseEvent e) {
			maybeShowPopup(e);
		}

		protected void maybeShowPopup(MouseEvent e) {
			if (!e.isPopupTrigger()) {
				return;
			}

			TreePath path = this.tree.getClosestPathForLocation(e.getX(), e.getY());

			Object[] selobjs = null;

			if ((path != null) && !this.tree.isPathSelected(path)) {
				selobjs = new Object[] { path.getLastPathComponent() };
			} else {
				int[] selrows = this.tree.getSelectionRows();

				if ((selrows != null) && (selrows.length > 0)) {
					selobjs = new Object[selrows.length];

					for (int ii = 0; ii < selrows.length; ++ii) {
						TreePath selpath = this.tree.getPathForRow(selrows[ii]);
						selobjs[ii] = selpath.getLastPathComponent();
					}
				}
			}

			JPopupMenu popup = null;
			Object obj = (selobjs != null) ? selobjs[0] : null;

			if (obj instanceof Context) {
				if (this.tree == Widgets.this.browserTree) {
					popup = Widgets.this.createContextPopup(selobjs);
				}
			} else if ((obj instanceof FolderInfo) || (obj instanceof FileInfo)) {
				popup = Widgets.this.createFilePopup(selobjs);
			}

			if (popup != null) {
				popup.show(e.getComponent(), e.getX(), e.getY());
			}
		}
	}

	public void saveExpandedState() {
		this.expandedNodes = this.getExpandedNodes(this.browserTree);
		this.browserSelectionNodes = this.browserTree.getSelectionPaths();

		Trace.traceln(Trace.NORMAL);
		Trace.traceln(Trace.NORMAL, "Expanded nodes");

		if (this.expandedNodes != null) {
			Trace.traceln(Trace.NORMAL, this.expandedNodes.toString());
		}

		Trace.traceln(Trace.NORMAL, "Selected nodes");

		if (this.browserSelectionNodes != null) {
			Trace.traceln(Trace.NORMAL, this.browserSelectionNodes.toString());
		}
	}

	public void restoreExpandedState() {
		for (TreePath nodePath : this.expandedNodes) {
			this.browserTree.makeVisible(nodePath);
			this.browserTree.expandPath(nodePath);
		}

		if (this.browserSelectionNodes != null) {
			for (TreePath selpath : this.browserSelectionNodes) {
				this.browserTree.addSelectionPath(selpath);
			}
		}
	}

	View view;

	Actions actions;

	JToolBar toolBar;
	JTree browserTree;
	JTree duplicateTree;
	JLabel preview;
	JSplitPane mainSplit;
	JSplitPane treeSplit1;
	JSplitPane detailSplit1;
	JSplitPane detailSplit2;

	JPanel statusBar;
	JProgressBar progressBar;

	JToggleButton showEmptyFoldersButton;
	JToggleButton showUniqueButton;
	JToggleButton showLocalDupsButton;
	JToggleButton showGlobalDupsButton;
	JToggleButton showFilesButton;

	JButton recycleButton;
	JButton recycleGDUPButton;

	JButton loadPrefixChecksumButton;
	JButton loadSampleChecksumButton;

	JFileChooser openDlg;

	private TreePath[] browserSelectionNodes = null;
	private List<TreePath> expandedNodes = null;
}