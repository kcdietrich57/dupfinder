package dup.model;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

/** Functions to create/reload a persisted context */
class ContextLoader {
	public static Context loadContextFromFile(File file) {
		FileInputStream fstream = null;
		DataInputStream in = null;
		BufferedReader br = null;

		Context context = null;

		try {
			fstream = new FileInputStream(file);
			in = new DataInputStream(fstream);
			br = new BufferedReader(new InputStreamReader(in));

			String contextLocation = Persistence.parseLineWithString(br.readLine(), "Location:");
			String contextName = Persistence.parseLineWithString(br.readLine(), "Context:");

			String v = Persistence.parseLineWithString(br.readLine(), "Version:");

			if ((contextName == null) || (contextLocation == null) || (v == null)) {
				return null;
			}

			context = new Context(contextLocation, contextName);
			context.getRoot();

			context.setDetailLevel(DetailLevel.Sample);

			if (!v.equals(Persistence.VERSION)) {
				throw new Exception("Data file version mismatch: " + v + " Expected: " + Persistence.VERSION);
			}

			context.setVersion(v);

			String line = br.readLine();

			for (;;) {
				String folderName = Persistence.parseLineWithString(line, "Folder:");
				if (folderName == null) {
					break;
				}

				line = load(br, context, folderName);
				if (line == null) {
					break;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}

		context.determineCurrentFileCount();

		return context;
	}

	public static File extractLocationFromSavedContext(File savedContext) {
		FileInputStream fstream = null;
		DataInputStream in = null;
		BufferedReader br = null;

		try {
			fstream = new FileInputStream(savedContext);
			in = new DataInputStream(fstream);
			br = new BufferedReader(new InputStreamReader(in));

			String contextLocation = Persistence.parseLineWithString(br.readLine(), "Location:");

			return new File(contextLocation);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}

		return null;
	}

	/** Reload persisted context information for a particular folder */
	private static String load(BufferedReader br, Context context, String folderPath) throws IOException {
		String line = null;

		File folderFile = new File(folderPath);
		FolderInfo folder = context.findOrCreateFolder(folderFile);

		for (line = br.readLine(); line != null; line = br.readLine()) {
			FileInfo finfo = Persistence.parseFileInfo(line, folder);
			if (finfo == null) {
				break;
			}

			folder.addFile(finfo);

			if (finfo.getDetailLevel().isLessThan(context.getDetailLevel())) {
				context.setDetailLevel(finfo.getDetailLevel());
			}
		}

		return line;
	}
}