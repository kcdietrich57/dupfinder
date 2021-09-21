package dup.analyze;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dup.model.Database;
import dup.model.FileInfo;
import dup.model.FolderInfo;
import dup.util.FileUtil;
import dup.util.Trace;

/** Information about file duplicates shared between duplicate files */
public class DuplicateInfo {
	private FileInfo file;

	private Set<FileInfo> verifiedDuplicateFiles = null;
	private Set<FileInfo> verifiedDifferentFiles = null;

	private Set<FileInfo> contextDuplicates = null;
	private Collection<FileInfo> globalDuplicates = null;

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

	public boolean hasContextDuplicates() {
		return (this.contextDuplicates != null) && !this.contextDuplicates.isEmpty();
	}

	public boolean hasGlobalDuplicates() {
		return this.globalDuplicates != null;
	}

	public boolean hasDuplicatesInFolder() {
		if (this.contextDuplicates == null) {
			return false;
		}

		FolderInfo folder = this.file.getFolder();

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

	public boolean equals(Object o) {
		if (!(o instanceof DuplicateInfo)) {
			return false;
		}

		return this.file.checksums.equals(((DuplicateInfo) o).file.checksums);
	}

	public Collection<FileInfo> getGlobalDuplicates() {
		return (this.globalDuplicates != null) ? this.globalDuplicates : FileInfo.NoFiles;
	}

	public void setGlobalDuplicates(List<FileInfo> dups) {
		if (dups != null) {
			this.globalDuplicates = dups;
			this.file.getFolder().globalDupCount += dups.size();
		}
	}

	public Collection<FileInfo> getContextDuplicates() {
		return (this.contextDuplicates != null) ? this.contextDuplicates : FileInfo.NoFiles;
	}

	/**
	 * Build duplicate chain for two files (same size)
	 * 
	 * TODO based on current information.
	 * 
	 * @param other The other file
	 * @return True if the files are duplicates
	 */
	public boolean addFileToDuplicateChain(DuplicateInfo other) {
		assert this.file.getSize() == other.file.getSize();

		if (!this.file.isDuplicateOf(other.file, false)) {
			return false;
		}

		Set<FileInfo> dups = null;

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

	public boolean isVerifiedEqual(DuplicateInfo other) {
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
			RegisteredDupDiffInfo.registerDuplicateFile(this.file, other.file);
		} else {
			addToVerifiedDifferent(other);
			RegisteredDupDiffInfo.registerDifferentFile(this.file, other.file);
		}

		return same;
	}

	private boolean compareContents(DuplicateInfo other) {
		return FileUtil.compareContents(this.file, other.file);
	}

	public void addToVerifiedDuplicate(DuplicateInfo other) {
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

	public void addToVerifiedDifferent(DuplicateInfo other) {
		this.verifiedDifferentFiles.add(other.file);
		other.verifiedDifferentFiles.add(this.file);
	}

	public Collection<FileInfo> getVerifiedDuplicates() {
		// TODO construct from Database.verifiedIdentical?
		return (this.verifiedDuplicateFiles != null) //
				? this.verifiedDuplicateFiles //
				: FileInfo.NoFiles;
	}

	public Collection<FileInfo> getVerifiedDifferentFiles() {
		// TODO construct from Database.verifiedDifferent?
		return this.verifiedDifferentFiles;
	}
}