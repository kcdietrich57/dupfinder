package dup.model;

import java.io.File;
import java.util.Collection;
import java.util.List;

import dup.analyze.Checksum;

public class FileInfo extends FileObjectInfo {
	private long filesize;
	private long timestamp;

	private DuplicateInfo dupinfo = new DuplicateInfo(this);

	public FileInfo(FolderInfo folder, File file) {
		super(folder, file.getName());

		this.filesize = file.length();
		this.timestamp = file.lastModified();
	}

	public FileInfo(FolderInfo folder, FileInfo file) {
		super(folder, file.getName());

		this.timestamp = file.timestamp;
		this.filesize = file.filesize;
		// TODO bad alias here, copy instead
		this.dupinfo = file.dupinfo;
	}

	public FileInfo(FolderInfo folder, String name, long size, long modified) {
		super(folder, name);

		this.filesize = size;
		this.timestamp = modified;
	}

	/**
	 * Is this file a duplicate of a file in a possibly different context? This will
	 * optionally compare file contents.
	 * 
	 * @param other        The other file
	 * @param compareFiles Whether to compare contents if necessary
	 * @return True if the files are duplicates by our best information
	 */
	public boolean isDuplicateOf(FileInfo other, boolean compareFiles) {
		return getDupinfo().isDuplicateOf(other, compareFiles);
	}

	public void clearDuplicateInfoForRestart() {
		this.dupinfo.prepareForReanalysis();
	}

	public void dispose() {
		this.dupinfo.dispose();
		this.dupinfo = null;
	}

	/**
	 * Build duplicate chain for two files (same size) TODO based on current
	 * information.
	 * 
	 * @param other The other file
	 * @return True if the files are duplicates
	 */
	public boolean addFileToDuplicateChain(FileInfo other) {
		return getDupinfo().addFileToDuplicateChain(other.getDupinfo());
	}

	public void removeFromDuplicateChains() {
		getDupinfo().removeFromDuplicateChains();
	}

	public long getLastModified() {
		return this.timestamp;
	}

	public long getSize() {
		return this.filesize;
	}

	public DuplicateInfo getDupinfo() {
		return this.dupinfo;
	}

	public Collection<FileInfo> getContextDuplicates() {
		return getDupinfo().getContextDuplicates();
	}

	public void setGlobalDuplicates(List<FileInfo> dups) {
		getDupinfo().setGlobalDuplicates(dups);
	}

	public Collection<FileInfo> getGlobalDuplicates() {
		return getDupinfo().getGlobalDuplicates();
	}

	public boolean isUnique() {
		return !(hasLocalDuplicates() || hasGlobalDuplicates());
	}

	public boolean hasLocalDuplicates() {
		return getDupinfo().hasLocalDuplicates();
	}

	public boolean hasGlobalDuplicates() {
		return getDupinfo().hasGlobalDuplicates();
	}

	public int getNumContextDuplicates() {
		return getDupinfo().getNumContextDuplicates();
	}

	public int getNumGlobalDuplicates() {
		return getDupinfo().getNumGlobalDuplicates();
	}

	public String toString() {
		return "File[" + getName() + "]";
	}

	public DetailLevel getDetailLevel() {
		return getDupinfo().getDetailLevel();
	}

	public void calcChecksums(Context context, DetailLevel detail) {
		setChecksums(Checksum.getChecksums(this, context, detail));
	}

	public int getPrefixChecksum() {
		return getPrefixChecksum(false);
	}

	public int getSampleChecksum() {
		return getSampleChecksum(false);
	}

	public int getPrefixChecksum(boolean calc) {
		return getDupinfo().getPrefixChecksum(calc);
	}

	public int getSampleChecksum(boolean calc) {
		return getDupinfo().getSampleChecksum(calc);
	}

	public void setChecksums(Checksum.Checksums checksums) {
		if (checksums.prefix != Checksum.CKSUM_UNDEFINED) {
			setPrefixChecksum(checksums.prefix);
		}

		if (checksums.sampleBytes != null) {
			setSampleBytes(checksums.sampleBytes);
		}

		if (checksums.sample != Checksum.CKSUM_UNDEFINED) {
			setSampleChecksum(checksums.sample);
		}
	}

	public void setPrefixChecksum(int value) {
		getDupinfo().setPrefixChecksum(value);
	}

	public byte[] getSampleBytes() {
		return getSampleBytes(false);
	}

	public byte[] getSampleBytes(boolean calc) {
		return getDupinfo().getSampleBytes(calc);
	}

	private void setSampleBytes(byte[] bytes) {
		setSampleBytes(bytes, bytes.length);
	}

	public void setSampleBytes(byte[] bytes, int length) {
		getDupinfo().setSampleBytes(bytes, length);
	}

	public void setSampleChecksum(int value) {
		getDupinfo().ck.sample = value;
	}

	public boolean hasDuplicatesInFolder() {
		return getDupinfo().hasDuplicatesInFolder();
	}

	// public boolean matchesFileOnDisk(Context context)
	// {
	// File jfile = getJavaFile(context);
	//
	// return jfile.exists() //
	// && jfile.canRead()
	// && (jfile.length() == this.size)
	// && (jfile.lastModified() == this.timestamp);
	// }

	// public boolean simpleCompareTo(FileInfo other)
	// {
	// return (this.size == other.size) //
	// && (this.timestamp == other.timestamp);
	// }

	// public boolean matches(FileInfo other)
	// {
	// return (getSize() == other.getSize()) //
	// && ((this.dupinfo == null) || this.dupinfo.equals(other.dupinfo));
	// // && compareSums(this.prefixChecksum, other.prefixChecksum)
	// // && compareSums(this.sampleChecksum, other.sampleChecksum)
	// // && compareSums(this.fullChecksum, other.fullChecksum);
	// }
}
