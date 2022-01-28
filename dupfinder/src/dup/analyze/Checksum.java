package dup.analyze;

import java.io.File;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import dup.model.Context;
import dup.model.FileInfo;
import dup.util.Trace;

/** Functions for calculating checksums for files */
public abstract class Checksum {
	public static final int BUFFER_SIZE = 1024;
	public static final int prefixCount = 1;
	public static final long LARGE_FILE = 50 * 1024 * 1024;

	/** Percentage of file data to include in checksum calculations */
	private static double samplePercent() {
		return 4.0;
	}

	/** Holds information about checksums during the process of calculation */
	private static class Checksums_internal {
		public ChecksumValues checksums = new ChecksumValues();

		public final MessageDigest msgdigestPrefix;
		public final MessageDigest msgdigestSample;
		public final MessageDigest msgdigestFull;

		public Checksums_internal(DetailLevel detail) {
			MessageDigest px = null;
			MessageDigest sx = null;
			MessageDigest fu = null;

			try {
				switch (detail) {
				case Full:
					fu = MessageDigest.getInstance("MD5");
				case Sample:
					sx = MessageDigest.getInstance("MD5");
				case Prefix:
					px = MessageDigest.getInstance("MD5");
					break;

				default:
					break;
				}
			} catch (NoSuchAlgorithmException e) {
				e.printStackTrace();
			}

			this.msgdigestPrefix = px;
			this.msgdigestSample = sx;
			this.msgdigestFull = fu;
		}

		/** Create the checksum values from the stored digest information */
		public void processData() {
			if (this.msgdigestFull != null) {
				this.checksums.full = getChecksum(this.msgdigestFull);
			}

			if (this.msgdigestSample != null) {
				this.checksums.sample = getChecksum(this.msgdigestSample);
			}

			if (this.msgdigestPrefix != null) {
				this.checksums.prefix = getChecksum(this.msgdigestPrefix);
			}
		}
	}

	public static int checksumCount = 0;
	public static int samplecount = 0;

	/** Calculate checksum(s) for a file */
	public static ChecksumValues getChecksums(FileInfo finfo, Context context, DetailLevel detail) {
		assert context != null;

		// Trace.traceln(Trace.VERBOSE, "Calculating checksums for " +
		// finfo.getName());
		File file = finfo.getJavaFile(context);

		// Small files don't need checksums
		if ((file.length() <= BUFFER_SIZE) //
				&& detail.isGreaterThan(DetailLevel.Prefix)) {
			detail = DetailLevel.Prefix;
		}

		Checksums_internal cksums = new Checksums_internal(detail);

		try {
			++checksumCount;
			calcChecksums(file, cksums);
		} catch (Exception e) {
			e.printStackTrace();
		}

		if ((file.length() <= BUFFER_SIZE) //
				&& detail.isLessThan(DetailLevel.Full)) {
			cksums.checksums.full = cksums.checksums.sample = cksums.checksums.prefix;
		} else if ((file.length() > LARGE_FILE) //
				&& !detail.isLessThan(DetailLevel.Sample) //
				&& detail.isLessThan(DetailLevel.Full)) {
			cksums.checksums.full = cksums.checksums.sample;
		}

		return cksums.checksums;
	}

	/** Process checksum(s) for a file and save results */
	private static void calcChecksums(File file, Checksums_internal cksums) throws Exception {
		int sampleFreq = (int) (100.0 / samplePercent());
		RandomAccessFile raf = null;

		try {
			byte[] buffer = new byte[BUFFER_SIZE];

			// System.out.println("Opening file " + file.getName());
			raf = new RandomAccessFile(file, "r");

			for (int ii = 0;;) {
				if ((cksums.msgdigestSample == null) && (cksums.msgdigestFull == null) //
						&& (ii >= prefixCount)) {
					break;
				}

				if ((ii > prefixCount) && (cksums.msgdigestFull == null)) {
					long off = BUFFER_SIZE * (long) ii;
					// System.out.println("Seeking " + off);
					raf.seek(off);
				}

				int numRead = raf.read(buffer);
				if (numRead <= 0) {
					break;
				}

				if ((ii == 0) && (cksums.checksums.sampleBytes == null)) {
					// TODO magic number (sample buffer length == 128)
					int length = Math.min(numRead, 128);
					cksums.checksums.sampleBytes = Arrays.copyOf(buffer, length);
				}

				if ((cksums.msgdigestPrefix != null) && (ii < prefixCount)) {
					cksums.msgdigestPrefix.update(buffer, 0, numRead);
				}

				if ((cksums.msgdigestSample != null) && ((ii % sampleFreq) == 0)) {
					cksums.msgdigestSample.update(buffer, 0, numRead);
				}

				if (cksums.msgdigestFull != null) {
					cksums.msgdigestFull.update(buffer, 0, numRead);
				}

				if (cksums.msgdigestFull == null && ii >= prefixCount //
						&& cksums.msgdigestSample != null) {
					ii += sampleFreq - (ii % sampleFreq);
				} else {
					++ii;
				}
			}

			cksums.processData();
		} finally {
			if (raf != null) {
				raf.close();
			}
		}
	}

	/** Calculate prefix checksum for a file */
	public static void calculatePrefixChecksum(FileInfo finfo) {
		if (finfo.getContext() == null) {
			return;
		}

		try {
			File file = finfo.getJavaFile();
			int sum = getChecksum(createChecksumDigest(finfo, file, 8, 1.0));
			finfo.setPrefixChecksum(sum);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/** Load some file content for quick comparison later */
	public static void loadSampleBytes(FileInfo finfo) {
		calculatePrefixChecksum(finfo);
	}

	/** Calculate checksum for sample file data and save results */
	public static void calculateSampleChecksum(FileInfo finfo) {
		if (finfo.getContext() == null) {
			return;
		}

		Trace.traceln(Trace.VERBOSE, "Sample Checksum # " + ++samplecount + " " + finfo.getName());

		try {
			File file = finfo.getJavaFile();
			int sum = getChecksum(createChecksumDigest(finfo, file, -1, samplePercent()));
			finfo.setSampleChecksum(sum);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/** Calculate checksum from digest */
	private static int getChecksum(MessageDigest digest) {
		return getChecksum(digest.digest());
	}

	/** Calculate a checksum from digest data */
	private static int getChecksum(byte[] bb) {
		if (bb == null) {
			return 1;
		}

		int check = 0;

		for (byte b : bb) {
			byte x = (byte) (check >> 24);
			check <<= 8;
			check |= (x ^ b);
		}

		return (check == ChecksumValues.CKSUM_UNDEFINED) ? 1 : check;
	}

	/** Build checksum data by reading file data */
	private static byte[] createChecksumDigest(FileInfo finfo, File file, //
			int count, double percentage) //
			throws Exception {
		MessageDigest msgdigest = MessageDigest.getInstance("MD5");

		RandomAccessFile raf = null;
		byte[] buffer = new byte[1024];

		int blocksToSkip = (int) ((100.0 / percentage) - 1);
		int bytesToSkip = blocksToSkip * buffer.length;

		try {
			if (!file.isFile() || !file.canRead()) {
				return null;
			}

			raf = new RandomAccessFile(file, "r");

			for (int ii = 0; (count <= 0) || (ii < count); ++ii) {
				int numRead = raf.read(buffer);

				if (numRead <= 0) {
					break;
				}

				if (ii == 0) {
					int sampleSize = Math.min(numRead, ChecksumValues.PREFIX_LENGTH);
					finfo.setSampleBytes(buffer, sampleSize);
				}

				msgdigest.update(buffer, 0, numRead);

				if (blocksToSkip > 0) {
					int skipped = raf.skipBytes(bytesToSkip);

					if (skipped < bytesToSkip) {
						break;
					}
				}
			}
		} finally {
			if (raf != null) {
				raf.close();
			}
		}

		return msgdigest.digest();
	}
}
