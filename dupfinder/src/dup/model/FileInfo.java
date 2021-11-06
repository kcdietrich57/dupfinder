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
import dup.analyze.Fingerprint;

/** Class representing a file object */
public class FileInfo extends FileObjectInfo {
	public static final List<FileInfo> NoFiles = Collections.unmodifiableList(new ArrayList<FileInfo>());

	public final long filesize;
	private long timestamp;
	public final ChecksumValues checksums;

	public byte unique;
	public static final byte UNIQUE = 0;
	public static final byte GDUP = 1;
	public static final byte LDUP = 2;
	public static final byte BDUP = 3;
	private List<FileInfo> globalDuplicates;
	private List<FileInfo> contextDuplicates;

	public FileInfo(FolderInfo folder, String name, long size, long modified) {
		super(folder, name);

		this.filesize = size;
		this.timestamp = modified;
		this.checksums = new ChecksumValues();
		
		clearDuplicateInfo();
	}

	public FileInfo(FolderInfo folder, File file) {
		this(folder, file.getName(), file.length(), file.lastModified());
	}

	public FileInfo(FolderInfo folder, FileInfo file) {
		super(folder, file.getName());

		this.filesize = file.filesize;
		this.timestamp = file.timestamp;
		this.checksums = file.checksums;
		
		clearDuplicateInfo();
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

	public boolean mayBeDuplicateOf(FileInfo other) {
		if (this.isIgnoredFile() || other.isIgnoredFile()) {
			return false;
		}
		
		return this.checksums.mayBeEqual(other.checksums);
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
		if (!checksumsMatch(other, level)) {
			return false;
		}

		boolean compareFiles = shouldCompareFile() && !level.isLessThan(DetailLevel.MAX);
		return !compareFiles //
				|| Fingerprint.filesAreIdentical(this, other);
	}

	private static final long COMPARE_THRESHOLD = 1024 * 1024 * 16;

	private boolean shouldCompareFile() {
		return this.filesize <= COMPARE_THRESHOLD;
	}

	public boolean checksumsMatch(FileInfo other) {
		return checksumsMatch(other, DetailLevel.Sample);
	}

	public boolean checksumsMatch(FileInfo other, DetailLevel level) {
		if (isIgnoredFile() || other.isIgnoredFile()) {
			return false;
		}

		if (getSize() != other.getSize()) {
			return false;
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

		return true;
	}

	public boolean isIgnoredFile() {
		if (getSize() == 0) {
			return true;
		}
		if (getName().equals(".DS_Store")) {
			return true;
		}
		if (getName().startsWith("._")) {
			return true;
		}
		if (getName().charAt(0) == '.' && getName().endsWith(".icloud")) {
			return true;
		}

		return false;
	}

	public void clearDuplicateInfo() {
		this.unique = FileInfo.UNIQUE;
		this.globalDuplicates = null;
		this.contextDuplicates = null;
	}

	public void dispose() {
		Database.instance().removeFile(this);
	}

	public long getLastModified() {
		return this.timestamp;
	}

	public long getSize() {
		return this.filesize;
	}

	private void cacheDuplicates() {
		if (this.unique == FileInfo.UNIQUE) {
			this.contextDuplicates = this.globalDuplicates = NoFiles;
			return;
		}

		List<FileInfo> dups = Database.instance().getAllDuplicates(this);
		assert dups != null;

		for (FileInfo file : dups) {
			if (file == this) {
				continue;
			}

			if (file.contextid == this.contextid) {
				if (this.contextDuplicates == null) {
					this.contextDuplicates = new ArrayList<>();
					this.contextDuplicates.add(this);
				}
				this.contextDuplicates.add(file);
			} else {
				if (this.globalDuplicates == null) {
					this.globalDuplicates = new ArrayList<>();
					this.globalDuplicates.add(this);
				}
				this.globalDuplicates.add(file);
			}
		}
		
		if (this.globalDuplicates == null) {
			this.globalDuplicates = NoFiles;
		}
		
		if (this.contextDuplicates == null) {
			this.contextDuplicates = NoFiles;
		}
	}

	/**
	 * Return context duplicates of a file (including the file itself) <br>
	 * If there are no duplicates, return empty collection
	 */
	public Collection<FileInfo> getContextDuplicates() {
		if (this.contextDuplicates == null) {
			cacheDuplicates();
		}

		return this.contextDuplicates;
	}

	/**
	 * Return global duplicates of a file (including the file itself) <br>
	 * If there are no duplicates, return empty collection
	 */
	public Collection<FileInfo> getGlobalDuplicates() {
		if (this.globalDuplicates == null) {
			cacheDuplicates();
		}

		return this.globalDuplicates;
	}

	public boolean isUnique() {
		return this.unique == FileInfo.UNIQUE;
	}

	public boolean hasContextDuplicates() {
		return (this.unique & FileInfo.LDUP) != 0;
	}

	public boolean hasGlobalDuplicates() {
		return (this.unique & FileInfo.GDUP) != 0;
	}

	private int safeCount(List<FileInfo> files) {
		return (files == null) ? 0 : files.size();
	}

	public int getNumContextDuplicates() {
		return safeCount(Database.instance().getContextDuplicates(this));
	}

	public int getNumGlobalDuplicates() {
		return safeCount(Database.instance().getGlobalDuplicates(this));
	}

	public String toString() {
		String ctxname = (getContext() != null) ? getContext().getName() : "N/A";
		return String.format("File[%s] cx='%s' dup=%d sz=%d det=%s %s", //
				getName(), ctxname, this.unique, getSize(), //
				getDetailLevel().toString(), //
				this.checksums.toString());
	}

	/** Return if our detail level is at a given level or above */
	public boolean isDetailLevel(DetailLevel detail) {
		return this.getDetailLevel().intval >= detail.intval;
	}

	public DetailLevel getDetailLevel() {
		if (this.filesize == 0) {
			return DetailLevel.MAX;
		}

		DetailLevel level = this.checksums.getDetailLevel();

		if (level == DetailLevel.Size) {
			return (getSize() >= 0) ? DetailLevel.Size : DetailLevel.None;
		}

		return level;
	}

	public void calcChecksums(Context context, DetailLevel detail) {
		if (context != null) {
			setChecksums(Checksum.getChecksums(this, context, detail));
		}
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
		if (!hasContextDuplicates()) {
			return false;
		}

		for (FileInfo file : Database.instance().getContextDuplicates(this)) {
			if (this != file && this.getFolder() == file.getFolder()) {
				return true;
			}
		}

		return false;
	}
}
