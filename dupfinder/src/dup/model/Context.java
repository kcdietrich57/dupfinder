package dup.model;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import dup.analyze.DuplicateChain;
import dup.util.FileUtil;
import dup.util.Trace;
import dup.util.Utility;

public class Context {
	private String name; // TODO Context name (currenty folder)
	private String version; // Version of the saved data format
	private File persistenceFile; // The file containing saved context metadata
	private boolean dirty; // Changes exist; context should be saved
	private DetailLevel detailLevel; // How much information has been gathered

	private File rootFolderFile; // Java file for the context root
	private FolderInfo rootFolder; // Descriptor of the context root

	private int filecount; // Number of files within the context
	private int uniquecount; // Number of unique files within the context
	private long totalsize; // Bytes in all files

	private List<FileInfo> allFiles; // List of files in context, sorted by size
	private List<DuplicateChain> localDuplicates; // Duplicates in this context

	private int dupcount;
	private DuplicateChain maxchain;
	private long waste;
	private DuplicateChain maxwaste;

	public final String mutex = "Context.mutex";

	private Context() {
		this.persistenceFile = null;
		this.version = null;
		this.dirty = false;
		this.detailLevel = DetailLevel.None;
		this.rootFolderFile = null;
		this.rootFolder = null;

		this.allFiles = null;
		this.localDuplicates = new ArrayList<DuplicateChain>();

		this.dupcount = 0;
		this.maxchain = null;
		this.waste = 0L;
		this.maxwaste = null;
	}

	/** Construct a context from filesystem or saved data */
	public Context(String rootFolderPath, String name) {
		this();

		this.rootFolderFile = new File(rootFolderPath);
		this.name = name;
		this.dirty = false;

		this.filecount = 0;
		this.uniquecount = 0;
	}

	/** Create a dummy clone context */
	public Context(Context other) {
		this();

		this.name = other.name;
		this.version = other.version;
		this.dirty = false;
		this.detailLevel = other.detailLevel;
		this.rootFolderFile = other.rootFolderFile;
		this.rootFolder = new FolderInfo(null, this.rootFolderFile);
	}

	public void close() {
		restartAnalysis();

		for (FileInfo f : this.allFiles.toArray(new FileInfo[0])) {
			f.dispose();
		}

		this.allFiles = null;
		this.rootFolder = null;
	}

	private void restartAnalysis() {
		this.filecount = 0;
		this.uniquecount = 0;
		this.dupcount = 0;

		this.localDuplicates.clear();

		clearDupCount(this.rootFolder);
		clearDupFileInfo();
	}

	private void clearDupCount(FolderInfo folder) {
		folder.dupcount = 0;

		for (FolderInfo subfolder : folder.getSubfolders()) {
			clearDupCount(subfolder);
		}
	}

	private void clearDupFileInfo() {
		Iterator<FileInfo> iter = this.rootFolder.iterateFiles(true);
		while (iter.hasNext()) {
			FileInfo file = iter.next();

			file.clearDuplicateInfoForRestart();
		}
	}

	public int getFileCount(boolean determine) {
		if (determine) {
			determineCurrentFileCount();
		}

		return this.filecount;
	}

	public void determineCurrentFileCount() {
		if ((this.filecount <= 0) && (this.rootFolder != null)) {
			this.filecount = this.rootFolder.getTreeFileCount();
		}
	}

	public DetailLevel determineDetailLevel() {
		DetailLevel detail = DetailLevel.Sample;

		if (this.rootFolder != null) {
			Iterator<FileInfo> iter = this.rootFolder.iterateFiles(true);
			while (iter.hasNext()) {
				FileInfo file = iter.next();

				if (file.getDetailLevel().isLessThan(detail)) {
					detail = file.getDetailLevel();
				}
			}
		}

		return detail;
	}

	public FolderInfo getRoot() {
		if (this.rootFolder == null) {
			this.rootFolder = new FolderInfo(null, this.rootFolderFile);
		}

		return this.rootFolder;
	}

	public void removeFile(FileInfo file) {
		FolderInfo folder = file.getFolder();

		file.removeFromDuplicateChains();
		folder.removeFile(file);
		this.allFiles.remove(file);
		--this.filecount;

		setDirty();
	}

	public FileInfo findFile(FileInfo finfo) {
		FolderInfo folder = findFolder(finfo.getFolder().getRelativeJavaFile());
		if (folder == null) {
			return null;
		}

		return folder.findFile(finfo.getName());
	}

	public FolderInfo findFolder(File folderFile) {
		return findFolder(folderFile, false);
	}

	public FolderInfo findOrCreateFolder(File folderFile) {
		return findFolder(folderFile, true);
	}

	private FolderInfo findFolder(File folderFile, boolean create) {
		if ((folderFile == null) || ("".equals(folderFile.getName()))) {
			return this.rootFolder;
		}

		if (folderFile.equals(this.rootFolderFile)) {
			return this.rootFolder;
		}

		File parentFile = folderFile.getParentFile();
		if (parentFile == null) {
			return null;
		}

		FolderInfo parentFolder = null;

		if (parentFile == getRootFile()) {
			parentFolder = getRoot();
		} else {
			parentFolder = findFolder(parentFile, create);
		}

		if (parentFolder == null) {
			return null;
		}

		FolderInfo folder = parentFolder.findSubfolder(folderFile.getName());

		if (create && (folder == null)) {
			folder = new FolderInfo(parentFolder, folderFile);
			parentFolder.addFolder(folder);
		}

		return folder;
	}

	public int ingest() {
		if (this.detailLevel.isLessThan(DetailLevel.Size)) {
			return FileUtil.ingestContext(this, DetailLevel.Size);
		}

		return getRoot().getTreeFileCount();
	}

	public DuplicateChain getSameSizeFileChain(FileInfo file) {
		if (this.allFiles.isEmpty()) {
			return null;
		}

		int start = 0;
		int end = this.allFiles.size();
		int probe = -1;

		for (;;) {
			int mid = (end + start) / 2;

			if (mid == probe) {
				++probe;
			} else {
				probe = mid;
			}

			if (probe >= end) {
				return null;
			}

			FileInfo probefile = this.allFiles.get(probe);

			long diff = probefile.getSize() - file.getSize();
			if (diff == 0) {
				break;
			}

			if (diff < 0) {
				start = probe + 1;
			} else {
				end = probe;
			}
		}

		for (; probe > 0; --probe) {
			FileInfo probefile = this.allFiles.get(probe - 1);

			if (probefile.getSize() != file.getSize()) {
				break;
			}
		}

		DuplicateChain chain = new DuplicateChain(this.allFiles.get(probe));

		for (; probe < this.allFiles.size(); ++probe) {
			FileInfo probefile = this.allFiles.get(probe);

			if (probefile.getSize() != file.getSize()) {
				break;
			}

			chain.addFile(probefile);
		}

		return chain;
	}

	public void analyzeContextDuplicates() {
		int level = Trace.NORMAL;

		restartAnalysis();

		this.filecount = this.rootFolder.getTreeFileCount();
		this.totalsize = getRoot().getTreeSize();

		Trace.traceln(level);
		Trace.traceln(level, "Analyzing duplicates for context " + getName());
		Trace.traceln(level, " Total files:  " + this.filecount //
				+ " size=" + Utility.formatSize(this.totalsize));

		Trace.traceln(level);
		Trace.traceln(level, "Analyzing file sizes...");
		analyzeFileSize();

		dumpDuplicateChains(level, "Based on file size");

		Trace.traceln(level);
		Trace.traceln(level, "Checking for context duplicates...");
		gatherDuplicateFiles();

		Trace.traceln(level);
		Trace.traceln(level, "Rebuilding context duplicates...");
		rebuildContextDuplicates();

		dumpDuplicateChains(Trace.NORMAL, "Based on cksum/comparison");

		for (FileInfo file : this.allFiles) {
			if (!file.isUnique()) {
				++file.getFolder().dupcount;
			}
		}
	}

	/** Put files with the same size into DuplicateChains. */
	private void analyzeFileSize() {
		this.allFiles = new ArrayList<FileInfo>(this.filecount);

		Iterator<FileInfo> iter = this.rootFolder.iterateFiles(true);
		while (iter.hasNext()) {
			FileInfo file = iter.next();

			this.allFiles.add(file);
		}

		Collections.sort(this.allFiles, compareFileSize);

		for (int ii = 0; ii < this.filecount;) {
			FileInfo file = this.allFiles.get(ii);

			int lastidx = ii + 1;

			while ((lastidx < this.filecount) //
					&& (this.allFiles.get(lastidx).getSize() == file.getSize())) {
				++lastidx;
			}

			int sscount = lastidx - ii;

			if (sscount == 1) {
				++this.uniquecount;
			} else {
				DuplicateChain newchain = new DuplicateChain();

				for (int di = ii; di < lastidx; ++di) {
					newchain.addFile(this.allFiles.get(di));
				}

				this.localDuplicates.add(newchain);
			}

			ii = lastidx;
		}
	}

	/**
	 * For each DuplicateChain containing files with the same size in this context,
	 * analyze the files further. Link duplicate files together so each item in the
	 * chain will be a set of linked identical files.
	 */
	private void gatherDuplicateFiles() {
		countDuplicates();

		long msDeadline = System.currentTimeMillis() + 2000;
		int numFilesToProcess = this.dupcount;
		int numFilesRemaining = this.dupcount;
		int numchains = this.localDuplicates.size();

		for (int idx = 0; idx < this.localDuplicates.size(); ++idx) {
			DuplicateChain chain = this.localDuplicates.get(idx);

			if (System.currentTimeMillis() >= msDeadline) {
				Trace.traceln(Trace.NORMAL, //
						"Processing chain " + idx + "/" + numchains //
								+ " (" + chain.getNumFiles() + ")" //
								+ " " + numFilesRemaining //
								+ " of " + numFilesToProcess + " files remaining");
				msDeadline += 2000;
			}

			for (int ii = 0; ii < chain.getNumFiles(); ++ii) {
				FileInfo f1 = chain.getFileInfo(ii);
				--numFilesRemaining;

				for (int jj = ii + 1; jj < chain.getNumFiles();) {
					FileInfo f2 = chain.getFileInfo(jj);

					if (f1.addFileToDuplicateChain(f2)) {
						chain.removeFile(jj);
						--numFilesRemaining;
					} else {
						++jj;
					}
				}
			}
		}
	}

	private void rebuildContextDuplicates() {
		List<DuplicateChain> verifiedDuplicateChains = new ArrayList<DuplicateChain>();

		for (DuplicateChain chain : this.localDuplicates) {
			for (int ii = 0; ii < chain.getNumFiles(); ++ii) {
				FileInfo f1 = chain.getFileInfo(ii);

				if (!f1.hasLocalDuplicates()) {
					chain.removeFile(ii);
					++this.uniquecount;
					continue;
				}

				DuplicateChain newchain = new DuplicateChain();
				verifiedDuplicateChains.add(newchain);

				for (FileInfo dupf : f1.getContextDuplicates()) {
					newchain.addFile(dupf);
				}
			}
		}

		this.localDuplicates.clear();
		this.localDuplicates.addAll(verifiedDuplicateChains);
	}

	private void dumpDuplicateChains(int level, String title) {
		countDuplicates();

		Trace.traceln(level, title);

		Trace.traceln(level, " Unique: " + this.uniquecount);

		Trace.trace(level, " Dups: " + this.dupcount);
		Trace.trace(level, " in " + this.localDuplicates.size() + " groups");

		if (this.maxchain != null) {
			Trace.trace(level, ", Largest: files=" + this.maxchain.getNumFiles());
			Trace.trace(level, " waste= " + Utility.formatSize(this.maxchain.getWaste()));
		}

		Trace.traceln(level);

		float wasteRatio = ((float) this.waste) / this.totalsize;
		Trace.trace(level, " Waste " + Utility.formatSize(this.waste) //
				+ "(" + Utility.formatPercent(wasteRatio) + "%)");

		if (this.maxwaste != null) {
			Trace.trace(level, ", Largest: files=" + this.maxwaste.getNumFiles());
			Trace.trace(level, " waste=: " + Utility.formatSize(this.maxwaste.getWaste()));
		}

		Trace.traceln(level);
	}

	private void countDuplicates() {
		this.dupcount = 0;
		this.maxchain = null;
		this.waste = 0L;
		this.maxwaste = null;

		for (DuplicateChain chain : this.localDuplicates) {
			this.dupcount += chain.getNumFiles();
			this.waste += chain.getWaste();

			if ((this.maxchain == null) //
					|| (chain.getNumFiles() > this.maxchain.getNumFiles())) {
				this.maxchain = chain;
			}

			if ((this.maxwaste == null) //
					|| (chain.getWaste() > this.maxwaste.getWaste())) {
				this.maxwaste = chain;
			}
		}
	}

	public static final Comparator<FileInfo> compareFileSize;

	static {
		compareFileSize = new Comparator<FileInfo>() {
			@Override
			public int compare(FileInfo f1, FileInfo f2) {
				long diff = f1.getSize() - f2.getSize();

				return (diff == 0) ? 0 : (diff < 0) ? -1 : 1;
			}
		};
	}

	public String getVersion() {
		return this.version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public List<FileInfo> getAllFiles() {
		return this.allFiles;
	}

	public int getUniqueFileCount() {
		return this.uniquecount;
	}

	public DetailLevel getDetailLevel() {
		return this.detailLevel;
	}

	public void setDetailLevel(DetailLevel detail) {
		this.detailLevel = detail;
	}

	public String getName() {
		return this.name;
	}

	public boolean isDirty() {
		return this.dirty;
	}

	public void setDirty() {
		setDirty(true);
	}

	public void setDirty(boolean isDirty) {
		this.dirty = isDirty;
	}

	public File getRootFile() {
		return this.rootFolderFile;
	}
}
