package dup.analyze;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dup.model.Context;
import dup.model.FileInfo;

public class DuplicateInfo2 {
	private long filesize = -1;

	/** All files in this group (same size), sorted by checksum info */
	private List<FileInfo> sameSizeFiles;

	/** Sub-groups of files with identical checksum info, sorted by context */
	private Map<ChecksumValues, List<FileInfo>> duplicateFiles;

	public DuplicateInfo2() {
		this.sameSizeFiles = new ArrayList<FileInfo>();
		this.duplicateFiles = new HashMap<ChecksumValues, List<FileInfo>>();
	}

	public long fileSize() {
		return this.filesize;
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
		this.filesize = file.getSize();
		file.dupinfo2 = this;

//		Collections.sort(this.sameSizeFiles, new Comparator<FileInfo>() {
//			public int compare(FileInfo f1, FileInfo f2) {
//				return f1.getChecksums().compareTo(f2.getChecksums());
//			}
//		});
	}

	public DetailLevel getMaxDetail() {
		DetailLevel max = DetailLevel.None;

		for (FileInfo file : this.sameSizeFiles) {
			if (file.getDetailLevel().isGreaterThan(max)) {
				max = file.getDetailLevel();

				if (max == DetailLevel.MAX) {
					break;
				}
			}
		}

		return max;
	}

	/** Raise detail level of files to be consistent with each other */
	private void normalize() {
		DetailLevel max = getMaxDetail();
		// Remember if we changed any checksums
		boolean fixed = false;

		for (FileInfo file : this.sameSizeFiles) {
			file.getContext();
			if (file.getDetailLevel().isLessThan(max)) {
				file.calcChecksums(file.getContext(), max);
				fixed = true;
			}
		}

		Collections.sort(this.sameSizeFiles, new Comparator<FileInfo>() {
			public int compare(FileInfo f1, FileInfo f2) {
				int diff = f1.getDetailLevel().compareTo(f2.getDetailLevel());
				if (diff != 0) {
					return diff;
				}

				return f1.checksums.compareTo(f2.checksums);
			}
		});

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

	private void sortFilesByChecksums() {
		Collections.sort(this.sameSizeFiles, new Comparator<FileInfo>() {
			public int compare(FileInfo f1, FileInfo f2) {
				int diff = f1.getDetailLevel().compareTo(f2.getDetailLevel());
				if (diff != 0) {
					return diff;
				}

				return f1.checksums.compareTo(f2.checksums);
			}
		});
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
		//sortFilesByChecksums();

		for (int idx = 0; idx < this.sameSizeFiles.size(); ++idx) {
			FileInfo file = this.sameSizeFiles.get(idx);

			for (int otheridx = idx + 1; otheridx < this.sameSizeFiles.size(); ++otheridx) {
				FileInfo otherfile = this.sameSizeFiles.get(otheridx);

				if (file.isDuplicateOf(otherfile, true)) {
					List<FileInfo> dups = this.duplicateFiles.get(file.checksums);

					if (dups == null) {
						dups = new ArrayList<FileInfo>();
						this.duplicateFiles.put(file.checksums, dups);

						dups.add(file);
					}

					if (!dups.contains(otherfile)) {
						dups.add(otherfile);
					}
				}
			}
		}
		
		//sortFilesByChecksums();
	}

	public String toString() {
		StringBuffer sb = new StringBuffer();

		sb.append(String.format("DuplicateInfo: sz=%d files=%d chains=%d", //
				this.sameSizeFiles.get(0).getSize(), //
				this.sameSizeFiles.size(), //
				this.duplicateFiles.size()));

		return sb.toString();
	}
}
