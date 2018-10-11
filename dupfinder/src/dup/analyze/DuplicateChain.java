package dup.analyze;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dup.model.FileInfo;
import dup.model.FolderInfo;

public class DuplicateChain {
	private List<FileInfo> files;

	public static final Comparator<DuplicateChain> compareSize;

	static {
		compareSize = new Comparator<DuplicateChain>() {
			public int compare(DuplicateChain c1, DuplicateChain c2) {
				long diff = c1.getFileSize() - c2.getFileSize();

				return (diff == 0) ? 0 : (diff < 0) ? -1 : 1;
			}
		};
	}

	public DuplicateChain() {
		this.files = new ArrayList<FileInfo>();
	}

	public DuplicateChain(FileInfo file) {
		this();

		this.files.add(file);
	}

	public void addFile(FileInfo file) {
		this.files.add(file);
	}

	public void removeFile(int idx) {
		this.files.remove(idx);
	}

	public int getNumFiles() {
		return this.files.size();
	}

	public long getWaste() {
		return getFileSize() * (getNumFiles() - 1);
	}

	public long getTotalSize() {
		return getNumFiles() * getFileSize();
	}

	public FileInfo getFileInfo() {
		return getFileInfo(0);
	}

	public FileInfo getFileInfo(int index) {
		return this.files.get(index);
	}

	public long getFileSize() {
		return this.files.get(0).getSize();
	}

	private List<FolderInfo> getFoldersWithMultipleDuplicates() {
		Set<FolderInfo> folders = new HashSet<FolderInfo>();

		for (int ii = 0; ii < this.files.size(); ++ii) {
			FileInfo f1 = this.files.get(ii);

			for (int jj = ii + 1; jj < this.files.size(); ++jj) {
				FileInfo f2 = this.files.get(jj);

				if (f1.getFolder() == f2.getFolder()) {
					folders.add(f2.getFolder());
					break;
				}
			}
		}

		return new ArrayList<FolderInfo>(folders);
	}
}
