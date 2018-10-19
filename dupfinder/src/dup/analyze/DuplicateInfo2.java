package dup.analyze;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import dup.model.Context;
import dup.model.FileInfo;

public class DuplicateInfo2 {
	/** All files in this group (same size), sorted by checksum info */
	private List<FileInfo> sameSizeFiles;

	/** Sub-groups of files with identical checksum info, sorted by context */
	private Map<ChecksumValues, List<FileInfo>> duplicateFiles;

	public DuplicateInfo2() {
		this.sameSizeFiles = new ArrayList<FileInfo>();
	}

	/**
	 * Introduce a new file to the group.
	 * 
	 * If quick is true, we add the file quickly without any additional analysis
	 * 
	 * Otherwise, we calculate checksums on the new file to bring it up to the level
	 * of the group if necessary and update the duplicate lists.
	 */
	public void addFile(FileInfo file) {
		this.sameSizeFiles.add(file);

//		Collections.sort(this.sameSizeFiles, new Comparator<FileInfo>() {
//			public int compare(FileInfo f1, FileInfo f2) {
//				return f1.getChecksums().compareTo(f2.getChecksums());
//			}
//		});
	}

	/** Raise detail level of files to be consistent with each other */
	public void normalize() {
		Collections.sort(this.sameSizeFiles, new Comparator<FileInfo>() {
			public int compare(FileInfo f1, FileInfo f2) {
				int diff = f1.getDetailLevel().compareTo(f2.getDetailLevel());
				if (diff != 0) {
					return diff;
				}

				return f1.getChecksums().compareTo(f2.getChecksums());
			}
		});

		// Identify ranges of files with compatible checksums
		int start = -1;
		int end = 0;

		// Remember if we changed any checksums
		boolean fixed = false;

		while (end < this.sameSizeFiles.size()) {
			start = end;
			++end;

			boolean fix = false;
			DetailLevel maxdetail = this.sameSizeFiles.get(start).getDetailLevel();

			while ((end < this.sameSizeFiles.size()) //
					&& this.sameSizeFiles.get(start).checksumsMatch(this.sameSizeFiles.get(end))) {
				DetailLevel detail = this.sameSizeFiles.get(end).getDetailLevel();

				if (detail.compareTo(maxdetail) > 0) {
					fix = true;
					maxdetail = detail;
				}

				++end;
			}

			if (fix) {
				normalizeFiles(start, end, maxdetail);
				fixed = true;
			}
		}

		if (fixed) {
			// Rebuild duplicate chains using new information

			this.duplicateFiles.clear();

			FileInfo lastfile = null;

			for (FileInfo file : this.sameSizeFiles) {
				if (file.getDetailLevel().compareTo(DetailLevel.Size) <= 0) {
					// Skip it
				} else if ((lastfile != null) && file.checksumsMatch(lastfile)) {
					List<FileInfo> dups = this.duplicateFiles.get(file.checksums);

					if (dups == null) {
						dups = new ArrayList<FileInfo>();
						this.duplicateFiles.put(file.checksums, dups);

						dups.add(lastfile);
					}

					dups.add(file);
				}

				lastfile = file;
			}
		}
	}

	/** Increase detail level of a group of files to a desired level */
	private void normalizeFiles(int start, int end, DetailLevel detail) {
		for (int idx = start; idx < end; ++idx) {
			FileInfo file = this.sameSizeFiles.get(idx);

			file.calcChecksums(file.getContext(), detail);
		}
	}

	public boolean hasDuplicates() {
		// TODO I don't know if I will have lists with just one file yet
		for (List<FileInfo> dups : this.duplicateFiles.values()) {
			if (dups.size() > 1) {
				return true;
			}
		}

		return false;
	}

	public boolean hasGlobalDuplicates() {
		for (List<FileInfo> dups : this.duplicateFiles.values()) {
			Context lastcontext = null;

			for (FileInfo file : dups) {
				if (lastcontext != file.getContext()) {
					return true;
				}
			}
		}

		return false;
	}

	public boolean hasLocalDuplicates() {
		for (List<FileInfo> dups : this.duplicateFiles.values()) {
			Context lastcontext = null;

			for (FileInfo file : dups) {
				if (lastcontext == file.getContext()) {
					return true;
				}
			}
		}

		return false;
	}

	public void processFiles() {

	}
}
