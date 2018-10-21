package dup.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import dup.model.Context;
import dup.model.FileInfo;
import dup.model.FolderInfo;

/** General utility functions */
public final class Utility {
	private static final long K = 1024;

	public static String formatPercent(float ratio) {
		return String.format("%5.2f", (ratio * 100.0)).trim();
	}

	public static String formatSize(long size) {
		int unit = 0;
		long frac = 0;

		while (size > K) {
			++unit;

			if (frac >= 500) {
				++size;
			}

			frac = size % K;
			size = size / K;
		}

		return String.format("%d.%01d%c", //
				Long.valueOf(size), //
				Long.valueOf((frac * 100) / 1024), //
				"bKMGT".charAt(unit));
	}

	/** Given an array of selected objects, create a list of FileObjects */
	public static List<FileInfo> gatherFilesFromSelection(Object[] selobjs) {
		List<FileInfo> files = new ArrayList<FileInfo>();

		if (selobjs == null) {
			return files;
		}

		for (Object selobj : selobjs) {
			if (selobj instanceof FileInfo) {
				files.add((FileInfo) selobj);
				continue;
			}

			if (selobj instanceof Context) {
				selobj = ((Context) selobj).getRoot();
			}

			if (selobj instanceof FolderInfo) {
				FolderInfo folder = (FolderInfo) selobj;

				Iterator<FileInfo> iter = folder.iterateFiles(true);
				while (iter.hasNext()) {
					files.add(iter.next());
				}
			}
		}

		return files;
	}

	public static void reportMemory(String when) {
		Runtime rt = Runtime.getRuntime();
		long totmem = rt.totalMemory();
		long freemem = rt.freeMemory();

		Trace.traceln(Trace.NORMAL, "Memory usage " + when //
				+ ": " + Utility.formatSize(freemem) + " free of " + Utility.formatSize(totmem));
	}

	// TODO this should return some representation of the delta. For now it just
	// emits trace statements.
	//
	// This will calculate the delta between two contexts (or two versions).
	// The contexts must be loaded and the comparison will be limited to the
	// information available in the FileInfo objects.
	// No file reading is performed - it is assumed that if the available
	// size and checksum information matches, the files are identical.
	// This is to allow offline comparisons.
	//
	// The return value indicates the level of confidence.
//	public static DetailLevel compareContexts(Context context,
//			Context refContext)
//	{
//		// Files that exist in context, but nowhere in refContext.
//		// Duplicates may exist in context.
//		List<FileInfo> newFiles = new ArrayList<FileInfo>();
//
//		// Files that exist in refContext, but nowhere in context.
//		// Duplicates may exist in refcontext.
//		List<FileInfo> deletedFiles = new ArrayList<FileInfo>();
//
//		// Files that exist in both contexts but whose contents differ.
//		// Upon analysis, this is changed to a combination of other changes:
//		//
//		// If ctx.a exists nowhere in ref, and ref.a exists nowhere in ctx:
//		// DELETE a + NEW a (i.e. update ctx FileInfo metadata from ref)
//		//
//		// TODO ugh this gets ugly. Simple for now, Figure it out later...
//		// However, if ctx.a != ref.a and duplicates ref.b:
//		// MOVE a TO b (i.e. create FileInfo for ctx.b if not already there)
//		// - or DELETE a (if ctx.b already exists)
//		// NEW a
//		// Likewise, if ctx.a != ref.a, but ctx.b duplicates ref.a:
//		// NEW a (update ctx.a with info from ref.a)
//		// ctx.b should take care of itself
//		List<FileChange> changedFiles = new ArrayList<FileChange>();
//
//		// Simplistic for now -
//		// If a deleted and new file appear to be the same based on the info
//		// we have available, we will assume the file was moved/renamed.
//		List<FileChange> movedFiles = new ArrayList<FileChange>();
//
//		context.compareToContext(refContext, newFiles, deletedFiles,
//				changedFiles);
//
//		// Backwards to detect multiple new files that match one deleted file
//		findMovedFiles(deletedFiles, newFiles, movedFiles, false);
//		movedFiles.clear();
//
//		// This will warn about a new file that matches multiple deleted files
//		// (which generally is a desirable outcome - less duplicates as a
//		// result)
//		findMovedFiles(newFiles, deletedFiles, movedFiles, true);
//
//		DetailLevel confidence = (context.getDetailLevel()
//				.isLessThan(refContext.getDetailLevel()))
//				? context.getDetailLevel()
//				: refContext.getDetailLevel();
//
//		Trace.traceln();
//		Trace.traceln("Results of reloading database for " + context.getName());
//		Trace.traceln("The confidence level was " + confidence);
//		Trace.traceln();
//		Trace.traceln("" + deletedFiles.size() + " were deleted.");
//		for (FileInfo file : deletedFiles) {
//			Trace.traceln("  " + file.getRelativeName());
//		}
//		Trace.traceln("" + newFiles.size() + " were added.");
//		for (FileInfo file : newFiles) {
//			Trace.traceln("  " + file.getRelativeName());
//		}
//		Trace.traceln("" + changedFiles.size() + " were different.");
//		for (FileChange fc : changedFiles) {
//			Trace.traceln("  " + fc.before.getRelativeName());
//			Trace.traceln("  -> " + fc.after.getRelativeName());
//		}
//		Trace.traceln("" + movedFiles.size() + " were moved/renamed.");
//		for (FileChange fc : movedFiles) {
//			Trace.traceln("  " + fc.before.getRelativeName());
//			Trace.traceln("  -> " + fc.after.getRelativeName());
//		}
//		Trace.traceln();
//
//		if (newFiles.isEmpty() && deletedFiles.isEmpty()
//				&& changedFiles.isEmpty()) {
//			return confidence;
//		}
//
//		for (FileInfo file : deletedFiles) {
//			context.removeFile(file);
//		}
//
//		for (FileInfo file : newFiles) {
//			context.addFile(file);
//		}
//
//		for (FileChange change : changedFiles) {
//			context.removeFile(change.before);
//			context.addFile(change.after);
//		}
//
//		for (FileChange change : movedFiles) {
//			context.removeFile(change.before);
//			context.addFile(change.after);
//		}
//
//		return confidence;
//	}

//	private static void findMovedFiles(List<FileInfo> newFiles,
//			List<FileInfo> deletedFiles, List<FileChange> movedFiles,
//			boolean cleanUpLists)
//	{
//		Collections.sort(newFiles, Context.compareFileSize);
//		Collections.sort(deletedFiles, Context.compareFileSize);
//
//		int delfileIdx = 0;
//
//		for (FileInfo newfile : newFiles) {
//			int movecount = 0;
//
//			for (int ii = delfileIdx; ii < deletedFiles.size();) {
//				FileInfo delfile = deletedFiles.get(ii);
//
//				if (newfile.getSize() < delfile.getSize()) {
//					break;
//				}
//				if (newfile.getSize() > delfile.getSize()) {
//					delfileIdx = ++ii;
//					continue;
//				}
//
//				if (newfile.getDetailLevel().isLessThan(
//						delfile.getDetailLevel())) {
//					// TODO improve newfile's detail to match
//					// newfile.load(delfile.getDetailLevel());
//				}
//
//				if (newfile.matches(delfile)) {
//					FileChange moveinfo = new FileChange(delfile, newfile);
//					movedFiles.add(moveinfo);
//					++movecount;
//				}
//
//				++ii;
//			}
//
//			if (movecount > 1) {
//				Trace.trace((cleanUpLists)
//						? "Warning: new file replaces multiple deleted files"
//						: "Warning: multiple move copies detected");
//			}
//		}
//
//		if (cleanUpLists) {
//			for (FileChange fc : movedFiles) {
//				newFiles.remove(fc.after);
//				deletedFiles.remove(fc.before);
//			}
//		}
//	}

	// no instances
	private Utility() {
	}
}
