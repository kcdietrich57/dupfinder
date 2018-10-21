package dup.analyze;

import java.util.Arrays;

/** Holds checksum values for a file */
public class ChecksumValues implements Comparable<ChecksumValues> {
	public static final int CKSUM_UNDEFINED = 0;

	private static boolean sumsAreEqual(int sum1, int sum2) {
		return compareSums(sum1, sum2) == 0;
	}

	private static int compareSums(int sum1, int sum2) {
		if (sum1 == CKSUM_UNDEFINED) {
			return (sum2 == CKSUM_UNDEFINED) ? 0 : -1;
		}

		return (sum2 == CKSUM_UNDEFINED) ? 1 : (sum1 - sum2);
	}

	public static boolean isIdentical(int cksum1, int cksum2) {
		return ((cksum1 != 0) && (cksum2 != 0) //
				&& (cksum1 == cksum2));
	}

	public static boolean isIdentical(byte[] bytes1, byte[] bytes2) {
		return ((bytes1 != null) && (bytes2 != null) //
				&& Arrays.equals(bytes1, bytes2));
	}

	public static boolean checksumsAreCompatible(int cksum1, int cksum2) {
		return (cksum1 == 0) || (cksum2 == 0) || (cksum1 == cksum2);
	}

	public static boolean bytesAreCompatible(byte[] bytes1, byte[] bytes2) {
		return (bytes1 == null) || (bytes2 == null) //
				|| Arrays.equals(bytes1, bytes2);
	}

	public int prefix;
	public int sample;
	public byte[] sampleBytes;

	public ChecksumValues() {
		this.prefix = this.sample = CKSUM_UNDEFINED;
		this.sampleBytes = null;
	}

	public void setValues(ChecksumValues source) {
		this.prefix = source.prefix;
		this.sample = source.sample;

		this.sampleBytes = (source.sampleBytes != null) //
				? Arrays.copyOf(source.sampleBytes, source.sampleBytes.length) //
				: null;
	}

	public DetailLevel getDetailLevel() {
		if (this.sampleBytes != null) {
			return DetailLevel.Sample;
		}

		if (this.sample != CKSUM_UNDEFINED) {
			return DetailLevel.Sample;
		}

		if (this.prefix != CKSUM_UNDEFINED) {
			return DetailLevel.Prefix;
		}

		return DetailLevel.Size;
	}

	public boolean equals(Object obj) {
		return (obj instanceof ChecksumValues) //
				? (compareTo((ChecksumValues) obj) == 0) //
				: false;
	}

	public int compareTo(ChecksumValues other) {
		int diff = compareSums(this.prefix, other.prefix);
		if (diff != 0) {
			return diff;
		}

		diff = compareSums(this.sample, other.sample);
		if (diff != 0) {
			return diff;
		}

		if (this.sampleBytes == null) {
			return (other.sampleBytes == null) ? 0 : -1;
		}

		if (other.sampleBytes == null) {
			return 1;
		}

		for (int ii = 0; ii < this.sampleBytes.length; ++ii) {
			int b1 = ((int) this.sampleBytes[ii]) & 0xFF;
			int b2 = ((int) other.sampleBytes[ii]) & 0xFF;

			diff = b1 - b2;
			if (diff != 0) {
				return diff;
			}
		}

		return 0;
	}

	public String toString() {
		return String.format("Cksum: p=%d s=%d sbLen=%d", //
				this.prefix, this.sample, //
				((this.sampleBytes != null) ? this.sampleBytes.length : 0));
	}
}