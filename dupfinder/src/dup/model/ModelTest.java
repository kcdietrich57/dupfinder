package dup.model;

public class ModelTest {

	public static void main(String[] args) {
		Database db = Database.instance();

		Context context = db.openContext("/Users/greg/tmp", "tmp");
		if (context != null) {
			context.ingest();

			int nfiles = context.getFileCount(false);
			long tsize = context.getRoot().getSize();

			System.out.println("Found " + tsize + " bytes in " + nfiles + " files.");
		}
	}
}
