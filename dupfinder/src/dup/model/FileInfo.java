package dup.model;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import dup.analyze.Checksum;
import dup.analyze.ChecksumValues;
import dup.analyze.DetailLevel;
import dup.analyze.DuplicateInfo;
import dup.analyze.DuplicateInfo2;
import dup.analyze.Fingerprint;
import dup.util.Utility;

/** Class representing a file object */
public class FileInfo extends FileObjectInfo {
	private long filesize;
	private long timestamp;
	public final ChecksumValues checksums;

	public DuplicateInfo2 dupinfo2;
	private DuplicateInfo dupinfo;

	public FileInfo(FolderInfo folder, String name, long size, long modified) {
		super(folder, name);

		this.filesize = size;
		this.timestamp = modified;
		// TODO this should be null if no same-size files exist?
		this.checksums = new ChecksumValues();
		this.dupinfo2 = null;
		this.dupinfo = new DuplicateInfo(this);
	}

	public FileInfo(FolderInfo folder, File file) {
		this(folder, file.getName(), file.length(), file.lastModified());
	}

	public FileInfo(FolderInfo folder, FileInfo file) {
		super(folder, file.getName());

		this.timestamp = file.timestamp;
		this.filesize = file.filesize;
		this.checksums = file.checksums;

		// TODO bad alias here, copy instead
		this.dupinfo2 = file.dupinfo2;
		this.dupinfo = file.dupinfo;
	}

	public ChecksumValues getChecksums() {
		return this.checksums;
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
		if (this.dupinfo.getVerifiedDifferentFiles().contains(other)) {
			return false;
		}

		if ((this.dupinfo.getVerifiedDuplicates() != null) //
				&& this.dupinfo.getVerifiedDuplicates().contains(other)) {
			return true;
		}

		if (Database.instance().isRegisteredDifferentFile(this, other)) {
			this.dupinfo.addToVerifiedDifferent(other.getDupinfo());
			return false;
		}

		if (Database.instance().isRegisteredDuplicateFile(this, other)) {
			this.dupinfo.addToVerifiedDuplicate(other.getDupinfo());
			return true;
		}

		if (!checksumsMatch(other) //
				// TODO I believe we have already checked this above
				|| !this.dupinfo.isVerifiedEqual(other.getDupinfo())) {
			return false;
		}

		compareFiles = false;
		return !compareFiles //
				|| Fingerprint.filesAreIdentical(this, other);
	}

	public boolean checksumsMatch(FileInfo other) {
		if (isIgnoredFile(this) || isIgnoredFile(other)) {
			return false;
		}

		if ((this.getSize() != other.getSize()) //
				|| !Utility.checksumsAreCompatible(this.getPrefixChecksum(true), other.getPrefixChecksum(true)) //
				|| !Utility.bytesAreCompatible(this.getSampleBytes(true), other.getSampleBytes(true)) //
				|| !Utility.checksumsAreCompatible(this.getSampleChecksum(true), other.getSampleChecksum(true)) //
		// TODO || !Utility.checksumsMatch(this.getFullChecksum(context),
		// other.getFullChecksum(otherContext))
		) {
			return false;
		}

		return true; // isVerifiedEqual(context, otherContext,
						// other.getDupinfo());
	}

	private boolean isIgnoredFile(FileInfo finfo) {
		if (finfo.getSize() == 0) {
			return true;
		}
		if (finfo.getName().equals(".DS_Store")) {
			return true;
		}

		return false;
	}

	public void clearDuplicateInfoForRestart() {
		this.dupinfo.prepareForReanalysis();
	}

	public void dispose() {
		this.dupinfo.dispose();
		this.dupinfo = null;
		this.dupinfo2 = null;
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
		return !(hasContextDuplicates() || hasGlobalDuplicates());
	}

	public boolean hasContextDuplicates() {
		return getDupinfo().hasContextDuplicates();
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
		String ctxname = (getContext() != null) ? getContext().getName() : "N/A";
		return String.format("File[%s] cx=%s sz=%d det=%s %s", //
				getName(), ctxname, getSize(), //
				getDetailLevel().toString(), //
				this.checksums.toString());
	}

	public DetailLevel getDetailLevel() {
		DetailLevel level = this.checksums.getDetailLevel();

		if (level == DetailLevel.Size) {
			return (getSize() >= 0) ? DetailLevel.Size : DetailLevel.None;
		}

		return level;
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
		if (calc && ((this.checksums == null) || (this.checksums.prefix == 0))) {
			Fingerprint.calculatePrefixChecksum(this);
		}

		return this.checksums.prefix;
	}

	public void setSampleChecksum(int value) {
		this.checksums.sample = value;
	}

	public int getSampleChecksum(boolean calc) {
		if (calc && (this.checksums.sample == 0)) {
			Fingerprint.calculateSampleChecksum(this);
		}

		return this.checksums.sample;
	}

	public void setChecksums(ChecksumValues checksums) {
		if (!this.checksums.equals(checksums)) {
			this.checksums.setValues(checksums);
		}
	}

	public void setPrefixChecksum(int value) {
		this.checksums.prefix = value;
	}

	public byte[] getSampleBytes() {
		return getSampleBytes(false);
	}

	public byte[] getSampleBytes(boolean calc) {
		if (calc && (getContext() != null) && (this.checksums.sampleBytes == null)) {
			Fingerprint.loadSampleBytes(this);
		}

		return this.checksums.sampleBytes;
	}

	public void setSampleBytes(byte[] bytes, int length) {
		// getDupinfo().setSampleBytes(bytes, length);
		this.checksums.sampleBytes = Arrays.copyOf(bytes, length);
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
