package dup.util;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import dup.analyze.DupDiffFileInfo;
import dup.analyze.RegisteredDupDiffInfo;
import dup.model.Context;
import dup.model.Database;
import dup.model.FileInfo;
import dup.model.FileObjectInfo;
import dup.model.FolderInfo;

public class Dumper {

	public static void dumpSelectionDuplicateInfo(Object[] selections) {
		if (selections.length == 0) {
			return;
		}

		Object selobj = selections[0];

		List<FileInfo> globalDups = new ArrayList<FileInfo>();
		List<FileInfo> contextDups = new ArrayList<FileInfo>();
		List<FileInfo> bothDups = new ArrayList<FileInfo>();

		if (selobj instanceof Context) {
			selobj = ((Context) selobj).getRoot();
		}

		if (selobj instanceof FileObjectInfo) {
			FileObjectInfo fileobj = (FileObjectInfo) selobj;

			if (fileobj instanceof FolderInfo) {
				bothDups = Dumper.getDuplicateFiles((FolderInfo) fileobj, true);
			} else if (fileobj instanceof FileInfo) {
				FileInfo fileinfo = (FileInfo) fileobj;

				Set<FileInfo> set = new HashSet<FileInfo>();
				set.addAll(fileinfo.getContextDuplicates());
				set.addAll(fileinfo.getGlobalDuplicates());
				bothDups.addAll(set);
			}
		}

		for (Iterator<FileInfo> iter = bothDups.iterator(); iter.hasNext();) {
			FileInfo file = iter.next();

			if (file.hasGlobalDuplicates()) {
				if (!file.hasContextDuplicates()) {
					globalDups.add(file);
					iter.remove();
				}
			} else if (file.hasContextDuplicates()) {
				contextDups.add(file);
				iter.remove();
			}
		}

		if (selobj instanceof FileInfo) {
		} else {
			String selname = (selobj instanceof Database) ? "database" : "";
			Trace.traceln(Trace.NORMAL,
					"Duplicates in " + selname + " global: " + globalDups.size() + " context: " + contextDups.size());

			return;
		}

		FileInfo file = (FileInfo) selobj;

		Trace.traceln(Trace.NORMAL, "Duplicates for file " + file.getName());

		for (FileInfo f : contextDups) {
			if (f != file) {
				dumpFile(f, " C");
			}
		}

		for (FileInfo f : bothDups) {
			if (f != file) {
				dumpFile(f, "GC");
			}
		}

		for (FileInfo f : globalDups) {
			if (f != file) {
				dumpFile(f, "G ");
			}
		}

		Trace.traceln();
		Trace.traceln(Trace.NORMAL, "Verified Duplicates (file)");

		// TODO think about the idea of VERIFIED duplicates
//		DuplicateInfo2 dupinfo = Database.instance().getDuplicateInfo(file);
//		if (dupinfo != null) {
//			List<FileInfo> dups = dupinfo.getDuplicates(file);
//
//			if (dups != null) {
//				for (FileInfo f : dups) {
//					Trace.traceln(Trace.NORMAL, " " + f.getFullName());
//				}
//			}
//		}

		Trace.traceln();
		Trace.traceln(Trace.NORMAL, "Verified Differences (file)");

//		DuplicateInfo di = file.getDupinfo();
//		for (FileInfo f : di.getVerifiedDifferentFiles()) {
//			Trace.traceln(Trace.NORMAL, " " + f.getFullName());
//		}

		File jfile = file.getJavaFile();

		Trace.traceln();
		Trace.traceln(Trace.NORMAL, "Registered Duplicates (DB)");

		for (DupDiffFileInfo f : RegisteredDupDiffInfo.getRegisteredDuplicates(jfile)) {
			Trace.traceln(Trace.NORMAL, " " + f.filename);
		}

		Trace.traceln();
		Trace.traceln(Trace.NORMAL, "Registered Differences (DB)");

		for (DupDiffFileInfo f : RegisteredDupDiffInfo.getRegisteredDifferentFiles(jfile)) {
			Trace.traceln(Trace.NORMAL, " " + f.filename);
		}
	}

	private static void dumpFile(FileInfo f, String s) {
		int level = Trace.NORMAL;
		String contextname = (f.getContext() != null) //
				? f.getContext().getName() //
				: "NO_CONTEXT";

		Trace.trace(level, s + "  Sz: " + f.getSize());
		Trace.trace(level, " Ps: " + f.getPrefixChecksum());
		Trace.trace(level, " Ss: " + f.getSampleChecksum());
		Trace.trace(level, "  " + contextname);
		Trace.trace(level, " " + f.getName());
		Trace.traceln(level);
	}

	/**
	 * Get non-unique files in this folder. If recurse is true, get all non-unique
	 * files in the entire sub-tree.
	 */
	public static List<FileInfo> getDuplicateFiles(FolderInfo folder, boolean recurse) {
		Set<FileInfo> files = new HashSet<FileInfo>();

		for (Iterator<FileInfo> fiter = folder.iterateFiles(recurse); fiter.hasNext();) {
			FileInfo file = fiter.next();

			if (!file.isUnique()) {
				files.add(file);
			}
		}

		return new ArrayList<FileInfo>(files);
	}
}
