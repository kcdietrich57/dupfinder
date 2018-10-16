package dup.analyze;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Arrays;

import dup.model.Database;
import dup.model.FileInfo;
import dup.util.Trace;

/** Functions related to checksum generation and file comparison */
public class Fingerprint {
	/** Determine and remember if two files are duplicates or not */
	public static boolean filesAreIdentical(FileInfo file1, FileInfo file2) {
		boolean identical = filesAreIdentical_internal(file1, file2);

		if (identical) {
			Database.instance().registerDuplicateFile(file1, file2);
		} else {
			Database.instance().registerDifferentFile(file1, file2);
		}

		return identical;
	}

	/** Analyze and compare two files */
	private static boolean filesAreIdentical_internal(FileInfo fileinfo1, FileInfo fileinfo2) {
		if (Database.skipFileComparison) {
			Trace.traceln(Trace.VERBOSE, "Skipping file compare...");
			return true;
		}

		File file1 = fileinfo1.getJavaFile();
		File file2 = fileinfo2.getJavaFile();

		if ((file1 == null) || (file2 == null)) {
			return false;
		}

		if (!file1.isFile() || !file1.canRead() || !file2.isFile() || !file2.canRead()
				|| (file1.length() != file2.length())) {
			return false;
		}

		if (Database.instance().isRegisteredDifferentFile(fileinfo1, fileinfo2)) {
			return false;
		}

		if (Database.instance().isRegisteredDuplicateFile(fileinfo1, fileinfo2)) {
			return true;
		}

		InputStream fis1 = null;
		InputStream fis2 = null;

		try {
			fis1 = new FileInputStream(file1);
			fis2 = new FileInputStream(file2);

			byte[] buffer1 = new byte[1024];
			byte[] buffer2 = new byte[1024];

			for (;;) {
				int numRead1 = fis1.read(buffer1);
				int numRead2 = fis2.read(buffer2);

				if (numRead1 != numRead2) {
					return false;
				}

				if (numRead1 <= 0) {
					return true;
				}

				if (!Arrays.equals(buffer1, buffer2)) {
					return false;
				}
			}
		} catch (Exception e) {
			return false;
		} finally {
			safeClose(fis1);
			safeClose(fis2);
		}
	}

	public static void calculatePrefixChecksum(FileInfo file) {
		Checksum.calculatePrefixChecksum(file);
	}

	public static void loadSampleBytes(FileInfo file) {
		Checksum.loadSampleBytes(file);
	}

	public static void calculateSampleChecksum(FileInfo file) {
		Checksum.calculateSampleChecksum(file);
	}

	private static void safeClose(InputStream fis) {
		try {
			if (fis != null) {
				fis.close();
			}
		} catch (Exception e) {
			// ignore
		}
	}
}
