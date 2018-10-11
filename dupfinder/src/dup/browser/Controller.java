package dup.browser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.swing.SwingUtilities;

import dup.model.Context;
import dup.model.Database;
import dup.model.DetailLevel;
import dup.model.FileInfo;
import dup.model.FolderInfo;

public class Controller {
	Thread loadDetailsThread = new Thread() {
		@Override
		public void run() {
			jobSchedulerTask();
		}
	};

	public Controller() {
		this.loadDetailsThread.start();
	}

	public void setView(View view) {
		this.view = view;
	}

	private void jobSchedulerTask() {
		for (;;) {
			synchronized (this.loadDetailsJobs) {
				try {
					if ((this.currJob != null) || this.loadDetailsJobs.isEmpty()) {
						this.loadDetailsJobs.wait(100);
					} else {
						this.currJob = this.loadDetailsJobs.remove(0);
						this.currJob.run();
						this.currJob = null;

						updateProgress();
					}
				} catch (InterruptedException e) {
					// go on
				}
			}
		}
	}

	void loadDetails(List<FileInfo> files, DetailLevel detail) {
		loadDetails(files.toArray(), detail);
	}

	void loadDetails(Object[] objects, DetailLevel detail) {
		Set<FileInfo> filesToLoad = new HashSet<FileInfo>();
		long totalBytes = 0;

		for (Object selobj : objects) {
			if (selobj instanceof Context) {
				Context context = ((Context) selobj);

				selobj = context.getRoot();
				context.ingest();
			}

			if (selobj instanceof FolderInfo) {
				Iterator<FileInfo> iter = ((FolderInfo) selobj).iterateFiles(true);

				while (iter.hasNext()) {
					FileInfo file = iter.next();

					totalBytes += addFileAndDuplicatesForLoad( //
							filesToLoad, file, detail);
				}
			} else if (selobj instanceof FileInfo) {
				FileInfo file = (FileInfo) selobj;

				totalBytes += addFileAndDuplicatesForLoad( //
						filesToLoad, file, detail);
			}
		}

		if (filesToLoad.isEmpty()) {
			return;
		}

		synchronized (Controller.this.loadDetailsJobs) {
			this.loadDetailsJobs.add(new LoadDetailsJob(filesToLoad, detail, totalBytes));
		}
	}

	private long addFileAndDuplicatesForLoad(Collection<FileInfo> filesToLoad, FileInfo file, DetailLevel detail) {
		int filecount = filesToLoad.size();

		FileInfo origfile = Database.instance().findFile(file);

		if (!origfile.isUnique() && origfile.getDetailLevel().isLessThan(detail)) {
			filesToLoad.add(origfile);
		}

		for (FileInfo dfile : origfile.getContextDuplicates()) {
			if (dfile.getDetailLevel().isLessThan(detail)) {
				filesToLoad.add(dfile);
			}
		}

		for (FileInfo dfile : origfile.getGlobalDuplicates()) {
			if (dfile.getDetailLevel().isLessThan(detail)) {
				filesToLoad.add(dfile);
			}
		}

		return (filesToLoad.size() - filecount) * origfile.getSize();
	}

	private View view;

	FolderTreeModel browserTreeModel;
	FolderTreeModel duplicateTreeModel;

	private final List<LoadDetailsJob> loadDetailsJobs = new ArrayList<LoadDetailsJob>();

	LoadDetailsJob currJob = null;

	public long currJobBytes() {
		synchronized (this.loadDetailsJobs) {
			return (this.currJob != null) ? this.currJob.totalBytes : 0;
		}
	}

	public long processedCurJobBytes() {
		synchronized (this.loadDetailsJobs) {
			return (this.currJob != null) ? this.currJob.bytesProcessed : 0;
		}
	}

	public long totalLoadJobBytes() {
		long total = 0;

		synchronized (this.loadDetailsJobs) {
			if (this.currJob != null) {
				total = this.currJob.totalBytes;
			}

			for (LoadDetailsJob job : loadDetailsJobs) {
				total += job.totalBytes;
			}
		}

		return total;
	}

	public long processedLoadJobBytes() {
		long total = 0;

		synchronized (this.loadDetailsJobs) {
			if (this.currJob != null) {
				total = this.currJob.bytesProcessed;
			}

			for (LoadDetailsJob job : loadDetailsJobs) {
				total += job.bytesProcessed;
			}
		}

		return total;
	}

	protected void updateProgress() {
		int percent = -1;

		if ((this.currJob != null) && this.currJob.showProgress) {
			long work = currJobBytes();
			long progress = processedCurJobBytes();

			percent = (int) ((progress * 100) / work);
		}

		this.view.updateProgress(percent);
	}

	class LoadDetailsJob implements Runnable {
		Collection<FileInfo> filesToLoad;
		DetailLevel detail;
		long totalBytes;
		long bytesProcessed = 0;
		private boolean showProgress = true;

		public LoadDetailsJob(Collection<FileInfo> files, DetailLevel detail, long totalBytes) {
			this.filesToLoad = files;
			this.detail = detail;
			this.totalBytes = totalBytes;
		}

		public void setShowProgress(boolean yesno) {
			this.showProgress = yesno;
		}

		public void run() {
			Set<Context> changedContexts = new HashSet<Context>();

			for (FileInfo file : this.filesToLoad) {
				if (loadDetails(file, this.detail)) {
					changedContexts.add(file.getContext());
				}

				updateProgress();
			}

			for (Context context : changedContexts) {
				context.setDirty();
			}

			Database.instance().analyzeDuplicates();

			try {
				SwingUtilities.invokeAndWait(new Runnable() {
					public void run() {
						Controller.this.view.rebuildBrowseTree();
					}
				});
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		private boolean loadDetails(FileInfo file, DetailLevel detail) {
			if (!file.getDetailLevel().isLessThan(detail)) {
				return false;
			}

			Context context = file.getContext();

			file.calcChecksums(context, detail);
			this.bytesProcessed += file.getSize();
			context.setDirty();

			Controller.this.browserTreeModel.nodeChanged(file);

			return true;
		}
	}
}
