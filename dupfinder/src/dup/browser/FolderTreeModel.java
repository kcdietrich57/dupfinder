package dup.browser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.JTree;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

import dup.model.Context;
import dup.model.Database;
import dup.model.FileInfo;
import dup.model.FileObjectInfo;
import dup.model.FolderInfo;

class TreeModelFilter {
	private final FolderTreeModel model;

	public TreeModelFilter(FolderTreeModel model) {
		this.model = model;
	}

	public static final int SHOW_FILES = 0x01;
	public static final int SHOW_UNIQUE = 0x02;
	public static final int SHOW_LDUPS = 0x04;
	public static final int SHOW_GDUPS = 0x08;
	public static final int SHOW_LGDUPS = 0x10;
	public static final int SHOW_EMPTY_FOLDERS = 0x20;

	public static final int SHOW_ALL_FILES = 0x17;
	public static final int SHOW_ALL = 0x37;

	public int flags = SHOW_FILES | SHOW_LDUPS | SHOW_GDUPS | SHOW_LGDUPS;

	public Set<Object> selection = null;

	public int getFlags() {
		return this.flags;
	}

	public boolean showAll() {
		return SHOW_ALL == (this.flags & SHOW_ALL);
	}

	public boolean showAllFiles() {
		return SHOW_ALL == (this.flags & SHOW_ALL);
	}

	public boolean showFiles() {
		return ((this.flags & SHOW_FILES) != 0);
	}

	public boolean showUniqueFiles() {
		return ((this.flags & SHOW_UNIQUE) != 0);
	}

	public boolean showLocalDuplicateFiles() {
		return ((this.flags & SHOW_LDUPS) != 0);
	}

	public boolean showGlobalDuplicateFiles() {
		return ((this.flags & SHOW_GDUPS) != 0);
	}

	public boolean showBothDuplicateFiles() {
		return ((this.flags & SHOW_LGDUPS) != 0);
	}

	public boolean showEmptyFolders() {
		return ((this.flags & SHOW_EMPTY_FOLDERS) != 0);
	}

	public void showFiles(boolean yesno) {
		showItems(TreeModelFilter.SHOW_FILES, yesno);
	}

	public void showUniqueFiles(boolean yesno) {
		showItems(TreeModelFilter.SHOW_UNIQUE, yesno);
	}

	public void showLocalDuplicateFiles(boolean yesno) {
		showItems(TreeModelFilter.SHOW_LDUPS, yesno);
	}

	public void showGlobalDuplicateFiles(boolean yesno) {
		showItems(TreeModelFilter.SHOW_GDUPS, yesno);
	}

	public void showLocalGlobalFiles(boolean yesno) {
		showItems(TreeModelFilter.SHOW_LGDUPS, yesno);
	}

	public void showEmptyFolders(boolean yesno) {
		showItems(TreeModelFilter.SHOW_EMPTY_FOLDERS, yesno);
	}

	private void showItems(int itemType, boolean yesno) {
		boolean b = (getFlags() & itemType) != 0;
		if (b != yesno) {
			this.flags = (yesno) ? (this.flags | itemType) : (this.flags & ~itemType);

			this.model.structureChanged();
		}
	}

	public boolean isFileVisible(FileInfo file) {
		if (!showFiles()) {
			return false;
		}

		boolean b = false;

		if (showUniqueFiles() && file.isUnique()) {
			b = true;
		} else if (showLocalDuplicateFiles() && file.hasContextDuplicates()) {
			b = true;
		} else if (showGlobalDuplicateFiles() && file.hasGlobalDuplicates()) {
			b = true;
		}

		return b && isReachableFromSelection(file);
	}

	private boolean isReachableFromSelection(FileInfo file) {
		if ((this.selection == null) || this.selection.contains(file)) {
			return true;
		}

		for (FileInfo f : file.getContextDuplicates()) {
			if (selectionReaches(f)) {
				return true;
			}
		}

		for (FileInfo f : file.getGlobalDuplicates()) {
			if (selectionReaches(f)) {
				return true;
			}
		}

		return false;
	}

	private boolean selectionReaches(FileInfo file) {
		assert this.selection != null;

		for (Object o : this.selection) {
			if (o instanceof Database) {
				return true;
			}

			if (o == file) {
				return true;
			}

			if (o instanceof Context) {
				o = ((Context) o).getRoot();
			}

			if (o instanceof FolderInfo) {
				if (folderContains((FolderInfo) o, file)) {
					return true;
				}
			}
		}

		return false;
	}

	private boolean folderContains(FolderInfo fol, FileInfo file) {
		FolderInfo ffol = file.getFolder();

		while (ffol != null) {
			if (ffol == fol) {
				return true;
			}

			ffol = ffol.getFolder();
		}

		return false;
	}

	public void setSelection(Object[] selfiles) {
		this.selection = (selfiles == null) ? null : new HashSet<Object>(Arrays.asList(selfiles));
	}
}

public class FolderTreeModel implements TreeModel {
	private final JTree tree;
	private Object root;
	TreeModelFilter filter;

	public FolderTreeModel(JTree tree) {
		this.tree = tree;
		this.root = Database.instance();
		this.filter = new TreeModelFilter(this);
	}

	public TreeModelFilter getModelFilter() {
		return this.filter;
	}

	public Object getRoot() {
		return (this.root != null) ? this.root : "<make a selection>";
	}

	void setRoot(Object root) {
		if (this.root == root) {
			return;
		}

		this.root = root;
		structureChanged();
	}

	public void structureChanged() {
		if (this.listeners.isEmpty()) {
			return;
		}

		TreeModelEvent e = new TreeModelEvent(this.tree, new Object[] { getRoot() });

		TreeModelListener[] ll = this.listeners.toArray(new TreeModelListener[0]);

		for (TreeModelListener l : ll) {
			l.treeStructureChanged(e);
		}
	}

	public void nodeChanged(Object node) {
		if (this.listeners.isEmpty()) {
			return;
		}

		Object[] nodeObjects = null;
		TreePath nodePath = null;

		if (node instanceof Context) {
			nodeObjects = new Object[] { node };
		} else if (node instanceof FileObjectInfo) {
			nodeObjects = ((FileObjectInfo) node).getPathFromRoot();
		}

		nodePath = new TreePath(nodeObjects);

		TreePath parentPath = nodePath.getParentPath();
		int[] indices;
		Object[] nodes;

		if (parentPath == null) {
			// TODO I'm not sure how to do this
			parentPath = nodePath;
			nodePath = null;
			return;
		} else {
			int idx = getIndexOfChild(parentPath.getLastPathComponent(), node);

			indices = new int[] { idx };
			nodes = new Object[] { node };
		}

		TreeModelEvent ev = new TreeModelEvent(this.tree, parentPath, indices, nodes);

		TreeModelListener[] ll = this.listeners.toArray(new TreeModelListener[0]);

		for (TreeModelListener l : ll) {
			try {
				l.treeNodesChanged(ev);
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
		}
	}

	public int getChildCount(Object parent) {
		if (parent instanceof Database) {
			return getVisibleChildCount(Database.instance());
		}

		if (parent instanceof Context) {
			return getVisibleChildCount(((Context) parent).getRoot());
		}

		if (parent instanceof FolderInfo) {
			return getVisibleChildCount((FolderInfo) parent);
		}

		if (parent instanceof FileInfo) {
			return 0;
		}

		return 0;
	}

	public Object getChild(Object parent, int index) {
		Object child = "MISSING";

		if (parent instanceof Database) {
			return getVisibleChild(Database.instance(), index);
		} else if (parent instanceof Context) {
			child = getVisibleChild(((Context) parent).getRoot(), index);
		} else if (parent instanceof FolderInfo) {
			child = getVisibleChild((FolderInfo) parent, index);
		} else if (parent instanceof FileInfo) {
			child = "HUH? child of file?";
		}

		return child;
	}

	private int getVisibleChildCount(Database db) {
		int n = 0;

		n = db.getContexts().size();
		// for (Context context : db.getContexts()) {
		// if (getVisibleChildCount(context) > 0) {
		// ++n;
		// }
		// }

		return n;
	}

	private int getVisibleChildCount(Context context) {
		return getVisibleChildCount(context.getRoot());
	}

	private int getVisibleChildCount(FolderInfo folder) {
		int count = getVisibleFolderCount(folder) //
				+ getVisibleFileCount(folder);

		return count;
	}

	private Object getVisibleChild(Database db, int index) {
		List<Context> contexts = db.getContexts();

		return (contexts.size() > index) ? contexts.get(index) : null;

		// int n = 0;
		//
		// for (Context context : db.getContexts()) {
		// if (getVisibleChildCount(context) > 0) {
		// ++n;
		//
		// if (n > index) {
		// return context;
		// }
		// }
		// }
		//
		// return null;
	}

	private Object getVisibleChild(FolderInfo folder, int index) {
		Object child = null;

		List<FolderInfo> folders = getVisibleFolders(folder);

		if (folders.size() > index) {
			child = folders.get(index);
		} else {
			index -= folders.size();

			List<FileInfo> files = getVisibleFiles(folder);

			if (files.size() > index) {
				child = files.get(index);
			}
		}

		if (child == null) {
			return "MISSING";
		}

		return child;
	}

	private int getVisibleFolderCount(FolderInfo folder) {
		return getVisibleFolders(folder).size();
	}

	private int getVisibleFileCount(FolderInfo folder) {
		return getVisibleFiles(folder).size();
	}

	private List<FolderInfo> getVisibleFolders(FolderInfo parent) {
		if (this.filter.showAll()) {
			return parent.getSubfolders();
		}

		List<FolderInfo> folders = new ArrayList<FolderInfo>();

		for (FolderInfo folder : parent.getSubfolders()) {
			if (isFolderVisible(folder)) {
				folders.add(folder);
			}
		}

		Collections.sort(folders, (f1, f2) -> {
			int diff = f2.getDupCount() - f1.getDupCount();
			if (diff != 0) {
				return diff;
			}

			return f1.getName().compareTo(f2.getName());
		});
		return folders;
	}

	Comparator<FileObjectInfo> compareFiles = new Comparator<FileObjectInfo>() {
		public int compare(FileObjectInfo f1, FileObjectInfo f2) {
			return f1.getName().compareToIgnoreCase(f2.getName());
		}
	};

	private List<FileInfo> getVisibleFiles(FolderInfo folder) {
		if (this.filter.showAllFiles()) {
			return folder.getFiles();
		}

		List<FileInfo> files = new ArrayList<FileInfo>();

		if (this.filter.showFiles()) {
			for (FileInfo file : folder.getFiles()) {
				if (isFileVisible(file)) {
					files.add(file);
				}
			}
		}

		Collections.sort(files, compareFiles);

		return files;
	}

	private boolean isFolderVisible(FolderInfo folder) {
		if (this.filter.showEmptyFolders()) {
			return true;
		}

		for (FolderInfo subfolder : folder.getSubfolders()) {
			if (isFolderVisible(subfolder)) {
				return true;
			}
		}

		for (FileInfo file : folder.getFiles()) {
			if (isFileVisible(file)) {
				return true;
			}
		}

		return false;
	}

	private boolean isFileVisible(FileInfo file) {
		return this.filter.isFileVisible(file);
	}

	public int getIndexOfChild(Object parent, Object child) {
		// Not implemented
		return 0;
	}

	public boolean isLeaf(Object node) {
		return (node instanceof FileInfo);
	}

	public void valueForPathChanged(TreePath path, Object newValue) {
		// not implemented
	}

	List<TreeModelListener> listeners = new ArrayList<TreeModelListener>();

	public void addTreeModelListener(TreeModelListener l) {
		if (!this.listeners.contains(l)) {
			this.listeners.add(l);
		}
	}

	public void removeTreeModelListener(TreeModelListener l) {
		this.listeners.remove(l);
	}
}
