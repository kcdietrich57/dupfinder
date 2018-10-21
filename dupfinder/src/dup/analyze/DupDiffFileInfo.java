package dup.analyze;

import java.io.File;

import dup.model.FileInfo;

public class DupDiffFileInfo {
	public String filename;
	// public long size;
	public long timestamp;

	public DupDiffFileInfo(String filename, long timestamp) {
		this.filename = filename;
		// this.size = ;
		this.timestamp = timestamp;
	}

	public DupDiffFileInfo(File file) {
		this.filename = file.getAbsolutePath();
		// this.size = file.length();
		this.timestamp = file.lastModified();
	}

	public boolean matches(FileInfo file) {
		File jfile = file.getJavaFile();

		if (!this.filename.equals(jfile.getAbsolutePath())) {
			return false;
		}

		return this.timestamp == jfile.lastModified();
	}

	public int hashCode() {
		return this.filename.hashCode();
	}

	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		return (o instanceof DupDiffFileInfo) ? this.filename.equals(((DupDiffFileInfo) o).filename)
				: super.equals(o);
	}
}