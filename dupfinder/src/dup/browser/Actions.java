package dup.browser;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Icon;

import dup.analyze.DetailLevel;
import dup.model.Context;
import dup.model.Database;
import dup.model.FileInfo;
import dup.util.Utility;

@SuppressWarnings("serial")
class Actions {
	public Actions(View view) {
		this.view = view;

		this.openContextAction = new OpenAction();
		this.saveContextAction = new SaveAction(new Object[0]);
		this.closeContextAction = new CloseAction(new Object[0]);
		this.closeAllContextsAction = new CloseAllAction();
		this.recycleAction = new RecycleAction();
		this.recycleGDUPAction = new RecycleGDUPAction();
		this.showFilesAction = new ShowFilesAction();
		this.showEmptyFoldersAction = new ShowEmptyFoldersAction();
		this.showUniqueAction = new ShowUniqueAction();
		this.showLocalDupsAction = new ShowLocalDupsAction();
		this.showGlobalDupsAction = new ShowGlobalDupsAction();
		this.loadPrefixChecksumAction = new LoadPrefixChecksumAction();
		this.loadSampleChecksumAction = new LoadSampleChecksumAction();
	}

	private void openContext() {
		this.view.openContext();
	}

	private void saveContext(Context context) {
		this.view.saveContext(context);
	}

	private void closeContext(Context context) {
		this.view.closeContext(context);
	}

	private void toggleShowFiles() {
		this.view.toggleShowFiles();
	}

	private void toggleShowUnique() {
		this.view.toggleShowUnique();
	}

	private void toggleShowGlobalDups() {
		this.view.toggleShowGlobalDups();
	}

	private void toggleShowEmptyFolders() {
		this.view.toggleShowEmptyFolders();
	}

	private void toggleShowLocalDups() {
		this.view.toggleShowLocalDups();
	}

	private void loadDetails(Object[] objects, DetailLevel detail) {
		this.view.loadDetails(objects, detail);
	}

	private void loadDetails(List<FileInfo> files, DetailLevel detail) {
		this.view.loadDetails(files, detail);
	}

	private void recycleSelectedFiles(List<FileInfo> files) {
		this.view.recycleSelectedFiles(files);
	}

	private void recycleGDUPSelectedFiles(List<FileInfo> files) {
		this.view.recycleGDUPSelectedFiles(files);
	}

	class CloseAction extends AbstractAction {
		Object[] selobjs;

		public CloseAction(Object[] selobjs) {
			super("Close", UIUtility.getIcon("imgClose_big", "Close"));

			this.selobjs = selobjs;
		}

		public void actionPerformed(ActionEvent e) {
			for (Object selobj : this.selobjs) {
				if (selobj instanceof Context) {
					closeContext((Context) selobj);
				}
			}
		}

		public void setSelection(Context context) {
			if (context != null) {
				this.selobjs = new Object[] { context };
				setEnabled(true);
			} else {
				setEnabled(false);
			}
		}
	}

	class CloseAllAction extends AbstractAction {
		public CloseAllAction() {
			super("CloseAll", UIUtility.getIcon("imgCloseAll_big", "CloseAll"));
		}

		public void actionPerformed(ActionEvent e) {
			for (Context context : Database.instance().getContexts().toArray(new Context[0])) {
				closeContext(context);
			}
		}
	}

	class OpenAction extends AbstractAction {
		public OpenAction() {
			super("Open", UIUtility.getIcon("imgOpen_big", "Open"));
		}

		public void actionPerformed(ActionEvent e) {
			openContext();
		}
	}

	class SaveAction extends AbstractAction {
		Object[] selobjs;

		public SaveAction(Object[] selobjs) {
			super("Save", UIUtility.getIcon("imgSave_big", "Save"));

			this.selobjs = selobjs;
		}

		public void actionPerformed(ActionEvent e) {
			for (Object selobj : this.selobjs) {
				if (selobj instanceof Context) {
					saveContext((Context) selobj);
				}
			}
		}

		public void setSelection(Context context) {
			if (context != null) {
				this.selobjs = new Object[] { context };
				setEnabled(context.isDirty());
			} else {
				setEnabled(false);
			}
		}
	}

	class ShowFilesAction extends AbstractAction {
		public ShowFilesAction() {
			super("Files", UIUtility.getIcon("imgFile", "Files"));
		}

		public void actionPerformed(ActionEvent e) {
			toggleShowFiles();
		}
	}

	class ShowGlobalDupsAction extends AbstractAction {
		public ShowGlobalDupsAction() {
			super("GDups", UIUtility.getIcon("imgGDup_big", "GDups"));
		}

		public void actionPerformed(ActionEvent e) {
			toggleShowGlobalDups();
		}
	}

	class ShowEmptyFoldersAction extends AbstractAction {
		public ShowEmptyFoldersAction() {
			super("Empty", UIUtility.getIcon("imgEmptyFolder", "Empty"));
		}

		public void actionPerformed(ActionEvent e) {
			toggleShowEmptyFolders();
		}
	}

	class ShowLocalDupsAction extends AbstractAction {
		public ShowLocalDupsAction() {
			super("LDups", UIUtility.getIcon("imgLDup_big", "LDups"));
		}

		public void actionPerformed(ActionEvent e) {
			toggleShowLocalDups();
		}
	}

	class ShowUniqueAction extends AbstractAction {
		public ShowUniqueAction() {
			super("Unique", UIUtility.getIcon("imgUniqueFile", "Unique"));
		}

		public void actionPerformed(ActionEvent e) {
			toggleShowUnique();
		}
	}

	class LoadDetailAction extends AbstractAction {
		Object[] selobjs;
		DetailLevel detail;

		public LoadDetailAction(Object[] selobjs, DetailLevel detail, DetailLevel current) {
			super("Load " + detail.toString(), //
					UIUtility.getIcon("imgLoad_big", "Load" + detail.toString()));

			this.selobjs = selobjs;
			this.detail = detail;

			setEnabled(detail.isGreaterThan(current));
		}

		public void actionPerformed(ActionEvent e) {
			loadDetails(this.selobjs, this.detail);
		}
	}

	abstract class AbstractSelectionAction extends AbstractAction {
		protected List<FileInfo> selfiles;

		public AbstractSelectionAction(String name, Icon icon) {
			super(name, icon);

			this.selfiles = new ArrayList<FileInfo>();
		}

		public AbstractSelectionAction(String name, Icon icon, List<FileInfo> selfiles) {
			super(name, icon);

			this.selfiles = selfiles;
		}

		public final void setSelection(Object[] selobjs) {
			setSelectedFiles(Utility.gatherFilesFromSelection(selobjs));
		}

		public void setSelectedFiles(List<FileInfo> files) {
			this.selfiles = files;
		}

		protected final DetailLevel getDetailLevelForSelection() {
			DetailLevel level = DetailLevel.Sample;

			if (this.selfiles != null) {
				for (FileInfo file : this.selfiles) {
					if (file.getDetailLevel().isLessThan(level)) {
						level = file.getDetailLevel();
					}
				}
			}

			return level;
		}
	}

	class LoadPrefixChecksumAction extends AbstractSelectionAction {
		public LoadPrefixChecksumAction() {
			super("LoadPrefixChecksum", UIUtility.getIcon("imgPrefix", "LoadPrefixChecksum"));
		}

		public void actionPerformed(ActionEvent arg0) {
			loadDetails(this.selfiles, DetailLevel.Prefix);
		}

		public void setSelectedFiles(List<FileInfo> files) {
			super.setSelectedFiles(files);

			setEnabled(isEnabled());
		}

		public boolean isEnabled() {
			DetailLevel current = getDetailLevelForSelection();

			return current.isLessThan(DetailLevel.Prefix);
		}
	}

	class LoadSampleChecksumAction extends AbstractSelectionAction {
		public LoadSampleChecksumAction() {
			super("LoadSampleChecksum", UIUtility.getIcon("imgSample", "LoadSampleChecksum"));
		}

		public LoadSampleChecksumAction(List<FileInfo> selfiles) {
			super("LoadSampleChecksum", UIUtility.getIcon("imgSample", "LoadSampleChecksum"), selfiles);
		}

		public void actionPerformed(ActionEvent arg0) {
			loadDetails(this.selfiles, DetailLevel.Sample);
		}

		public void setSelectedFiles(List<FileInfo> files) {
			super.setSelectedFiles(files);

			setEnabled(isEnabled());
		}

		public boolean isEnabled() {
			DetailLevel current = getDetailLevelForSelection();

			return current.isLessThan(DetailLevel.Sample);
		}
	}

	class RecycleAction extends AbstractSelectionAction {
		public RecycleAction() {
			super("Recycle", UIUtility.getIcon("imgRecycle", "Recycle"));
		}

		public RecycleAction(List<FileInfo> selfiles) {
			super("Recycle", UIUtility.getIcon("imgRecycle", "Recycle"), selfiles);
		}

		public void actionPerformed(ActionEvent e) {
			recycleSelectedFiles(this.selfiles);
		}
	}

	class RecycleGDUPAction extends AbstractSelectionAction {
		public RecycleGDUPAction() {
			super("Recycle GDUP", UIUtility.getIcon("imgRecycleGDUP", "RecycleGDUP"));
		}

		public RecycleGDUPAction(List<FileInfo> selfiles) {
			super("Recycle GDUP", UIUtility.getIcon("imgRecycleGDUP", "RecycleGDUP"), selfiles);
		}

		public void actionPerformed(ActionEvent e) {
			recycleGDUPSelectedFiles(this.selfiles);
		}
	}

	View view;

	Action openContextAction;
	SaveAction saveContextAction;
	CloseAction closeContextAction;
	CloseAllAction closeAllContextsAction;

	LoadPrefixChecksumAction loadPrefixChecksumAction;
	LoadSampleChecksumAction loadSampleChecksumAction;

	RecycleAction recycleAction;
	RecycleGDUPAction recycleGDUPAction;

	Action showEmptyFoldersAction;
	Action showUniqueAction;
	Action showLocalDupsAction;
	Action showGlobalDupsAction;
	Action showFilesAction;
}