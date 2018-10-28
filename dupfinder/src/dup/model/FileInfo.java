package dup.model;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import dup.analyze.Checksum;
import dup.analyze.ChecksumValues;
import dup.analyze.DetailLevel;
import dup.analyze.DuplicateInfo;
import dup.analyze.DuplicateInfo2;
import dup.analyze.Fingerprint;

/** Class representing a file object */
public class FileInfo extends FileObjectInfo {
	public static final Collection<FileInfo> NoFiles = Collections.unmodifiableCollection(new ArrayList<FileInfo>());

	private long filesize;
	private long timestamp;
	public final ChecksumValues checksums;
	/**
	 * True if the uniqueness of this file has been confirmed. If the file belongs
	 * to a list of duplicates, it has been compared fully to other confirmed files
	 * in the list; if the file is not in such a list, it has been eliminated from
	 * all lists of same-size files via comparison.<br>
	 * TODO perhaps this is not any better/different than detailLevel == MAX.
	 */
	public boolean confirmed;

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
		this.confirmed = false;
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

		// TODO uncertain whether a cloned fileinfo should be confirmed or not.
		this.confirmed = file.confirmed;
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
		return isDuplicateOf(other, DetailLevel.Sample);
	}

	/**
	 * Is this file a duplicate of a file in a possibly different context? This will
	 * optionally compare file contents.
	 * 
	 * @param other The other file
	 * @param level What level of comparison to perform
	 * @return True if the files are duplicates by our best information
	 */
	public boolean isDuplicateOf(FileInfo other, DetailLevel level) {
//		if (this.dupinfo.getVerifiedDifferentFiles().contains(other)) {
//			return false;
//		}
//
//		if ((this.dupinfo.getVerifiedDuplicates() != null) //
//				&& this.dupinfo.getVerifiedDuplicates().contains(other)) {
//			return true;
//		}
//
//		if (RegisteredDupDiffInfo.isRegisteredDifferentFile(this, other)) {
//			this.dupinfo.addToVerifiedDifferent(other.getDupinfo());
//			return false;
//		}
//
//		if (RegisteredDupDiffInfo.isRegisteredDuplicateFile(this, other)) {
//			this.dupinfo.addToVerifiedDuplicate(other.getDupinfo());
//			return true;
//		}

		if (!checksumsMatch(other, level) //
		// TODO I believe we have already checked this above
//				|| !this.dupinfo.isVerifiedEqual(other.getDupinfo())
		) {
			return false;
		}

		boolean compareFiles = false;
		return !compareFiles //
				|| Fingerprint.filesAreIdentical(this, other);
	}

	public boolean checksumsMatch(FileInfo other) {
		return checksumsMatch(other, DetailLevel.Sample);
	}

	public boolean checksumsMatch(FileInfo other, DetailLevel level) {
		if (isIgnoredFile() || other.isIgnoredFile()) {
			return false;
		}

		assert (getSize() == other.getSize());
		if (!level.isGreaterThan(DetailLevel.Size)) {
			return true;
		}

		if (!level.isLessThan(DetailLevel.Prefix)) {
			if (!ChecksumValues.isIdentical(this.getPrefixChecksum(true), other.getPrefixChecksum(true))) {
				return false;
			}
		}

		if (!level.isLessThan(DetailLevel.Sample)) {
			if (!ChecksumValues.isIdentical(this.getSampleBytes(true), other.getSampleBytes(true)) //
					|| !ChecksumValues.isIdentical(this.getSampleChecksum(true), other.getSampleChecksum(true))) {
				return false;
			}
		}

		// TODO if (ChecksumValues.isIdentical(this.getFullChecksum(context),
		// other.getFullChecksum(otherContext))) {
		// return true;
		// }

		return true; // isVerifiedEqual(context, otherContext,
						// other.getDupinfo());
	}

	private boolean isIgnoredFile() {
		if (getSize() == 0) {
			return true;
		}
		if (getName().equals(".DS_Store")) {
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
		DuplicateInfo2 dupinfo = Database.instance().getDuplicateInfo(this);
		if (dupinfo != null) {
			dupinfo.forgetFile(this);
		}
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

	/**
	 * Return context duplicates of a file (including the file itself) <br>
	 * If there are no duplicates, return empty collection
	 */
	public Collection<FileInfo> getContextDuplicates() {
		List<FileInfo> dups = getAllDuplicates();
		if (dups == null) {
			return FileInfo.NoFiles;
		}

		List<FileInfo> cdups = new ArrayList<FileInfo>();

		for (FileInfo file : dups) {
			if ((this != file) && (this.contextid == file.contextid)) {
				cdups.add(file);
			}
		}

		if (!cdups.isEmpty()) {
			cdups.add(this);
		}

		Collection<FileInfo> cdups2 = getDupinfo().getContextDuplicates();
		return cdups2;
	}

	public void setGlobalDuplicates(List<FileInfo> dups) {
		getDupinfo().setGlobalDuplicates(dups);
	}

	/**
	 * Return global duplicates of a file (including the file itself) <br>
	 * If there are no duplicates, return empty collection
	 */
	public Collection<FileInfo> getGlobalDuplicates() {
		List<FileInfo> dups = getAllDuplicates();
		if (dups == null) {
			return FileInfo.NoFiles;
		}

		List<FileInfo> cdups = new ArrayList<FileInfo>();

		for (FileInfo file : dups) {
			if (this.contextid != file.contextid) {
				cdups.add(file);
			}
		}

		if (!cdups.isEmpty()) {
			cdups.add(this);
		}

		// TODO figure out exactly what this is - return cdups;
		Collection<FileInfo> cdups2 = getDupinfo().getGlobalDuplicates();
		return cdups2;
	}

	private List<FileInfo> getAllDuplicates() {
		DuplicateInfo2 dupinfo = Database.instance().getDuplicateInfo(this);

		return (dupinfo != null) ? dupinfo.getDuplicates(this) : null;
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
		return String.format("File[%s] cx='%s' sz=%d det=%s %s", //
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
