package dup.analyze;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import dup.model.Context;
import dup.model.FileInfo;
import dup.model.FolderInfo;
import dup.util.Trace;

/** Helper class to process a group of contexts looking for duplicates */
public final class Analyzer {
	public static void analyzeGlobalDuplicates(Collection<Context> contexts) {
		if (contexts.size() < 2) {
			return;
		}

		new Analyzer(contexts).analyze();
	}

	/** The contexts we are processing */
	private Collection<Context> contexts;
	/** All files in all of our contexts, grouped by context */
	private List<List<FileInfo>> finfos;
	/** Count of all files */
	private int totalFileCount;
	/** Keeps track of current file index per context */
	private int[] idx;
	/** The current file size we are considering */
	private long curFileSize;
	/** Files that are the current size */
	private List<FileInfo> samesize = new ArrayList<FileInfo>();
	/** */
	private long deadline;

	private Analyzer(Collection<Context> contexts) {
		this.contexts = contexts;

		this.idx = new int[this.contexts.size()];
		this.curFileSize = 0;
	}

	private void analyze() {
		this.deadline = System.currentTimeMillis();

		gatherContextFiles();

		for (;;) {
			if (!determineNextSize()) {
				break;
			}

			traceProgress();

			List<FileInfo> samesize = getSameSizeList();

			while (samesize.size() > 1) {
				List<FileInfo> dupfiles = getDuplicates(samesize);

				if (hasGlobalDuplicates(dupfiles)) {
					for (FileInfo dfile : dupfiles) {
						dfile.setGlobalDuplicates(dupfiles);
					}
				}
			}
		}
	}

	/** Return whether a collection of duplicate files includes any global dups */
	public static boolean hasGlobalDuplicates(Collection<FileInfo> dupfiles) {
		if ((dupfiles == null) || (dupfiles.size() < 2)) {
			return false;
		}

		FolderInfo firstroot = null;

		for (FileInfo file : dupfiles) {
			if (firstroot == null) {
				firstroot = file.getRootFolder();
			} else if (firstroot != file.getRootFolder()) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Get list of files in a list that duplicate the first file in the list. Remove
	 * the duplicate files from the original list and return the new list.
	 */
	private List<FileInfo> getDuplicates(List<FileInfo> samesize) {
		List<FileInfo> dupfiles = null;

		FileInfo file = samesize.remove(0);

		for (Iterator<FileInfo> iter = samesize.iterator(); iter.hasNext();) {
			FileInfo file2 = iter.next();

			if (file.isDuplicateOf(file2, true)) {
				if (dupfiles == null) {
					dupfiles = new ArrayList<FileInfo>();

					dupfiles.add(file);
				}

				dupfiles.add(file2);
				iter.remove();
			}
		}

		return dupfiles;
	}

	/** Build list of files of the current size. Empty list if no duplicates */
	private List<FileInfo> getSameSizeList() {
		List<FileInfo> samesize = this.samesize;

		samesize.clear();

		FileInfo firstfile = null;

		for (int contextNum = 0; contextNum < this.idx.length; ++contextNum) {
			List<FileInfo> contextFiles = this.finfos.get(contextNum);
			int idx = this.idx[contextNum];

			while (contextFiles.size() > idx) {
				FileInfo finfo = contextFiles.get(idx);

				if (finfo.getSize() != this.curFileSize) {
					break;
				}

				if (firstfile == null) {
					firstfile = finfo;
				} else {
					samesize.add(finfo);
				}

				++idx;
				++this.idx[contextNum];
			}
		}

		if (!samesize.isEmpty()) {
			samesize.add(firstfile);
		}

		return samesize;
	}

	/** Build lists of files per context and total file count */
	private void gatherContextFiles() {
		this.finfos = new ArrayList<List<FileInfo>>();

		for (Context context : this.contexts) {
			List<FileInfo> contextFiles = context.getAllFiles();

			this.finfos.add(contextFiles);
			this.totalFileCount += contextFiles.size();
		}
	}

	/** Trace progress of analysis */
	private void traceProgress() {
		if (System.currentTimeMillis() >= this.deadline) {
			this.deadline += 1000;

			int remaining = countRemainingFiles();

			Trace.traceln(Trace.NORMAL, " Size=" + this.curFileSize //
					+ " " + remaining + " files of " + this.totalFileCount + " remaining");
		}
	}

	/** Return total count of files remaining to process */
	private int countRemainingFiles() {
		int remaining = this.totalFileCount;

		for (int ii : this.idx) {
			remaining -= ii;
		}

		return remaining;
	}

	/** Look through next file in each context to find the next potential dups */
	private boolean determineNextSize() {
		// TODO look at determineNextSize - verify/describe algorithm
		for (;;) {
			int minidx = -1;
			long minsize = Long.MAX_VALUE;
			long secondsize = Long.MAX_VALUE;

			for (int ii = 0; ii < this.idx.length; ++ii) {
				List<FileInfo> list = this.finfos.get(ii);
				if (list.size() <= this.idx[ii]) {
					continue;
				}

				FileInfo file = list.get(this.idx[ii]);

				if (file.getSize() < minsize) {
					secondsize = minsize;
					minsize = file.getSize();
					minidx = ii;
				} else if (file.getSize() < secondsize) {
					secondsize = file.getSize();
				}
			}

			if (minidx < 0) {
				return false;
			}

			if (minsize == secondsize) {
				this.curFileSize = minsize; // First potential global dup size
				return true;
			}

			List<FileInfo> list = this.finfos.get(minidx);
			while ((this.idx[minidx] < list.size()) //
					&& list.get(this.idx[minidx]).getSize() < secondsize) {
				++this.idx[minidx];
			}
		}
	}
}
