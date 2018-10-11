package dup.model;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

public class FolderInfo extends FileObjectInfo {
	private final List<FolderInfo> folders = new ArrayList<FolderInfo>();
	private final List<FileInfo> files = new ArrayList<FileInfo>();

	public int dupcount = 0;
	public int globalDupCount = 0;

	public FolderInfo(FolderInfo folder, File file) {
		super(folder, file);
	}

	public FolderInfo(FolderInfo folder, String name) {
		super(folder, name);
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

	public FolderInfo findSubfolder(String name) {
		for (FolderInfo folder : this.folders) {
			if (folder.getName().equals(name)) {
				return folder;
			}
		}

		return null;
	}

	public int getTreeFolderCount() {
		int count = 1;

		for (FolderInfo folder : this.folders) {
			count += folder.getTreeFolderCount();
		}

		return count;
	}

	public int getFileCount() {
		return this.files.size();
	}

	public int getTreeFileCount() {
		int count = this.files.size();

		for (FolderInfo folder : this.folders) {
			count += folder.getTreeFileCount();
		}

		return count;
	}

	public long getSize() {
		return getTreeSize();
	}

	public long getFolderSize() {
		long tot = 0L;

		for (FileInfo file : this.files) {
			tot += file.getSize();
		}

		return tot;
	}

	public long getTreeSize() {
		long tot = getFolderSize();
		for (FolderInfo folder : this.folders) {
			long subtot = folder.getTreeSize();
			tot += subtot;
		}

		return tot;
	}

	public long getFolderDupSize() {
		long tot = 0L;

		for (FileInfo file : this.files) {
			if (!file.isUnique()) {
				tot += file.getSize();
			}
		}

		return tot;
	}

	public long getTreeDupSize() {
		long tot = getFolderDupSize();

		for (FolderInfo folder : this.folders) {
			long subtot = folder.getTreeDupSize();
			tot += subtot;
		}

		return tot;
	}

	public int getDupCount() {
		int dupcount = 0;

		for (FileInfo file : this.files) {
			if (!file.isUnique()) {
				++dupcount;
			}
		}

		return dupcount;
	}

	public int getLocalDupCount() {
		return this.dupcount;
	}

	public int getGlobalDupCount() {
		return this.globalDupCount;
	}

	public int getTreeLocalDupCount() {
		int count = getLocalDupCount();

		for (FolderInfo folder : this.folders) {
			count += folder.getTreeLocalDupCount();
		}

		return count;
	}

	public int getTreeDupCount() {
		int dupcount = getDupCount();

		for (FolderInfo folder : this.folders) {
			dupcount += folder.getTreeDupCount();
		}

		return dupcount;
	}

	public int getTreeGlobalDupCount() {
		int count = getGlobalDupCount();

		for (FolderInfo folder : this.folders) {
			count += folder.getTreeGlobalDupCount();
		}

		return count;
	}

	public int getLocalDupCountPercent() {
		return (this.files.isEmpty()) ? 0 : (getLocalDupCount() * 100) / getFileCount();
	}

	public boolean isAllDups() {
		return getLocalDupCount() == this.files.size();
	}

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

	public List<FolderInfo> getTreeFolders() {
		List<FolderInfo> folders = new ArrayList<FolderInfo>();

		folders.add(this);

		for (FolderInfo folder : this.folders) {
			folders.addAll(folder.getTreeFolders());
		}

		return folders;
	}

	public List<FolderInfo> getSubfolders() {
		return this.folders;
	}

	public List<FileInfo> getFiles() {
		return this.files;
	}

	public Iterator<FileInfo> iterateFiles(boolean recurse) {
		return new FileInfoIterator(recurse);
	}

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

	public FolderInfo findFolderFromPath(String path) {
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

	public FolderInfo findFolder(String name) {
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

	private class FileInfoIterator implements Iterator<FileInfo> {
		private Iterator<FolderInfo> folderIterator = null;
		private Iterator<FileInfo> fileIterator = null;
		private boolean recurse = false;

		public FileInfoIterator(boolean recurse) {
			this.recurse = recurse;
			this.folderIterator = (recurse) ? FolderInfo.this.folders.iterator() : null;
			this.fileIterator = FolderInfo.this.files.iterator();
		}

		@Override
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

		@Override
		public FileInfo next() {
			return this.fileIterator.next();
		}

		@Override
		public void remove() {
			// not supported
		}
	}

	public FileInfo findFile(String name) {
		for (FileInfo file : this.files) {
			if (file.getName().equals(name)) {
				return file;
			}
		}

		return null;
	}

	public DetailLevel getMinimumDetailLevel() {
		Iterator<FileInfo> iter = iterateFiles(true);

		DetailLevel detail = DetailLevel.Sample;

		while (iter.hasNext()) {
			FileInfo file = iter.next();

			if (file.getDetailLevel().isLessThan(detail)) {
				detail = file.getDetailLevel();
			}
		}

		return detail;
	}
}
