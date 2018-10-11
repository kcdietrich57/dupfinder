package dup.analyze;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import dup.model.Context;
import dup.model.FileInfo;
import dup.model.FolderInfo;
import dup.util.Trace;

public final class Analyzer {
	public static void analyzeGlobalDuplicates(Collection<Context> contexts) {
		if (contexts.size() < 2) {
			return;
		}

		new Analyzer(contexts).analyze();
	}

	private Collection<Context> contexts;
	private List<List<FileInfo>> finfos;
	private int totalFileCount;
	private int[] idx;
	private long curFileSize;
	private long deadline;
	private List<FileInfo> samesize;

	private Analyzer(Collection<Context> contexts) {
		this.contexts = contexts;

		this.idx = new int[this.contexts.size()];
		this.curFileSize = 0;
		this.samesize = new ArrayList<FileInfo>();

	}

	private void analyze() {
		this.deadline = System.currentTimeMillis();

		gatherContextFiles();

		for (;;) {
			if (!determineNextSize()) {
				break;
			}

			traceProgress();

			getSameSizeList();

			while (this.samesize.size() > 1) {
				List<FileInfo> dupfiles = getDuplicates();

				if (hasGlobalDuplicates(dupfiles)) {
					for (FileInfo dfile : dupfiles) {
						dfile.setGlobalDuplicates(dupfiles);
					}
				}
			}
		}
	}

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

	private List<FileInfo> getDuplicates() {
		List<FileInfo> dupfiles = null;

		FileInfo file = this.samesize.remove(0);

		for (Iterator<FileInfo> iter = this.samesize.iterator(); iter.hasNext();) {
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

	private void getSameSizeList() {
		this.samesize.clear();

		FileInfo firstfile = null;

		for (int ii = 0; ii < this.idx.length; ++ii) {
			List<FileInfo> list = this.finfos.get(ii);
			int idx = this.idx[ii];

			while (list.size() > idx) {
				FileInfo finfo = list.get(idx);

				if (finfo.getSize() != this.curFileSize) {
					break;
				}

				if (firstfile == null) {
					firstfile = finfo;
				} else {
					this.samesize.add(finfo);
				}

				++idx;
				++this.idx[ii];
			}
		}

		if (!this.samesize.isEmpty()) {
			this.samesize.add(firstfile);
		}
	}

	private void gatherContextFiles() {
		this.finfos = new ArrayList<List<FileInfo>>();

		for (Context context : this.contexts) {
			List<FileInfo> contextFiles = context.getAllFiles();

			this.finfos.add(contextFiles);
			this.totalFileCount += contextFiles.size();
		}
	}

	private void traceProgress() {
		if (System.currentTimeMillis() >= this.deadline) {
			this.deadline += 1000;

			int remaining = countRemainingFiles();

			Trace.traceln(Trace.NORMAL, " Size=" + this.curFileSize //
					+ " " + remaining + " files of " + this.totalFileCount + " remaining");
		}
	}

	private int countRemainingFiles() {
		int remaining = this.totalFileCount;

		for (int ii : this.idx) {
			remaining -= ii;
		}

		return remaining;
	}

	private boolean determineNextSize() {
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
			while (this.idx[minidx] < list.size() && list.get(this.idx[minidx]).getSize() < secondsize) {
				++this.idx[minidx];
			}
		}
	}
}
