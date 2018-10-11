package dup.model;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dup.analyze.Analyzer;
import dup.analyze.Checksum;
import dup.browser.FolderTreeModel;
import dup.browser.View;
import dup.util.FileUtil;
import dup.util.Trace;
import dup.util.Utility;

public class Database {
	public static boolean skipFileComparison = false;

	public static Database instance() {
		if (instance == null) {
			Database db = new Database();

			if (Persistence.getDbFolder() != null) {
				instance = db;
				instance.load();
			}
		}

		return instance;
	}

	private static Database instance = null;

	private List<Context> contexts = new ArrayList<Context>();

	private FolderTreeModel model = null;
	private View view = null;

	private Map<String, RegisteredDupDiffInfo> registeredDupDiffInfo;
	private boolean dirty = false;

	private Database() {
		this.registeredDupDiffInfo = new HashMap<String, RegisteredDupDiffInfo>();

		FileUtil.setupDBFolder();

		// this.contextMonitor = new ContextMonitor(this);
	}

	// Trivial getters/setters
	public List<Context> getContexts() {
		return this.contexts;
	}

	public FolderTreeModel getModel() {
		return this.model;
	}

	public void setModel(FolderTreeModel model) {
		this.model = model;
	}

	public void setView(View view) {
		this.view = view;
	}

	// =========================================================
	// System management
	// =========================================================

	// Housekeeping to do when exiting
	// Load/save dirty contexts before closing
	public void shutDown() {
		// TODO shut down loading thread
		for (Context context : this.contexts) {
			// if (context.isDirty()) {
			// FileUtil.ingestFileDetails(context, DetailLevel.Full);
			//
			// Persistence.save(context, this.dbfolder);
			// }

			context.close();
		}

		instance = null;
	}

	private void load() {
		if (Persistence.getDbFolder() == null) {
			return;
		}

		Persistence.loadDatabase();

		// for (File f : Persistence.getDbFolder().listFiles()) {
		// if (f.isFile() && f.canRead() && f.getName().endsWith(".db")) {
		// Context context = ContextLoader.loadContextFromFile(f);
		//
		// // TODO defer until necessary (or user command)
		// // openContext(context);
		//
		// this.contexts.add(context);
		// }
		// }

		analyzeDuplicates();
	}

	// =========================================================
	// Context management
	// =========================================================

	public void closeContext(Context context) {
		if (this.contexts.contains(context)) {
			// saveContext(context);
			this.contexts.remove(context);
			analyzeDuplicates();
		}
	}

	public void saveContext(Context context) {
		if (this.contexts.contains(context)) {
			if (context.isDirty()) {
				Persistence.save(context);
			}
		}
	}

	/**
	 * Set up a context. If the folder is already open, return the existing context.
	 * Otherwise, we create a new context. The context name is qualified if
	 * necessary to make it unique.
	 */
	public Context openContext(String folderPath, String contextName) {
		Checksum.checksumCount = 0;
		FileUtil.compareCount = 0;

		// TODO check context overlap (one context is a subtree of another
		// in the filesystem)
		Context context = findLoadedContext(folderPath);
		if (context != null) {
			return context;
		}

		reportMemory("Before loading context " + contextName);

		long start = System.currentTimeMillis();
		Trace.traceln(Trace.NORMAL, "Loading context " + contextName);

		File savedContext = Persistence.findSavedContext(new File(folderPath));
		if (savedContext != null) {
			context = ContextLoader.loadContextFromFile(savedContext);
		} else {
			contextName = getUniqueContextName(contextName);

			context = ingestNewContext(folderPath, contextName);
		}

		this.contexts.add(context);

		context.analyzeContextDuplicates();

		Trace.traceln(Trace.NORMAL);
		Trace.traceln(Trace.NORMAL, "Analyzing global duplicates...");
		Analyzer.analyzeGlobalDuplicates(this.contexts);

		Trace.traceln(Trace.NORMAL, "Checksum calculations: " + Checksum.checksumCount);
		Trace.traceln(Trace.NORMAL, "File comparisons: " + FileUtil.compareCount);

		long diff = (System.currentTimeMillis() - start + 500) / 1000;
		Trace.traceln(Trace.NORMAL, "Elapsed time for open = " + diff + " seconds");

		// TODO when to save the registered dup/diff info?
		// Persistence.saveDatabase();

		reportMemory("After loading context " + contextName);

		return context;
	}

	public static void reportMemory(String when) {
		Runtime rt = Runtime.getRuntime();
		long totmem = rt.totalMemory();
		long freemem = rt.freeMemory();

		Trace.traceln(Trace.NORMAL, "Memory usage " + when //
				+ ": " + Utility.formatSize(freemem) + " free of " + Utility.formatSize(totmem));
	}

	/** Find an existing context with a particular root folder */
	public Context getContextForRoot(FolderInfo root) {
		for (Context context : this.contexts) {
			if (context.getRoot() == root) {
				return context;
			}
		}

		return null;
	}

	/** Find an existing context pointing at a given folder on disk */
	public Context getLikeContext(FileObjectInfo root) {
		// TODO UNUSED
		// And this seems to hinge on having absolute paths to the folders.
		// But this is not the case.
		File rootfile = root.getRelativeJavaFile();
		if (rootfile == null) {
			return null;
		}

		for (Context context : this.contexts) {
			File contextfile = context.getRoot().getRelativeJavaFile();

			if (rootfile.equals(contextfile)) {
				return context;
			}
		}

		return null;
	}

	public Context getContextForFile(FileInfo file) {
		FileInfo origfile = findFile(file);

		return (origfile != null) ? origfile.getContext() : null;
	}

	/** Find an existing context with a given path to its root folder */
	private Context findLoadedContext(String folderPath) {
		File rootfile = new File(folderPath);

		for (Context context : this.contexts) {
			if (context.getRootFile().equals(rootfile)) {
				return context;
			}
		}

		return null;
	}

	private Context ingestNewContext(String folderPath, String contextName) {
		if (!new File(folderPath).isDirectory()) {
			System.err.println("Error! Context folder '" + folderPath + "' does not exist or is not a directory!");
			return null;
		}

		Context context = new Context(folderPath, contextName);

		context.setVersion(Persistence.VERSION);

		FileUtil.ingestContext(context);
		context.setDirty();

		return context;
	}

	private String getUniqueContextName(String name) {
		String basename = name;

		int index = name.lastIndexOf('[');
		if (index > 0) {
			basename = name.substring(0, index);
		}

		boolean collision;
		int qualifier = 0;
		String retname = basename;

		do {
			collision = false;

			for (Context context : this.contexts) {
				if (context.getName().equals(retname)) {
					collision = true;
					break;
				}
			}

			if (collision) {
				++qualifier;
				retname = basename + "[" + qualifier + "]";
			}
		} while (collision);

		return retname;
	}

	// =========================================================
	// File management
	// =========================================================

	public FileInfo findFile(FileInfo file) {
		for (Context context : this.contexts) {
			FileInfo origfile = context.findFile(file);

			if (origfile != null) {
				return origfile;
			}
		}

		return null;
	}

	// =========================================================
	// Duplicate analysis
	// =========================================================

	public void analyzeDuplicates() {
		analyzeDuplicates(this.contexts);
	}

	public void analyzeDuplicates(List<Context> contexts) {
		if (this.model == null) {
			// Too early during initialization to do this properly
			return;
		}

		// for (Context context : contexts) {
		// context.restartAnalysis();
		// }

		for (Context context : contexts) {
			context.analyzeContextDuplicates();
		}

		Analyzer.analyzeGlobalDuplicates(contexts);
	}

	void rebuildBrowseTree() {
		this.view.rebuildBrowseTree();
	}

	public void registerDuplicateFile(FileInfo fileinfo1, FileInfo fileinfo2) {
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

	public void registerDupDiffInfo(RegisteredDupDiffInfo info) {
		this.registeredDupDiffInfo.put(info.key.getAbsolutePath(), info);
	}

	public void registerDifferentFile(FileInfo fileinfo1, FileInfo fileinfo2) {
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

	public boolean isRegisteredDuplicateFile(FileInfo file, FileInfo other) {
		RegisteredDupDiffInfo info = getRegisteredDupDiffInfo(file);

		return (info != null) && info.isDuplicate(other);
	}

	public boolean isRegisteredDifferentFile(FileInfo file, FileInfo other) {
		RegisteredDupDiffInfo info = getRegisteredDupDiffInfo(file);

		return (info != null) && info.isDifferent(other);
	}

	private RegisteredDupDiffInfo findOrCreateDupDiffInfo(FileInfo file) {
		File jfile = file.getJavaFile();

		RegisteredDupDiffInfo info = this.registeredDupDiffInfo.get(jfile.getAbsolutePath());

		if (info == null) {
			info = new RegisteredDupDiffInfo(file);
			this.registeredDupDiffInfo.put(jfile.getAbsolutePath(), info);
		} else {
			info.syncFileChecksums(file);
		}

		return info;
	}

	private static Collection<DupDiffFileInfo> NoFiles = new ArrayList<DupDiffFileInfo>();

	public Collection<DupDiffFileInfo> getRegisteredDuplicates(File file) {
		RegisteredDupDiffInfo info = this.registeredDupDiffInfo.get(file.getAbsolutePath());

		return (info != null) ? info.duplicateFiles : NoFiles;
	}

	public Collection<DupDiffFileInfo> getRegisteredDifferentFiles(File file) {
		RegisteredDupDiffInfo info = this.registeredDupDiffInfo.get(file.getAbsolutePath());

		return (info != null) ? info.differentFiles : NoFiles;
	}

	public boolean isDirty() {
		return this.dirty;
	}

	public void setDirty(boolean yesno) {
		this.dirty = yesno;
	}

	public Map<String, RegisteredDupDiffInfo> getRegisteredDupDiffInfo() {
		return this.registeredDupDiffInfo;
	}

	public RegisteredDupDiffInfo getRegisteredDupDiffInfo(FileInfo file) {
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

	private RegisteredDupDiffInfo getRegisteredDupDiffInfo(File file) {
		return getRegisteredDupDiffInfo(file.getAbsolutePath());
	}

	public RegisteredDupDiffInfo getRegisteredDupDiffInfo(String filename) {
		return this.registeredDupDiffInfo.get(filename);
	}

	public static class DupDiffFileInfo {
		public String filename;
		// public long size;
		public long timestamp;

		public DupDiffFileInfo(String filename, long timestamp) {
			this.filename = filename;
			// this.size = ;
			this.timestamp = timestamp;
		}

		public DupDiffFileInfo(File file) {
			this.filename = file.getAbsolutePath();
			// this.size = file.length();
			this.timestamp = file.lastModified();
		}

		public boolean matches(FileInfo file) {
			File jfile = file.getJavaFile();

			if (!this.filename.equals(jfile.getAbsolutePath())) {
				return false;
			}

			return this.timestamp == jfile.lastModified();
		}

		public int hashCode() {
			return this.filename.hashCode();
		}

		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}

			return (o instanceof DupDiffFileInfo) ? this.filename.equals(((DupDiffFileInfo) o).filename)
					: super.equals(o);
		}
	}

	public static class RegisteredDupDiffInfo {
		File key;
		long filesize;
		long timestamp;
		int psum;
		int ssum;

		Collection<DupDiffFileInfo> duplicateFiles;
		Collection<DupDiffFileInfo> differentFiles;

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

		private void syncFileChecksums(FileInfo file) {
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

	// private ContextMonitor contextMonitor;

	// =========================================================
	// Reconciling with filesystem
	// =========================================================

	// Open an existing, persisted context
	// public void openContext(Context context)
	// {
	// Trace.traceln("Updating context " + context.getName());
	// reconcileContextWithStorage(context);
	//
	// if (context.isDirty() ||
	// context.getDetailLevel().isLessThan(DetailLevel.Full)) {
	// Trace.traceln("Completing the load of updated context " +
	// context.getName());
	// FileUtil.ingestFileDetails(context, DetailLevel.Full);
	//
	// Trace.traceln("Saving updated context " + context.getName());
	// Persistence.save(context, this.dbfolder);
	// }
	// }

	// Compare a context with the actual disk contents and update the context
	// accordingly to make it consistent.
	// private static void reconcileContextWithStorage(Context context)
	// {
	// Context refContext = new Context(context.getRootFile(), context.getName()
	// + "-ref");
	// FileUtil.ingestContext(refContext, DetailLevel.Size);
	//
	// Utility.compareContexts(context, refContext);
	// refContext.close();
	// }

	// =========================================================
	// Background loading
	// =========================================================

	// boolean finishLoadingDuplicateFiles(BatchDetailsUpdate bdu)
	// {
	// bdu.close();
	//
	// boolean found = false;
	//
	// for (Context context : this.contexts) {
	// if (context.getRoot().getTreeDupCount() == 0) {
	// continue;
	// }
	//
	// Trace.traceln("Loading duplicate for context " + context.getName());
	//
	// bdu.setContext(context);
	//
	// Iterator<FileInfo> iter = context.getRoot().iterateFiles(true);
	// while (iter.hasNext()) {
	// FileInfo file = iter.next();
	//
	// if (!file.isUnique() //
	// || (file.getDetailLevel() == DetailLevel.Full)) {
	// continue;
	// }
	//
	// if (bdu.addFile(file)) {
	// found = true;
	//
	// if (bdu.size() >= 100) {
	// break;
	// }
	// }
	// }
	//
	// if (bdu.size() > 0) {
	// bdu.close();
	// break;
	// }
	// }
	//
	// return found;
	// }

	// boolean finishLoadingAllFiles(BatchDetailsUpdate bdu)
	// {
	// bdu.close();
	//
	// boolean found = false;
	//
	// for (Context context : this.contexts) {
	// // TODO synchronize access
	// if (context.getRoot().getTreeFileCount() == 0) {
	// continue;
	// }
	//
	// Trace.traceln("Loading files for context " + context.getName());
	//
	// bdu.setContext(context);
	//
	// Iterator<FileInfo> iter = context.getRoot().iterateFiles(true);
	// while (iter.hasNext()) {
	// FileInfo file = iter.next();
	//
	// if (bdu.addFile(file)) {
	// found = true;
	//
	// if (bdu.size() >= 100) {
	// break;
	// }
	// }
	// }
	//
	// if (bdu.size() > 0) {
	// bdu.close();
	// break;
	// }
	// }
	//
	// return found;
	// }
}

// Class to do background loading
// class ContextMonitor extends Thread
// {
// private Database db;
//
// public ContextMonitor(Database db)
// {
// this.db = db;
//
// int pri = getPriority();
//
// if (pri > Thread.MIN_PRIORITY) {
// setPriority(pri - 1);
// }
//
// // start();
// }
//
// public void run()
// {
// Trace.traceln("ContextMonitor starting");
//
// BatchDetailsUpdate bdu = new BatchDetailsUpdate();
//
// for (;;) {
// boolean b = false;
//
// if (this.db.finishLoadingDuplicateFiles(bdu)) {
// b = true;
// }
// else {
// b = this.db.finishLoadingAllFiles(bdu);
// }
//
// if (b) {
// this.db.rebuildBrowseTree();
// }
//
// if (!b) {
// try {
// Trace.traceln("ContextMonitor sleeping");
// sleep(10000);
// }
// catch (InterruptedException e) {
// }
// }
// }
// }
// }
