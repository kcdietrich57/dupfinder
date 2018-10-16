package dup.model;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.file.Path;

import dup.model.Database.DupDiffFileInfo;
import dup.model.Database.RegisteredDupDiffInfo;

public class Persistence {
	public static final String VERSION = "1.1 2/23/2013";
	private static final String DBFILE_NAME = "__DUPDB__";

	private final static char ENCODE_SENTINEL = '&';
	private final static char ENCODE_TERMINATOR = ';';

	private static final String hexDigits = "0123456789ABCDEF";

	private static File dbfolder;

	public static File findSavedContext(File rootFolder) {
		File embeddedDBFile = new File(rootFolder, DBFILE_NAME);
		if (embeddedDBFile.isFile() && embeddedDBFile.canRead()) {
			return embeddedDBFile;
		}

		FileFilter filter = new FileFilter() {
			public boolean accept(File pathname) {
				return pathname.getName().endsWith(".db");
			}
		};

		File[] dbfiles = dbfolder.listFiles(filter);

		for (File file : dbfiles) {
			if (!file.isFile() || !file.canRead()) {
				continue;
			}

			File location = ContextLoader.extractLocationFromSavedContext(file);

			if (rootFolder.equals(location)) {
				return file;
			}
		}

		return null;
	}

//	private static void saveDatabase() {
//		PrintStream ps = null;
//		try {
//			File dbfile = new File(dbfolder, "__DB__.dat");
//
//			ps = new PrintStream(dbfile);
//
//			Persistence.saveDatabase(ps);
//		} catch (Exception e) {
//			e.printStackTrace();
//		} finally {
//			if (ps != null) {
//				ps.close();
//			}
//		}
//	}

	public static void loadDatabase() {
		File dbfile = new File(dbfolder, "__DB__.dat");
		if (!dbfile.exists()) {
			return;
		}

		FileInputStream fstream = null;
		DataInputStream in = null;
		BufferedReader br = null;

		try {
			fstream = new FileInputStream(dbfile);
			in = new DataInputStream(fstream);
			br = new BufferedReader(new InputStreamReader(in));

			String v = Persistence.parseLineWithString(br.readLine(), "DATABASE");
			v = Persistence.parseLineWithString(br.readLine(), "Version:");

			if (!v.equals(Persistence.VERSION)) {
				throw new Exception("Data file version mismatch: " + v //
						+ " Expected: " + Persistence.VERSION);
			}

			String line = br.readLine();

			while (line != null) {
				if ((line == null) || !line.equals("Registered Differences:")) {
					break;
				}

				line = Persistence.parseDiffInfo(line, br);
			}

			while (line != null) {
				if ((line == null) || !line.equals("Registered Duplicates:")) {
					break;
				}

				line = Persistence.parseDupInfo(line, br);
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
	}

	private static RegisteredDupDiffInfo findOrCreateDupDiffInfo(String filename, String line) {
		RegisteredDupDiffInfo info = Database.instance().getRegisteredDupDiffInfo(filename);

		if (info == null) {
			StringBuilder data = new StringBuilder(line);

			int space = data.indexOf(" ");
			assert space > 0;
			int count = parseIntHex(data.substring(0, space));
			data.delete(0, space + 1);

			space = data.indexOf(" ");
			assert space > 0;
			long size = parseLongHex(data.substring(0, space));
			data.delete(0, space + 1);

			space = data.indexOf(" ");
			assert space > 0;
			long timestamp = parseLongHex(data.substring(0, space));
			data.delete(0, space + 1);

			space = data.indexOf(" ");
			assert space > 0;
			int psum = parseIntHex(data.substring(0, space));
			data.delete(0, space + 1);

			space = data.indexOf(" ");
			assert space > 0;
			int ssum = parseIntHex(data.substring(0, space));
			data.delete(0, space + 1);

			info = new RegisteredDupDiffInfo( //
					new File(filename), size, timestamp, psum, ssum);

			Database.instance().registerDupDiffInfo(info);
		}

		return info;
	}

	private static String parseDiffInfo(String line, BufferedReader br) throws IOException {
		String label = "Registered Differences:";
		if (!line.equals(label)) {
			return line;
		}

		String filename = decodeString(br.readLine());

		RegisteredDupDiffInfo info = findOrCreateDupDiffInfo(filename, br.readLine());

		line = br.readLine();

		while (line != null) {
			DupDiffFileInfo file = parseDupDiffFileInfo("Diff:", line);
			if (file == null) {
				break;
			}

			info.addDifferent(file);

			line = br.readLine();
		}

		return line;
	}

	private static String parseDupInfo(String line, BufferedReader br) throws IOException {
		String label = "Registered Duplicates:";
		if (!line.equals(label)) {
			return line;
		}

		String filename = decodeString(br.readLine());

		RegisteredDupDiffInfo info = findOrCreateDupDiffInfo(filename, br.readLine());

		line = br.readLine();

		while (line != null) {
			DupDiffFileInfo file = parseDupDiffFileInfo("Dup:", line);
			if (file == null) {
				break;
			}

			info.addDuplicate(file);

			line = br.readLine();
		}

		return line;
	}

	private static DupDiffFileInfo parseDupDiffFileInfo(String label, String line) {
		if (!line.startsWith(label)) {
			return null;
		}

		line = line.substring(label.length());

		StringBuilder data = new StringBuilder(line);

		int space = data.indexOf(" ");
		assert space > 0;
		String filename = decodeString(data.substring(0, space));
		data.delete(0, space + 1);

		long timestamp = parseLongHex(data.toString());

		return new DupDiffFileInfo(filename, timestamp);
	}

	public static void save(Context context) {
		PrintStream ps = null;
		try {
			File dbfile = File.createTempFile(context.getName(), ".db", dbfolder);

			ps = new PrintStream(dbfile);
			Persistence.save(context, ps);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (ps != null) {
				ps.close();
			}
		}
	}

//	private static void saveDatabase(PrintStream ps) {
//		Database db = Database.instance();
//
//		if (!db.isDirty()) {
//			// TODO return;
//		}
//
//		ps.println("DATABASE");
//		ps.println("Version:" + VERSION);
//
//		for (Map.Entry<String, RegisteredDupDiffInfo> entry : //
//		Database.instance().getRegisteredDupDiffInfo().entrySet()) {
//			String key = entry.getKey();
//			RegisteredDupDiffInfo info = entry.getValue();
//
//			if ((info == null) || info.differentFiles.isEmpty()) {
//				continue;
//			}
//
//			ps.println("Registered Differences:");
//			ps.println(encodeString(key));
//
//			ps.println(hexString(info.differentFiles.size()) //
//					+ " " + hexString(info.filesize) //
//					+ " " + hexString(info.timestamp) //
//					+ " " + hexString(info.psum) //
//					+ " " + hexString(info.ssum));
//
//			for (DupDiffFileInfo f : info.differentFiles) {
//				ps.println("Diff:" //
//						+ encodeString(f.filename) //
//						+ " " + hexString(f.timestamp));
//			}
//		}
//
//		Set<String> keys = new HashSet<String>(//
//				Database.instance().getRegisteredDupDiffInfo().keySet());
//
//		while (!keys.isEmpty()) {
//			String key = keys.iterator().next();
//			keys.remove(key);
//
//			RegisteredDupDiffInfo info = Database.instance().getRegisteredDupDiffInfo(key);
//
//			if (info.duplicateFiles.isEmpty()) {
//				continue;
//			}
//
//			ps.println("Registered Duplicates:");
//			ps.println(encodeString(key));
//
//			ps.println(hexString(info.differentFiles.size()) //
//					+ " " + hexString(info.filesize) //
//					+ " " + hexString(info.timestamp) //
//					+ " " + hexString(info.psum) //
//					+ " " + hexString(info.ssum));
//
//			for (DupDiffFileInfo file : info.duplicateFiles) {
//				keys.remove(file);
//
//				ps.println("Dup:" //
//						+ encodeString(file.filename) //
//						+ " " + hexString(file.timestamp));
//			}
//		}
//
//		db.setDirty(false);
//	}

	private static void save(Context context, PrintStream ps) {
		File rootFile = context.getRootFile();

		ps.println("Location:" + encodeString(rootFile.getAbsolutePath()));
		ps.println("Context:" + encodeString(context.getName()));
		ps.println("Version:" + context.getVersion());

		FolderInfo folder = context.getRoot();
		saveFolder(folder, ps);

		context.setDirty(false);
	}

	private static void saveFolder(FolderInfo folder, PrintStream ps) {
		ps.println("Folder:" + encodeString(folder.getRelativeName()));

		for (FileInfo file : folder.getFiles()) {
			ps.println("File:" + encodeString(file.getName()) //
					+ " " + hexString(file.getSize()) //
					+ " " + hexString(file.getLastModified()) //
					+ " " + hexString(file.getPrefixChecksum()) //
					+ " " + hexString(file.getSampleChecksum()));

			// Note that for verified dups/diffs, we know that the checksums and
			// sizes are the same. All that we need to save is the modified date
			// and (absolute) file location.
			// If the file moves or the modified date changes, we will consider
			// the validation to be voided and we will need to compare the files
			// again.
//			for (FileInfo vdup : file.getDupinfo().getVerifiedDuplicates()) {
//				if (vdup == file) {
//					continue;
//				}
//
//				ps.println("VDup:" //
//						+ " " + hexString(file.getLastModified()) //
//						+ " " + encodeString(vdup.getFullName()));
//
//			}
//
//			for (FileInfo vdup : file.getDupinfo().getVerifiedDifferentFiles()) {
//				ps.println("VDiff:" //
//						+ " " + hexString(file.getLastModified()) //
//						+ " " + encodeString(vdup.getFullName()));
//
//			}
		}

		for (FolderInfo subfolder : folder.getSubfolders()) {
			saveFolder(subfolder, ps);
		}
	}

	private static String hexString(Integer value) {
		return String.format("%1$08X", value);
	}

	private static String hexString(Long value) {
		return String.format("%1$016X", value);
	}

	private static String encodeString(String s) {
		StringBuilder sb = new StringBuilder(s);

		for (int ii = 0; ii < sb.length(); ++ii) {
			Integer c = new Integer(sb.charAt(ii));

			if ((c <= 0x20) || (c > 0x7F) || (c == ENCODE_SENTINEL)) {
				sb.setCharAt(ii, ENCODE_SENTINEL);
				++ii;

				sb.insert(ii, String.format("%1$x;", c));
			}
		}

		return sb.toString();
	}

	/**
	 * Parse an input line starting with a identifying string.
	 * 
	 * @param line  The complete line
	 * @param label The ID tag to look for at the start of the line
	 * @return The line, minus the tag. If tag is missing, return null.
	 */
	public static String parseLineWithString(String line, String label) {
		if (!line.startsWith(label)) {
			return null;
		}

		return decodeString(line.substring(label.length()));
	}

	public static FileInfo parseFileInfo(String line, FolderInfo folder) {
		String label = "File:";

		if (!line.startsWith(label)) {
			return null;
		}

		StringBuilder data = new StringBuilder(line.substring(label.length()));

		int space = data.indexOf(" ");
		assert space > 0;
		String name = decodeString(data.substring(0, space));
		data.delete(0, space + 1);

		space = data.indexOf(" ");
		assert space > 0;
		long size = parseLongHex(data.substring(0, space));
		data.delete(0, space + 1);

		space = data.indexOf(" ");
		assert space > 0;
		long timestamp = parseLongHex(data.substring(0, space));
		data.delete(0, space + 1);

		FileInfo finfo = new FileInfo(folder, name, size, timestamp);

		space = data.indexOf(" ");
		assert space > 0;
		finfo.setPrefixChecksum(parseIntHex(data.substring(0, space)));
		data.delete(0, space + 1);

		space = data.indexOf(" ");
		assert space > 0;
		finfo.setSampleChecksum(parseIntHex(data.substring(0, space)));

		return finfo;
	}

	/**
	 * Parse saved information about a related file (path + modified time) If the
	 * file no longer exists or the timestamp does not match, we ignore it.
	 * 
	 * @param line The line containing the encoded information
	 * @return The file, if it appears to be unchanged
	 */
	public static File parseAndVerifyRelatedFileInfo(String line) {
		StringBuilder data = new StringBuilder(line);

		int space = data.indexOf(" ");
		assert space > 0;
		String name = decodeString(data.substring(0, space));
		data.delete(0, space + 1);

		File other = new File(name);
		if (!other.isFile()) {
			return null;
		}

		space = data.indexOf(" ");
		assert space < 0;
		long timestamp = parseLongHex(data.toString());

		if (other.lastModified() != timestamp) {
			return null;
		}

		return other;
	}

	private static String decodeString(String s) {
		StringBuilder sb = new StringBuilder(s);

		for (int ii = 0; ii < sb.length(); ++ii) {
			Integer c = new Integer(sb.charAt(ii));

			if (c == ENCODE_SENTINEL) {
				char ch = 0;
				int jj = ii + 1;

				for (;; ++jj) {
					char schar = sb.charAt(jj);
					if (schar == ENCODE_TERMINATOR) {
						break;
					}

					ch <<= 4;
					ch += hexDigitValue(schar);
				}

				sb.setCharAt(ii, ch);
				sb.delete(ii + 1, jj + 1);
			}
		}

		return sb.toString();
	}

	private static char hexDigitValue(char ch) {
		ch = Character.toUpperCase(ch);
		int ii = hexDigits.indexOf(ch);

		return (char) ((ii >= 0) ? ii : 0);
	}

	private static int parseIntHex(String value) {
		return (int) parseLongHex(value);
	}

	private static long parseLongHex(String value) {
		value = value.toUpperCase();
		long accum = 0L;

		for (int ii = 0; ii < value.length(); ++ii) {
			char ch = value.charAt(ii);

			int digit = hexDigits.indexOf(ch);
			assert digit >= 0;

			accum *= 16;
			accum += digit;
		}

		return accum;
	}

	public static void setFolderPath(Path p) {
		dbfolder = p.toFile();
	}

	public static File getDbFolder() {
		return dbfolder;
	}
}
