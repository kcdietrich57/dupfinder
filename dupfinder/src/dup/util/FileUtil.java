package dup.util;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import dup.analyze.Checksum;
import dup.analyze.ChecksumValues;
import dup.analyze.DetailLevel;
import dup.model.Context;
import dup.model.Database;
import dup.model.FileInfo;
import dup.model.FolderInfo;
import dup.model.persist.Persistence;

public class FileUtil {
	private static final LinkOption[] NO_LINK_OPTIONS = new LinkOption[0];

	public static boolean createHardLink(String original, String newlink) {
		FileSystem fs = FileSystems.getDefault();

		return createHardLink(fs.getPath(original), fs.getPath(newlink));
	}

	public static boolean createHardLink(Path existingFile, Path newLink) {
		Path folder = newLink.getParent();

		if (!Files.exists(folder, NO_LINK_OPTIONS) //
				|| !Files.isDirectory(folder, NO_LINK_OPTIONS) //
				|| !Files.isWritable(folder) //
				|| !Files.exists(existingFile, NO_LINK_OPTIONS) //
				|| Files.exists(newLink, NO_LINK_OPTIONS)) {
			return false;
		}

		Path ret;

		try {
			ret = Files.createLink(newLink, existingFile);

			Trace.traceln(Trace.DEBUG, "ret=" + ret);
			Trace.traceln(Trace.DEBUG, "Same file? " + Files.isSameFile(existingFile, ret));

			return true;
		} catch (Exception e) {
			e.printStackTrace();
		}

		return false;
	}

	public static void main(String[] args) {
		boolean b = createHardLink("/tmp/trace.txt", "/tmp/trace2.txt");

		if (!b) {

		}
	}

	// Verify/Create disk folder to hold persisted DB info
	public static void setupDBFolder() {
		try {
			String root = "c:/";

			Path p = FileSystems.getDefault().getPath(root, "tmp");

			if (!Files.isDirectory(p)) {
				root = "/";
				p = FileSystems.getDefault().getPath(root, "tmp");

				if (!Files.isDirectory(p)) {
					throw new Exception("Can't find tmp folder.");
				}
			}

			p = FileSystems.getDefault().getPath(root, "tmp", "dupdb");

			if (!Files.exists(p)) {
				try {
					p = Files.createDirectory(p);
				} catch (Exception e) {
					throw new Exception("Can't create dupdb folder", e);
				}
			}

			if (!Files.isDirectory(p)) {
				throw new Exception("'dupdb' is not a directory");
			}

			Persistence.setFolderPath(p);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static int ingestContext(Context context) {
		return ingestContext(context, DetailLevel.Size);
	}

	public static int ingestContext(Context context, DetailLevel detail) {
		if (context == null) {
			return 0;
		}

		int count = context.getFileCount(true);
		if (count > 0) {
			return count;
		}

		if (context.getRootFile() == null) {
			return 0;
		}

		FolderInfo folder = context.getRoot();

		loadCounter = 0;

		int fileCount = ingestTree(context, folder);
		Trace.traceln(Trace.NORMAL);

		context.determineDetailLevel();
		context.determineCurrentFileCount();

		Trace.traceln(Trace.NORMAL);
		Trace.traceln(Trace.NORMAL, "Ingested " + fileCount + " files");

		if (detail.isGreaterThan(context.getDetailLevel())) {
			ingestFileDetails(context, detail);
		}

		context.determineDetailLevel();

		return fileCount;
	}

	private static int loadCounter = 0;

	public static int addFile(List<FileInfo> files, FileInfo file) {
		int idx = Collections.binarySearch(files, file, //
				new Comparator<FileInfo>() {
					public int compare(FileInfo f1, FileInfo f2) {
						long diff = f1.getSize() - f2.getSize();

						if (diff < 0) {
							return -1;
						} else if (diff == 0) {
							return 0;
						} else {
							return 1;
						}
					}
				});

		if (idx < 0) {
			idx = -(idx + 1);
		}

		files.add(idx, file);

		return idx;
	}

	private static int ingestTree(Context context, FolderInfo folder) {
		if (folder == null) {
			return 0;
		}

		File absFile = new File(context.getRootFile(), folder.getRelativeName());
		File[] children = absFile.listFiles();
		if (children == null) {
			return 0;
		}

		for (File child : children) {
			if (child.isDirectory()) {
				FolderInfo childInfo = new FolderInfo(folder, child);

				ingestTree(context, childInfo);

				folder.addFolder(childInfo);
			} else if (child.isFile()) {
				FileInfo fileInfo = new FileInfo(folder, child);

				folder.addFile(fileInfo);
				Database.instance().addFile(fileInfo);
				context.addFile(fileInfo);

				if ((++loadCounter % 1000) == 0) {
					Trace.trace(Trace.NORMAL, ".");
					Trace.traceln(Trace.VERBOSE, "Ingest file #" + loadCounter);
				}
			}
		}

		context.determineDetailLevel();

		return folder.getTreeFileCount();
	}

	private static int ingestFileDetails(Context context, DetailLevel detail) {
		if (!detail.isGreaterThan(context.getDetailLevel())) {
			return 0;
		}

		int filecount = 0;

		Iterator<FileInfo> fileiter = context.getRoot().iterateFiles(true);
		while (fileiter.hasNext()) {
			FileInfo finfo = fileiter.next();

			if (finfo.getDetailLevel().isLessThan(detail)) {
				ChecksumValues checksums = Checksum.getChecksums(finfo, context, detail);
				finfo.setChecksums(checksums);

				++filecount;
			}
		}

		context.determineDetailLevel();

		return filecount;
	}

	public static int compareCount = 0;

	public static boolean compareContents(FileInfo file1, FileInfo file2) {
		Context context1 = file1.getContext();
		Context context2 = file2.getContext();

		if (context1 == null || context2 == null) {
			return true;
		}

		FileInputStream is1 = null;
		FileInputStream is2 = null;
		long bytesread = 0;

		try {
			File jf1 = file1.getJavaFile(context1);
			File jf2 = file2.getJavaFile(context2);

			// TODO If the file has gone missing, assume it was a duplicate...
			if (!jf1.isFile() || !jf1.canRead() //
					|| !jf2.isFile() || !jf2.canRead()) {
				return true;
			}

			++compareCount;

			is1 = new FileInputStream(jf1);
			is2 = new FileInputStream(jf2);

			byte[] buffer1 = new byte[4096];
			byte[] buffer2 = new byte[4096];

			for (;;) {
				int len1 = is1.read(buffer1);
				if (len1 <= 0) {
					return true;
				}

				bytesread += len1;

				int len2 = is2.read(buffer2);

				if ((len1 != len2) //
						|| !Arrays.equals(buffer1, buffer2)) {
					return false;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				is1.close();
			} catch (Exception e) {
			}
			try {
				is2.close();

			} catch (Exception e) {
			}

			Trace.traceln(Trace.VERBOSE, "CompareFiles: " //
					+ file1.getName() + " to " + file2.getName() //
					+ " Read " + bytesread + " of " + file1.getSize() + " bytes.");
		}

		return false;
	}
}
