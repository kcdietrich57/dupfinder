package dup.analyze;

import java.util.Arrays;

/** Holds checksum values for a file */
public class ChecksumValues implements Comparable<ChecksumValues> {
	public static final int CKSUM_UNDEFINED = 0;
	public static final int PREFIX_LENGTH = 1024;

//	private static boolean sumsAreEqual(int sum1, int sum2) {
//		return compareSums(sum1, sum2) == 0;
//	}

	private static int compareSums(int sum1, int sum2) {
		if (sum1 == CKSUM_UNDEFINED) {
			return (sum2 == CKSUM_UNDEFINED) ? 0 : -1;
		}

		return (sum2 == CKSUM_UNDEFINED) ? 1 : (sum1 - sum2);
	}

	/** Return whether two checksums are known and match */
	public static boolean isIdentical(int cksum1, int cksum2) {
		return ((cksum1 != CKSUM_UNDEFINED) && (cksum2 != CKSUM_UNDEFINED) //
				&& (cksum1 == cksum2));
	}

	/** Return whether two checksums match to the known level of detail */
	public static boolean isCompatible(int cksum1, int cksum2) {
		return (cksum1 == CKSUM_UNDEFINED) || (cksum2 == CKSUM_UNDEFINED) || (cksum1 == cksum2);
	}

	public static boolean isIdentical(byte[] bytes1, byte[] bytes2) {
		if ((bytes1 == null) || (bytes2 == null)) {
			return true;
		}

		boolean isequal = Arrays.equals(bytes1, bytes2);
		return isequal;
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

	/** Clone another checksum values object */
	public void setValues(ChecksumValues source) {
		this.prefix = source.prefix;
		this.sample = source.sample;

		this.sampleBytes = (source.sampleBytes != null) //
				? Arrays.copyOf(source.sampleBytes, source.sampleBytes.length) //
				: null;
	}

	public DetailLevel getDetailLevel() {
		if (this.sample != CKSUM_UNDEFINED) {
			return DetailLevel.Sample;
		}

		if ((this.sampleBytes != null) && (this.prefix != CKSUM_UNDEFINED)) {
			return DetailLevel.Prefix;
		}

		return DetailLevel.Size;
	}

	public boolean equals(Object obj) {
		return (this == obj) //
				|| (obj instanceof ChecksumValues) //
						&& (compareTo((ChecksumValues) obj) == 0);
	}

	public boolean mayBeEqual(ChecksumValues other) {
		if (this == other) {
			return true;
		}

		// TODO assuming files are the same size

		if (this.prefix != CKSUM_UNDEFINED && other.prefix != CKSUM_UNDEFINED //
				&& this.prefix != other.prefix) {
			return false;
		}

		if (this.sample != CKSUM_UNDEFINED && other.sample != CKSUM_UNDEFINED //
				&& this.sample != other.sample) {
			return false;
		}

		return true;
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

	public int hashCode() {
		return this.prefix ^ this.sample;
	}

	public String toString() {
		return String.format("Cksum: p=%d s=%d sbLen=%d", //
				this.prefix, this.sample, //
				((this.sampleBytes != null) ? this.sampleBytes.length : 0));
	}
}