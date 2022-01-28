package dup.analyze;

public enum DetailLevel {
	None(0, "None"), //
	Size(1, "Size"), //
	Prefix(2, "Prefix"), //
	Sample(3, "Sample"),
	Full(4, "Full");

	public static final DetailLevel MAX = Full;

	public int intval;
	public String name;

	private DetailLevel(int value, String name) {
		this.intval = value;
		this.name = name;
	}

	public boolean equals(DetailLevel other) {
		return other.intval == this.intval;
	}

	public boolean isLessThan(DetailLevel other) {
		return this.intval < other.intval;
	}

	public boolean isGreaterThan(DetailLevel other) {
		return this.intval > other.intval;
	}

	public DetailLevel nextLevel() {
		switch (this.intval) {
		case 0:
			return Size;
		case 1:
			return Prefix;
		case 2:
			return Sample;
		case 3:
		default:
			return Full;
		}
	}

	public String toString() {
		return this.name;
	}
}