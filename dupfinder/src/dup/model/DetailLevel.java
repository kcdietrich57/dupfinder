package dup.model;

public enum DetailLevel {
	None(0, "None"), //
	Size(1, "Size"), //
	Prefix(2, "Prefix"), //
	Sample(3, "Sample");

	public static final DetailLevel MAX = Sample;

	int intval;
	String name;

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

	public String toString() {
		return this.name;
	}
}