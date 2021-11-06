package dup.model;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import dup.analyze.DetailLevel;
import dup.util.FileUtil;
import dup.util.Trace;
import dup.util.Utility;

public class Context implements Comparable<Context> {
	public static final Comparator<FileInfo> compareFileSize;

	static {
		compareFileSize = new Comparator<FileInfo>() {
			public int compare(FileInfo f1, FileInfo f2) {
				long diff = f1.getSize() - f2.getSize();

				return (diff == 0) ? 0 : (diff < 0) ? -1 : 1;
			}
		};
	}

	private static int nextid = 1;

	public final int id;

	/** TODO Context name (currently folder) */
	private String name;

	/** Version of the saved data format */
	private String version;

	/** Descriptor of the context root */
	private FolderInfo rootFolder;

	/** Java file for the context root */
	private File rootFolderFile;

	/** List of files in context, sorted by size */
	private List<FileInfo> allFiles;

	/** Changes exist; context should be saved */
	private boolean dirty;

	private Context() {
		this.id = nextid++;

		this.version = null;
		this.dirty = false;

		this.rootFolderFile = null;
		this.rootFolder = null;

		this.allFiles = null; // new ArrayList<FileInfo>();
	}

	/** Construct a context from filesystem or saved data */
	public Context(String rootFolderPath, String name) {
		this();

		System.out.println("Creating folder for " + rootFolderPath);
		this.rootFolderFile = new File(rootFolderPath);
		System.out.println("Folder file " + this.rootFolderFile.getAbsolutePath());
		this.name = name;
		this.dirty = false;
	}

	/** Create a dummy clone context */
	public Context(Context other) {
		this();

		this.name = other.name;
		this.version = other.version;
		this.dirty = false;
		// this.detailLevel = other.detailLevel;
		this.rootFolderFile = other.rootFolderFile;
		this.rootFolder = new FolderInfo(this, this.rootFolderFile);
	}

	public int compareTo(Context other) {
		return this.id - other.id;
	}

	/** Close a context and remove its files from the Database */
	public void close() {
		for (FileInfo f : this.allFiles.toArray(new FileInfo[0])) {
			f.dispose();
		}

		this.allFiles = null;
		this.rootFolder = null;
	}

	/** Get the number of files in the context */
	public int getFileCount() {
		if (this.allFiles == null) {
			this.allFiles = new ArrayList<FileInfo>();
			return -1;
		}

		return this.allFiles.size();
	}

	public long getTotalSize() {
		long size = 0;

		for (FileInfo f : this.allFiles) {
			size += f.filesize;
		}

		return size;
	}

	public long getWaste() {
		long waste = 0;

		for (List<FileInfo> grp : Database.instance().getGroups(this)) {
			waste += getWaste(grp);
		}

		return waste;
	}

	/** Locate the root folder for this context */
	public FolderInfo getRoot() {
		if (this.rootFolder == null) {
			this.rootFolder = new FolderInfo(this, this.rootFolderFile);
			String rootpath = this.rootFolderFile.getAbsolutePath();
			String rootfolder = this.rootFolder.toString();
			String rootfoldername = this.rootFolder.getFullName();
			System.out.println("Created root folder for '" + rootpath + "'" //
					+ ": " + rootfolder + " " + rootfoldername);
		}

		return this.rootFolder;
	}

	/** Delete information about a file from this context */
	public void removeFile(FileInfo file) {
		FolderInfo folder = file.getFolder();

		folder.removeFile(file);
		this.allFiles.remove(file);

		Database.instance().removeFile(file);

		setDirty();
	}

	/** Locate a file in this context (finfo may be an alias) */
	public FileInfo findFile(FileInfo finfo) {
		FolderInfo folder = findFolder(finfo.getFolder().getRelativeJavaFile());
		if (folder == null) {
			return null;
		}

		return folder.findFile(finfo.getName());
	}

	/** Locate a folder in this context */
	public FolderInfo findFolder(File folderFile) {
		return findFolder(folderFile, false);
	}

	/** Locate or create a folder in this context */
	public FolderInfo findOrCreateFolder(File folderFile) {
		return findFolder(folderFile, true);
	}

	/** Locate a folder in this context, optionally creating it if not present */
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

		FolderInfo folder = parentFolder.getSubfolder(folderFile.getName());

		if (create && (folder == null)) {
			folder = new FolderInfo(parentFolder, folderFile);
			parentFolder.addFolder(folder);
		}

		return folder;
	}

	/** Build the context from the filesystem */
	public int ingest() {
		if (this.allFiles == null) { // this.detailLevel.isLessThan(DetailLevel.Size)) {
			this.allFiles = new ArrayList<>();

			return FileUtil.ingestContext(this, DetailLevel.Size);
		}

		return getRoot().getTreeFileCount();
	}

	public void addFile(FolderInfo folder, FileInfo file) {
		folder.addFile(file);
		FileUtil.addFile(this.allFiles, file);
	}

	/** Output description of duplicate chains for this context */
	private void dumpDuplicateChains(int level, String title) {
		// countDuplicates();

		Trace.traceln(level, title);

		Trace.traceln(level, " Unique: " + getUniqueFileCount()); // this.localUniqueCount);

		Trace.trace(level, " Dups: " + getDupFileCount()); // this.dupcount);
		Database db = Database.instance();
		List<List<FileInfo>> groups = db.getGroups(this);
		Trace.trace(level, String.format(" in %d groups", groups.size()));

		List<FileInfo> longestGroup = null;
		List<FileInfo> largestGroup = null;

		for (List<FileInfo> group : groups) {
			if (group.size() > longestGroup.size()) {
				longestGroup = group;
			}

			if (getWaste(group) > getWaste(largestGroup)) {
				largestGroup = group;
			}
		}

		Trace.trace(level, ", Longest: files=" + longestGroup.size());
		Trace.trace(level, " waste= " + Utility.formatSize(getWaste(largestGroup)));

		Trace.traceln(level);

		long totalWaste = getWaste();

		float wasteRatio = ((float) totalWaste) / getTotalSize();
		Trace.trace(level, " Waste " + Utility.formatSize(totalWaste) //
				+ "(" + Utility.formatPercent(wasteRatio) + "%)");

		Trace.trace(level, ", Largest: files=" + largestGroup.size());
		Trace.trace(level, " waste=: " + Utility.formatSize(getWaste(largestGroup)));

		Trace.traceln(level);
	}

	private long getWaste(List<FileInfo> group) {
		return (group.size() - 1) * group.get(0).filesize;
	}

	public String getVersion() {
		return this.version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public List<FileInfo> getAllFiles() {
		return Collections.unmodifiableList(this.allFiles);
	}

	public int getUniqueFileCount() {
		return this.rootFolder.getTreeUniqueCount();
		// return this.localUniqueCount;
	}

	public int getDupFileCount() {
		return this.rootFolder.getTreeDupCount();
	}

	public DetailLevel getDetailLevel() {
		DetailLevel detailLevel = DetailLevel.MAX;

		for (FileInfo f : this.allFiles) {
			if (f.getDetailLevel().intval < detailLevel.intval) {
				detailLevel = f.getDetailLevel();
			}
		}

		return detailLevel;
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

	@Override
	public String toString() {
		return String.format("Context: %s rootname=%s rootfile=%s", //
				getName(), getRoot().getFullName(), getRootFile().getAbsolutePath());
	}
}
