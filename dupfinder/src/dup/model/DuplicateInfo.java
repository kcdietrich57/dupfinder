package dup.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dup.analyze.Analyzer;
import dup.analyze.Checksum;
import dup.analyze.Checksum.Checksums;
import dup.analyze.Fingerprint;
import dup.util.FileUtil;
import dup.util.Trace;
import dup.util.Utility;

public class DuplicateInfo {
	FileInfo file;

	Checksums ck = new Checksums();

	Set<FileInfo> verifiedDuplicateFiles = null;
	Set<FileInfo> verifiedDifferentFiles = null;

	FileInfo nextContextDuplicate = null;
	Collection<FileInfo> contextDuplicates = null;
	Collection<FileInfo> globalDuplicates = null;

	public DuplicateInfo(FileInfo file) {
		this.file = file;

		this.verifiedDuplicateFiles = null;
		this.verifiedDifferentFiles = new HashSet<FileInfo>();
	}

	public void dispose() {
		// NOTE Assuming that we don't need/want to clean up Database.verified
		// collections here - either it doesn't matter, or it is done elsewhere

		if (this.verifiedDuplicateFiles != null) {
			for (FileInfo f : this.verifiedDuplicateFiles.toArray(new FileInfo[0])) {
				f.getDupinfo().forgetFile(this.file);
			}

			this.verifiedDuplicateFiles = null;
		}

		for (FileInfo f : this.verifiedDifferentFiles) {
			f.getDupinfo().forgetFile(this.file);
		}

		this.verifiedDifferentFiles.clear();

		if (this.contextDuplicates != null) {
			this.contextDuplicates.remove(this.file);
			this.contextDuplicates = null;
		}

		if (this.globalDuplicates != null) {
			this.globalDuplicates.remove(this.file);
			this.globalDuplicates = null;
		}

		this.file = null;
	}

	public void prepareForReanalysis() {
		// TODO We could forget verified info and rebuild from DB if we want to.
		// Remember file and verified identical/duplicate files
		// Reanalysis will rebuild duplicate chains, however.
		this.contextDuplicates = null;
		this.globalDuplicates = null;
	}

	private void forgetFile(FileInfo file) {
		// NOTE Assuming that we don't need to clean up Database.verified info
		// here - it doesn't matter or is done elsewhere.
		if ((this.verifiedDuplicateFiles != null) //
				&& (file != this.file) && this.verifiedDuplicateFiles.remove(file)) {
			if (this.verifiedDuplicateFiles.size() == 1) {
				this.verifiedDuplicateFiles = null;
			}
		}

		this.verifiedDifferentFiles.remove(file);
	}

	public DetailLevel getDetailLevel() {
		if (this.ck != null) {
			if (this.ck.sample != Checksum.CKSUM_UNDEFINED) {
				return DetailLevel.Sample;
			}

			if (this.ck.prefix != Checksum.CKSUM_UNDEFINED) {
				return DetailLevel.Prefix;
			}
		}

		if (this.file.getSize() >= 0) {
			return DetailLevel.Size;
		}

		return DetailLevel.None;
	}

	public boolean hasLocalDuplicates() {
		return (this.contextDuplicates != null) && !this.contextDuplicates.isEmpty();
	}

	public boolean hasGlobalDuplicates() {
		return this.globalDuplicates != null;
	}

	public boolean hasDuplicatesInFolder() {
		FolderInfo folder = this.file.getFolder();

		// for (FileInfo file = this.nextContextDuplicate; //
		// (file != null) && (file != this.file); //
		// file = file.getDupinfo().nextContextDuplicate) {
		// if (file.getFolder() == folder) {
		// return true;
		// }
		// }

		for (FileInfo file : this.contextDuplicates) {
			if ((file != this.file) && (file.getFolder() == folder)) {
				return true;
			}
		}

		return false;
	}

	public int getNumContextDuplicates() {
		return (this.contextDuplicates != null) ? this.contextDuplicates.size() : 0;
	}

	public int getNumGlobalDuplicates() {
		return (this.globalDuplicates != null) ? this.globalDuplicates.size() : 0;
	}

	public int getPrefixChecksum(boolean calc) {
		if (calc && ((this.ck == null) || (this.ck.prefix == 0))) {
			Fingerprint.calculatePrefixChecksum(this.file);
		}

		return this.ck.prefix;
	}

	public void setPrefixChecksum(int value) {
		if (this.ck == null) {
			this.ck = new Checksums();
		}

		this.ck.prefix = value;
	}

	public byte[] getSampleBytes(boolean calc) {
		if (calc && (this.file.getContext() != null) //
				&& ((this.ck == null) || (this.ck.sampleBytes == null))) {
			Fingerprint.loadSampleBytes(this.file);
		}

		return this.ck.sampleBytes;
	}

	public void setSampleBytes(byte[] bytes, int length) {
		this.ck.sampleBytes = Arrays.copyOf(bytes, length);
	}

	public int getSampleChecksum(boolean calc) {
		if (calc && ((this.ck == null) || (this.ck.sample == 0))) {
			Fingerprint.calculateSampleChecksum(this.file);
		}

		return this.ck.sample;
	}

	public boolean equals(Object o) {
		if (!(o instanceof DuplicateInfo)) {
			return false;
		}

		return this.ck.equals(((DuplicateInfo) o).ck);
	}

	/**
	 * Given that this file is the same size as another, Is my file a duplicate?
	 * This will optionally compare file contents.
	 * 
	 * @param other        The other file
	 * @param compareFiles Whether to compare contents if necessary
	 * @return True if the files are duplicates by our best information
	 */
	public boolean isDuplicateOf(FileInfo other, boolean compareFiles) {
		if (this.verifiedDifferentFiles.contains(other)) {
			return false;
		}

		if ((this.verifiedDuplicateFiles != null) //
				&& this.verifiedDuplicateFiles.contains(other)) {
			return true;
		}

		if (Database.instance().isRegisteredDifferentFile(this.file, other)) {
			addToVerifiedDifferent(other.getDupinfo());
			return false;
		}

		if (Database.instance().isRegisteredDuplicateFile(this.file, other)) {
			addToVerifiedDuplicate(other.getDupinfo());
			return true;
		}

		if (!checksumsMatch(other) //
				// TODO I believe we have already checked this above
				|| !isVerifiedEqual(other.getDupinfo())) {
			return false;
		}

		compareFiles = false;
		return !compareFiles //
				|| Fingerprint.filesAreIdentical(this.file, other);
	}

	public Collection<FileInfo> getGlobalDuplicates() {
		return (this.globalDuplicates != null) ? this.globalDuplicates : NoFiles;
	}

	public void setGlobalDuplicates(List<FileInfo> dups) {
		assert (dups != null);

		this.globalDuplicates = dups;
		++this.file.getFolder().globalDupCount;
	}

	private static final Collection<FileInfo> NoFiles = new ArrayList<FileInfo>();

	public Collection<FileInfo> getContextDuplicates() {
		return (this.contextDuplicates != null) ? this.contextDuplicates : NoFiles;
	}

	/**
	 * Build duplicate chain for two files (same size) TODO based on current
	 * information.
	 * 
	 * @param other The other file
	 * @return True if the files are duplicates
	 */
	public boolean addFileToDuplicateChain(DuplicateInfo other) {
		assert this.file.getSize() == other.file.getSize();
		// TODO this doesn't need to go through fileinfo
		if (!this.file.isDuplicateOf(other.file, false)) {
			return false;
		}

		Collection<FileInfo> dups = null;

		if (this.contextDuplicates != null) {
			dups = this.contextDuplicates;
			dups.add(other.file);

			if (other.contextDuplicates != null) {
				dups.addAll(other.contextDuplicates);

				for (FileInfo f : other.contextDuplicates) {
					f.getDupinfo().contextDuplicates = dups;
				}
			}

			other.contextDuplicates = dups;
		} else if (other.contextDuplicates != null) {
			dups = other.contextDuplicates;
			dups.add(this.file);

			this.contextDuplicates = dups;
		} else {
			dups = new HashSet<FileInfo>();
			dups.add(this.file);
			dups.add(other.file);

			this.contextDuplicates = dups;
			other.contextDuplicates = dups;
		}

		return true;
	}

	public void removeFromDuplicateChains() {
		if (this.globalDuplicates != null) {
			this.globalDuplicates.remove(this.file);

			if (!Analyzer.hasGlobalDuplicates(this.globalDuplicates)) {
				for (FileInfo f : this.globalDuplicates) {
					f.getDupinfo().globalDuplicates = null;
				}

				this.globalDuplicates.clear();
			}

			this.globalDuplicates = null;
		}

		if (this.contextDuplicates != null) {
			this.contextDuplicates.remove(this.file);

			if (this.contextDuplicates.size() == 1) {
				FileInfo f = this.contextDuplicates.iterator().next();

				f.getDupinfo().contextDuplicates = null;

				this.contextDuplicates.clear();
			}

			this.contextDuplicates = null;
		}
	}

	private boolean isVerifiedEqual(DuplicateInfo other) {
		if (this.verifiedDifferentFiles.contains(other.file)) {
			return false;
		}

		if ((this.verifiedDuplicateFiles != null) //
				&& this.verifiedDuplicateFiles.contains(this.file)) {
			return true;
		}

		boolean same = false;

		// this==x and x==other implies this==other
		// NB this is impossible. x can't be in two lists simultaneously.
		// int n = this.verifiedEqual.size();
		// this.verifiedEqual.removeAll(other.verifiedEqual);
		// same = this.verifiedEqual.size() < n;

		if (!same) {
			if (Database.skipFileComparison) {
				Trace.traceln(Trace.VERBOSE, "Skipping file comparison...");
				same = true;
			} else {
				same = compareContents(other);
			}
		}

		if (same) {
			addToVerifiedDuplicate(other);
			Database.instance().registerDuplicateFile(this.file, other.file);
		} else {
			addToVerifiedDifferent(other);
			Database.instance().registerDifferentFile(this.file, other.file);
		}

		return same;
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

	public boolean checksumsMatch(FileInfo other) {
		if (isIgnoredFile(this.file) || isIgnoredFile(other)) {
			return false;
		}

		if ((this.file.getSize() != other.getSize()) //
				|| !Utility.checksumsMatch(getPrefixChecksum(true), other.getPrefixChecksum(true)) //
				|| !Utility.bytesMatch(getSampleBytes(true), other.getSampleBytes(true)) //
				|| !Utility.checksumsMatch(getSampleChecksum(true), other.getSampleChecksum(true)) //
		// TODO || !Utility.checksumsMatch(getFullChecksum(context),
		// other.getFullChecksum(otherContext))
		) {
			return false;
		}

		return true; // isVerifiedEqual(context, otherContext,
						// other.getDupinfo());
	}

	private boolean compareContents(DuplicateInfo other) {
		return FileUtil.compareContents(this.file, other.file);
	}

	private void addToVerifiedDuplicate(DuplicateInfo other) {
		if (this.verifiedDuplicateFiles == null && other.verifiedDuplicateFiles != null) {
			this.verifiedDuplicateFiles = other.verifiedDuplicateFiles;
			this.verifiedDuplicateFiles.add(this.file);
		} else if (this.verifiedDuplicateFiles != null && other.verifiedDuplicateFiles == null) {
			other.verifiedDuplicateFiles = this.verifiedDuplicateFiles;
			this.verifiedDuplicateFiles.add(other.file);
		} else if (this.verifiedDuplicateFiles == null) {
			this.verifiedDuplicateFiles = other.verifiedDuplicateFiles = new HashSet<FileInfo>();
			this.verifiedDuplicateFiles.add(this.file);
			this.verifiedDuplicateFiles.add(other.file);
		} else {
			Set<FileInfo> olist = other.verifiedDuplicateFiles;

			for (FileInfo f : olist) {
				f.getDupinfo().verifiedDuplicateFiles = this.verifiedDuplicateFiles;
			}

			this.verifiedDuplicateFiles.addAll(olist);
		}
	}

	private void addToVerifiedDifferent(DuplicateInfo other) {
		this.verifiedDifferentFiles.add(other.file);
		other.verifiedDifferentFiles.add(this.file);
	}

	// TODO rename
	public Collection<FileInfo> getVerifiedDuplicates() {
		// TODO construct from Database.verifiedIdentical?
		return (this.verifiedDuplicateFiles != null) ? this.verifiedDuplicateFiles : NoFiles;
	}

	public Collection<FileInfo> getVerifiedDifferentFiles() {
		// TODO construct from Database.verifiedDifferent?
		return this.verifiedDifferentFiles;
	}
}