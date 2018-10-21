package dup.model;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import dup.analyze.DetailLevel;

public class FolderInfo extends FileObjectInfo {
	private final List<FolderInfo> folders = new ArrayList<FolderInfo>();
	private final List<FileInfo> files = new ArrayList<FileInfo>();

	/** Count of non-unique files in this folder */
	public int folderDupCount = 0;
	/** Count of non-local, non-unique files in this folder */
	public int globalDupCount = 0;

	public FolderInfo(FolderInfo folder, File file) {
		super(folder, file);
	}

	public FolderInfo(FolderInfo folder, String name) {
		super(folder, name);
	}
	
	public FolderInfo(Context context, File file) {
		super(context.id, file);
	}

	public void addFolder(FolderInfo folder) {
		assert (folder != null) && (folder.getFolder() == this);

		this.folders.add(folder);
	}

	public void addFile(FileInfo file) {
		this.files.add(file);
		// TODO dirty context, analyzeDuplicates, etc
	}

	public void removeFile(FileInfo file) {
		this.files.remove(file);
		// TODO dirty context, analyzeDuplicates, etc
	}

	/** Get child folder with a given name */
	public final FolderInfo getSubfolder(String name) {
		for (FolderInfo folder : this.folders) {
			if (folder.getName().equals(name)) {
				return folder;
			}
		}

		return null;
	}

	/** Count all folders under this folder (including this one) */
	public int getTreeFolderCount() {
		int count = 1;

		for (FolderInfo folder : this.folders) {
			count += folder.getTreeFolderCount();
		}

		return count;
	}

	/** Count files in this folder */
	public int getFileCount() {
		return this.files.size();
	}

	/** Count all files under this folder */
	public int getTreeFileCount() {
		int count = this.files.size();

		for (FolderInfo folder : this.folders) {
			count += folder.getTreeFileCount();
		}

		return count;
	}

	/** Get the total size of this folder's subtree */
	public long getSize() {
		return getTreeSize();
	}

	/** Get the sum of the size of all files under this folder */
	public long getTreeSize() {
		long tot = getFolderSize();

		for (FolderInfo folder : this.folders) {
			long subtot = folder.getTreeSize();
			tot += subtot;
		}

		return tot;
	}

	/** Get the sum of the sizes of all files in this folder */
	public long getFolderSize() {
		long tot = 0L;

		for (FileInfo file : this.files) {
			tot += file.getSize();
		}

		return tot;
	}

	/** Get the sum of the sizes of non-unique files in this folder */
	public long getFolderDupSize() {
		long tot = 0L;

		for (FileInfo file : this.files) {
			if (!file.isUnique()) {
				tot += file.getSize();
			}
		}

		return tot;
	}

	/** Get the sum of the sizes of all non-unique files under this folder */
	public long getTreeDupSize() {
		long tot = getFolderDupSize();

		for (FolderInfo folder : this.folders) {
			long subtot = folder.getTreeDupSize();
			tot += subtot;
		}

		return tot;
	}

	/** Count the number of non-unique files in this folder */
	public int getDupCount() {
		int dupcount = 0;

		for (FileInfo file : this.files) {
			if (!file.isUnique()) {
				++dupcount;
			}
		}

		return dupcount;
	}

	public int getGlobalDupCount() {
		return this.globalDupCount;
	}

	/** Count the number of context duplicates under this folder */
	public int getTreeLocalDupCount() {
		int count = this.folderDupCount;

		for (FolderInfo folder : this.folders) {
			count += folder.getTreeLocalDupCount();
		}

		return count;
	}

	/** Count the total number of non-unique files under this folder */
	public int getTreeDupCount() {
		int dupcount = getDupCount();

		for (FolderInfo folder : this.folders) {
			dupcount += folder.getTreeDupCount();
		}

		return dupcount;
	}

	/** Get the total count of global duplicates under this folder */
	public int getTreeGlobalDupCount() {
		int count = getGlobalDupCount();

		for (FolderInfo folder : this.folders) {
			count += folder.getTreeGlobalDupCount();
		}

		return count;
	}

	/** Get percentage of non-unique files in this folder */
	public int getDupPercent() {
		return (this.files.isEmpty()) ? 0 : (this.folderDupCount * 100) / getFileCount();
	}

	/** Get whether all files in this folder are non-unique */
	public boolean isAllDups() {
		return this.folderDupCount == this.files.size();
	}

	/** Get whether all files in this sub-tree are non-unique */
	public boolean isAllTreeDups() {
		if (!isAllDups()) {
			return false;
		}

		for (FolderInfo folder : this.folders) {
			if (!folder.isAllDups()) {
				// TODO testing
				folder.isAllDups();
				return false;
			}
		}

		return true;
	}

	/** Get all folders in the sub-tree under this folder */
	public List<FolderInfo> getTreeFolders() {
		List<FolderInfo> folders = new ArrayList<FolderInfo>();

		folders.add(this);

		for (FolderInfo folder : this.folders) {
			folders.addAll(folder.getTreeFolders());
		}

		return folders;
	}

	/** Get child folders in this folder */
	public List<FolderInfo> getSubfolders() {
		return this.folders;
	}

	/** Get files in this folder */
	public List<FileInfo> getFiles() {
		return this.files;
	}

	/**
	 * Get iterator for files in this folder. If recurse is true, iterate all files
	 * in the entire sub-tree.
	 */
	public Iterator<FileInfo> iterateFiles(boolean recurse) {
		return new FileInfoIterator(recurse);
	}

	/**
	 * Get non-unique files in this folder. If recurse is true, get all non-unique
	 * files in the entire sub-tree.
	 */
	public List<FileInfo> getDuplicateFiles(boolean recurse) {
		Set<FileInfo> files = new HashSet<FileInfo>();

		for (Iterator<FileInfo> fiter = iterateFiles(recurse); fiter.hasNext();) {
			FileInfo file = fiter.next();

			if (!file.isUnique()) {
				files.add(file);
			}
		}

		return new ArrayList<FileInfo>(files);
	}

	/** Get files that duplicate other files in this folder */
	public List<FileInfo> getDuplicateFilesInFolder(boolean recurse) {
		Set<FileInfo> files = new HashSet<FileInfo>();

		for (Iterator<FileInfo> fiter = iterateFiles(false); fiter.hasNext();) {
			FileInfo file = fiter.next();

			if (!files.contains(file) && file.hasDuplicatesInFolder()) {
				files.add(file);
			}
		}

		return new ArrayList<FileInfo>(files);
	}

	/** Locate a FolderInfo for a path string */
	private FolderInfo findFolderFromPath(String path) {
		StringTokenizer toker = new StringTokenizer(path, "/");

		if (!toker.hasMoreTokens()) {
			return null;
		}

		String token = toker.nextToken();

		if (!getName().equals(token)) {
			return null;
		}

		FolderInfo folder = this;

		while (toker.hasMoreTokens()) {
			token = toker.nextToken();

			folder = folder.findFolder(token);

			if (folder == null) {
				return null;
			}
		}

		return folder;
	}

	/** Locate a sub-folder by name in this folder */
	private FolderInfo findFolder(String name) {
		for (FolderInfo folder : this.folders) {
			if (folder.getName().equals(name)) {
				return folder;
			}
		}

		return null;
	}

	@Override
	public String toString() {
		return "Folder[" + getName() + "]";
	}

	/** Class to iterate files in a folder or tree */
	private class FileInfoIterator implements Iterator<FileInfo> {
		private Iterator<FolderInfo> folderIterator = null;
		private Iterator<FileInfo> fileIterator = null;
		private boolean recurse = false;

		/** Constructor - iterates files in folder or tree if recurse is true */
		public FileInfoIterator(boolean recurse) {
			this.recurse = recurse;
			this.folderIterator = (recurse) ? FolderInfo.this.folders.iterator() : null;
			this.fileIterator = FolderInfo.this.files.iterator();
		}

		public boolean hasNext() {
			while (!this.fileIterator.hasNext()) {
				if ((this.folderIterator == null) || !this.folderIterator.hasNext()) {
					return false;
				}

				FolderInfo folder = this.folderIterator.next();
				this.fileIterator = folder.iterateFiles(this.recurse);
			}

			return true;
		}

		public FileInfo next() {
			return this.fileIterator.next();
		}

		public void remove() {
			// not supported
		}
	}

	/** Get a file in this folder by name */
	public FileInfo findFile(String name) {
		for (FileInfo file : this.files) {
			if (file.getName().equals(name)) {
				return file;
			}
		}

		return null;
	}

	/** Return the lowest detail level of any file in this tree */
	public DetailLevel getMinimumDetailLevel() {
		Iterator<FileInfo> iter = iterateFiles(true);

		DetailLevel detail = DetailLevel.MAX;

		while (iter.hasNext()) {
			FileInfo file = iter.next();

			if (file.getDetailLevel().isLessThan(detail)) {
				detail = file.getDetailLevel();
			}
		}

		return detail;
	}
}
