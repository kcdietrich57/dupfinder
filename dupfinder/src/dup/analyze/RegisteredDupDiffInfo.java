package dup.analyze;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import dup.model.Database;
import dup.model.FileInfo;

public class RegisteredDupDiffInfo {
	private static Map<String, RegisteredDupDiffInfo> registeredDupDiffInfo = //
			new HashMap<String, RegisteredDupDiffInfo>();

	public static RegisteredDupDiffInfo findOrCreateDupDiffInfo(FileInfo file) {
		File jfile = file.getJavaFile();

		RegisteredDupDiffInfo info = registeredDupDiffInfo.get(jfile.getAbsolutePath());

		if (info == null) {
			info = new RegisteredDupDiffInfo(file);
			registeredDupDiffInfo.put(jfile.getAbsolutePath(), info);
		} else {
			info.syncFileChecksums(file);
		}

		return info;
	}

	public static Collection<DupDiffFileInfo> getRegisteredDuplicates(File file) {
		RegisteredDupDiffInfo info = registeredDupDiffInfo.get(file.getAbsolutePath());

		return (info != null) ? info.duplicateFiles : Database.NoFiles;
	}

	public static Collection<DupDiffFileInfo> getRegisteredDifferentFiles(File file) {
		RegisteredDupDiffInfo info = registeredDupDiffInfo.get(file.getAbsolutePath());

		return (info != null) ? info.differentFiles : Database.NoFiles;
	}

	public static Map<String, RegisteredDupDiffInfo> getRegisteredDupDiffInfo() {
		return registeredDupDiffInfo;
	}

	private static RegisteredDupDiffInfo getRegisteredDupDiffInfo(FileInfo file) {
		File jfile = file.getJavaFile();
		RegisteredDupDiffInfo info = getRegisteredDupDiffInfo(jfile);

		// TODO compare file attributes more carefully here
		if (info != null) {
			if ((jfile.length() == info.filesize) //
					&& (jfile.lastModified() == info.timestamp)) {
				file.setPrefixChecksum(info.psum);
				file.setSampleChecksum(info.ssum);
			}
		}

		return info;
	}

	private static RegisteredDupDiffInfo getRegisteredDupDiffInfo(File file) {
		return getRegisteredDupDiffInfo(file.getAbsolutePath());
	}

	public static RegisteredDupDiffInfo getRegisteredDupDiffInfo(String filename) {
		return registeredDupDiffInfo.get(filename);
	}

	public static void registerDuplicateFile(FileInfo fileinfo1, FileInfo fileinfo2) {
		assert (fileinfo1.getSize() == fileinfo2.getSize()) //
				&& (fileinfo1.getLastModified() == fileinfo2.getLastModified()) //
				&& (fileinfo1.getPrefixChecksum(false) == fileinfo2.getPrefixChecksum(false)) //
				&& (fileinfo1.getSampleChecksum(false) == fileinfo2.getSampleChecksum(false));

		RegisteredDupDiffInfo info1 = findOrCreateDupDiffInfo(fileinfo1);
		RegisteredDupDiffInfo info2 = findOrCreateDupDiffInfo(fileinfo2);

		info1.updateFrom(fileinfo1);
		info2.updateFrom(fileinfo2);

		assert (info1.filesize == fileinfo1.getSize()) //
				&& (info1.timestamp == fileinfo1.getLastModified()) //
				&& (info1.psum == fileinfo1.getPrefixChecksum(false)) //
				&& (info1.ssum == fileinfo1.getSampleChecksum(false));

		info1.addDuplicate(fileinfo2);
		info2.addDuplicate(fileinfo1);

		info1.addDuplicates(info2.duplicateFiles);
		info2.addDuplicates(info1.duplicateFiles);
	}

	public static void registerDupDiffInfo(RegisteredDupDiffInfo info) {
		registeredDupDiffInfo.put(info.key.getAbsolutePath(), info);
	}

	public static void registerDifferentFile(FileInfo fileinfo1, FileInfo fileinfo2) {
		assert (fileinfo1.getSize() == fileinfo2.getSize()) //
				&& (fileinfo1.getLastModified() == fileinfo2.getLastModified()) //
				&& (fileinfo1.getPrefixChecksum(false) == fileinfo2.getPrefixChecksum(false)) //
				&& (fileinfo1.getSampleChecksum(false) == fileinfo2.getSampleChecksum(false));

		RegisteredDupDiffInfo info1 = findOrCreateDupDiffInfo(fileinfo1);
		RegisteredDupDiffInfo info2 = findOrCreateDupDiffInfo(fileinfo2);

		assert (info1.filesize == fileinfo1.getSize()) //
				&& (info1.timestamp == fileinfo1.getLastModified()) //
				&& (info1.psum == fileinfo1.getPrefixChecksum(false)) //
				&& (info1.ssum == fileinfo1.getSampleChecksum(false));

		info1.addDifferent(fileinfo2);
		info2.addDifferent(fileinfo1);
	}

	public static boolean isRegisteredDuplicateFile(FileInfo file, FileInfo other) {
		RegisteredDupDiffInfo info = getRegisteredDupDiffInfo(file);

		return (info != null) && info.isDuplicate(other);
	}

	public static boolean isRegisteredDifferentFile(FileInfo file, FileInfo other) {
		RegisteredDupDiffInfo info = getRegisteredDupDiffInfo(file);

		return (info != null) && info.isDifferent(other);
	}

	public File key;
	public long filesize;
	public long timestamp;
	public int psum;
	public int ssum;

	public Collection<DupDiffFileInfo> duplicateFiles;
	public Collection<DupDiffFileInfo> differentFiles;

	public RegisteredDupDiffInfo(File key, long size, long timestamp, int psum, int ssum) {
		this.key = key;

		this.filesize = size;
		this.timestamp = timestamp;

		this.psum = psum;
		this.ssum = ssum;

		this.duplicateFiles = new ArrayList<DupDiffFileInfo>();
		this.differentFiles = new ArrayList<DupDiffFileInfo>();
	}

	public RegisteredDupDiffInfo(FileInfo file) {
		this(file.getJavaFile(), //
				file.getSize(), //
				file.getLastModified(), //
				file.getPrefixChecksum(false), //
				file.getSampleChecksum(false));
	}

	public void updateFrom(FileInfo file) {
		if (this.psum == 0) {
			this.psum = file.getPrefixChecksum(false);
		}

		if (this.ssum == 0) {
			this.ssum = file.getSampleChecksum(false);
		}
	}

	public boolean isDuplicate(FileInfo file) {
		if (!matchesFile(file)) {
			return false;
		}

		for (DupDiffFileInfo info : this.duplicateFiles) {
			if (info.matches(file)) {
				syncFileChecksums(file);

				return true;
			}
		}

		return false;
	}

	private boolean matchesFile(FileInfo file) {
		int psum = file.getPrefixChecksum(false);
		int ssum = file.getSampleChecksum(false);

		return ((psum == 0) || (this.psum == psum)) //
				&& ((ssum == 0) || (this.ssum == ssum));
	}

	public void syncFileChecksums(FileInfo file) {
		file.setPrefixChecksum(this.psum);
		file.setSampleChecksum(this.ssum);
	}

	public boolean isDifferent(FileInfo file) {
		for (DupDiffFileInfo info : this.differentFiles) {
			if (info.matches(file)) {
				return true;
			}
		}

		return false;
	}

	public void addDifferent(FileInfo file) {
		addDifferent(new DupDiffFileInfo(file.getJavaFile()));
	}

	public void addDifferent(DupDiffFileInfo file) {
		if (!this.differentFiles.contains(file)) {
			this.differentFiles.add(file);
		}
	}

	public void addDuplicate(FileInfo file) {
		addDuplicate(new DupDiffFileInfo(file.getJavaFile()));
	}

	public void addDuplicate(DupDiffFileInfo file) {
		if (!this.duplicateFiles.contains(file)) {
			this.duplicateFiles.add(file);
		}
	}

	public void addDuplicates(Collection<DupDiffFileInfo> files) {
		for (DupDiffFileInfo file : files) {
			addDuplicate(file);
		}
	}
}