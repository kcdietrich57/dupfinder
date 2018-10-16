package dup.analyze;

import java.io.File;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import dup.model.Context;
import dup.model.DetailLevel;
import dup.model.FileInfo;
import dup.util.Trace;

public abstract class Checksum {
	public static final int CKSUM_UNDEFINED = 0;
	public static final int BUFFER_SIZE = 1024;
	public static final int prefixCount = 1;

	/** Percentage of file data to include in checksum calculations */
	private static double samplePercent() {
		return 2.0;
	}

	/** Holds checksum values for a file */
	public static class ChecksumValues {
		public int prefix;
		public int sample;
		public byte[] sampleBytes;

		public ChecksumValues() {
			this.prefix = this.sample = CKSUM_UNDEFINED;
			this.sampleBytes = null;
		}

		public boolean equals(Object obj) {
			if (!(obj instanceof ChecksumValues)) {
				return false;
			}

			ChecksumValues other = (ChecksumValues) obj;

			if (!compareSums(this.prefix, other.prefix) //
					|| !compareSums(this.sample, other.sample)) {
				return false;
			}

			boolean b = (sampleBytes == null) || (other.sampleBytes == null) //
					|| Arrays.equals(this.sampleBytes, other.sampleBytes);

			if (!b) {
				Trace.traceln(Trace.DEBUG, "False positive on checksum");
			}

			return b;
		}

		private boolean compareSums(int sum1, int sum2) {
			if ((sum1 == Checksum.CKSUM_UNDEFINED) || (sum2 == Checksum.CKSUM_UNDEFINED)) {
				return true;
			}

			return sum1 == sum2;
		}
	}

	/** Holds information about checksums during the process of calculation */
	private static class Checksums_internal {
		public ChecksumValues checksums = new ChecksumValues();

		public final MessageDigest msgdigestPrefix;
		public final MessageDigest msgdigestSample;

		public Checksums_internal(DetailLevel detail) {
			MessageDigest px = null;
			MessageDigest sx = null;

			try {
				switch (detail) {
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
		}

		/** Create the checksum values from the stored digest information */
		public void processData() {
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
				&& detail.isLessThan(DetailLevel.Sample)) {
			cksums.checksums.sample = cksums.checksums.prefix;
		}

		return cksums.checksums;
	}

	/** Process checksum(s) for a file and save results */
	private static void calcChecksums(File file, Checksums_internal cksums) throws Exception {
		int sampleFreq = (int) (100.0 / samplePercent());
		RandomAccessFile raf = null;

		try {
			byte[] buffer = new byte[BUFFER_SIZE];

			raf = new RandomAccessFile(file, "r");

			for (int ii = 0;; ++ii) {
				if (cksums.msgdigestSample != null) {
					if ((ii > prefixCount) && (0 != (ii % sampleFreq))) {
						continue;
					}

					raf.seek(BUFFER_SIZE * (long) ii);
				}

				if ((cksums.msgdigestSample == null) && (ii >= prefixCount)) {
					break;
				}

				int numRead = raf.read(buffer);
				if (numRead <= 0) {
					break;
				}

				if ((ii == 0) && (cksums.checksums.sampleBytes == null)) {
					int length = Math.min(numRead, 128);
					cksums.checksums.sampleBytes = Arrays.copyOf(buffer, length);
				}

				if ((cksums.msgdigestPrefix != null) && (ii < prefixCount)) {
					cksums.msgdigestPrefix.update(buffer, 0, numRead);
				}

				if ((cksums.msgdigestSample != null) && ((ii % sampleFreq) == 0)) {
					cksums.msgdigestSample.update(buffer, 0, numRead);
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

		return (check == CKSUM_UNDEFINED) ? 1 : check;
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
					int sampleSize = Math.min(numRead, 512);
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
