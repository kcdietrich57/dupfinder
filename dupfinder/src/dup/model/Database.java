package dup.model;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import dup.analyze.Analyzer;
import dup.analyze.Checksum;
import dup.analyze.DetailLevel;
import dup.analyze.DupDiffFileInfo;
import dup.analyze.DuplicateInfo2;
import dup.model.persist.ContextLoader;
import dup.model.persist.Persistence;
import dup.util.FileUtil;
import dup.util.Trace;
import dup.util.Utility;

public class Database {
	public static Collection<DupDiffFileInfo> NoFiles = new ArrayList<DupDiffFileInfo>();
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
	private List<FileInfo> files = new ArrayList<FileInfo>();
	public final List<DuplicateInfo2> duplicates = new ArrayList<DuplicateInfo2>();

	/** TODO Flags whether the file model is initialized yet */
	private boolean modelAvailable = false;

	private Database() {
		FileUtil.setupDBFolder();

		// this.contextMonitor = new ContextMonitor(this);
	}

	// Trivial getters/setters
	public List<Context> getContexts() {
		return this.contexts;
	}

	public void setModelAvailable(boolean yesno) {
		this.modelAvailable = yesno;
	}

	// =========================================================
	// System management
	// =========================================================

	/**
	 * Housekeeping to do when exiting - Load/save dirty contexts before closing
	 */
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

	/** Load saved contexts */
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

	/** Close a context */
	public void closeContext(Context context) {
		if (this.contexts.contains(context)) {
			// TODO autosave - saveContext(context);
			this.contexts.remove(context);
			analyzeDuplicates();
		}
	}

	/** Persist duplicate information for a context */
	public void saveContext(Context context) {
		if (this.contexts.contains(context)) {
			if (context.isDirty()) {
				Persistence.save(context);
			}
		}
	}

	/** Add a file (while ingesting a context) */
	public void addFile(FileInfo file) {
		int idx = FileUtil.addFile(this.files, file);

		boolean isdup = false;
		if ((idx > 0) && (this.files.get(idx - 1).getSize() == file.getSize())) {
			isdup = true;
		} else if ((idx + 1 < this.files.size()) //
				&& (this.files.get(idx + 1).getSize() == file.getSize())) {
			isdup = true;
		}

		if (isdup) {
			addFileToDuplicates(idx, file);
		}
	}

	public DuplicateInfo2 getDuplicateInfo(FileInfo file) {
		int idx = Collections.binarySearch(this.duplicates, //
				new DuplicateInfo2(file.getSize()), //
				new Comparator<DuplicateInfo2>() {
					public int compare(DuplicateInfo2 di1, DuplicateInfo2 di2) {
						long diff = di1.fileSize() - di2.fileSize();

						if (diff < 0) {
							return -1;
						} else if (diff == 0) {
							return 0;
						} else {
							return 1;
						}
					}
				});

		return (idx >= 0) ? this.duplicates.get(idx) : null;
	}

	/** Add file to existing duplicate info database */
	private void addFileToDuplicates(int idx, FileInfo file) {
		DuplicateInfo2 dupinfo = getDupinfo(idx, file.getSize());

		dupinfo.addFile(file);
	}

	/** Get or create duplicate info for a given file size */
	private DuplicateInfo2 getDupinfo(int fileidx, long size) {
		DuplicateInfo2 dupinfo = new DuplicateInfo2(size);

		int dupidx = Collections.binarySearch(this.duplicates, //
				dupinfo, //
				new Comparator<DuplicateInfo2>() {
					public int compare(DuplicateInfo2 d1, DuplicateInfo2 d2) {
						long diff = d1.fileSize() - d2.fileSize();
						if (diff < 0) {
							return -1;
						} else if (diff > 0) {
							return 1;
						} else {
							return 0;
						}
					}
				});

		if (dupidx >= 0) {
			dupinfo = this.duplicates.get(dupidx);
		} else {
			this.duplicates.add(-dupidx - 1, dupinfo);

			for (int idx = fileidx - 1; idx > 0; --idx) {
				FileInfo file = this.files.get(idx);
				if (file.getSize() != size) {
					break;
				}

				dupinfo.addFile(file);
			}

			for (int idx = fileidx + 1; idx < this.files.size(); ++idx) {
				FileInfo file = this.files.get(idx);
				if (file.getSize() != size) {
					break;
				}

				dupinfo.addFile(file);
			}
		}

		return dupinfo;
	}

	/**
	 * Set up a new context for a folder.<br>
	 * If the folder is already open, return the existing context. <br>
	 * Otherwise, we create a new context. <br>
	 * The context name is qualified if necessary to make it unique.
	 */
	public Context openContext(String folderPath, String contextName) {
		Checksum.checksumCount = 0;
		FileUtil.compareCount = 0;

		// TODO check for context overlap (one context is a subtree of another
		// in the filesystem)
		Context context = findLoadedContext(folderPath);
		if (context != null) {
			return context;
		}

		Utility.reportMemory("Before loading context " + contextName);

		long start = System.currentTimeMillis();
		Trace.traceln(Trace.NORMAL, "Loading context " + contextName);

		File savedContext = Persistence.findSavedContext(new File(folderPath));
		if (savedContext != null) {
			context = ContextLoader.loadContextFromFile(savedContext);
		} else {
			contextName = getUniqueContextName(contextName);
			context = ingestNewContext(folderPath, contextName);
		}

		if (context != null) {
			this.contexts.add(context);

			Trace.traceln(Trace.NORMAL, "Processing ingested files");
			processFiles(DetailLevel.Size);
			summarizeDuplicates();
			processFiles(DetailLevel.Prefix);
			summarizeDuplicates();
			processFiles(DetailLevel.Sample);
			summarizeDuplicates();

			// TODO the following analysis methods are defunct
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
		}

		Utility.reportMemory("After loading context " + contextName);

		return context;
	}

	private void summarizeDuplicates() {
		int numchains = 0;
		int numfiles = 0;
		int longchain = 0;

		for (DuplicateInfo2 dupinfo : this.duplicates) {
			for (List<FileInfo> dups : dupinfo.getDuplicateLists()) {
				++numchains;
				numfiles += dups.size();
				if (dups.size() > longchain) {
					longchain = dups.size();
				}
			}
		}

		Trace.traceln(Trace.NORMAL, String.format(//
				"%d files in %d chains (long %d)", numfiles, numchains, longchain));
	}

	/** Perform analysis on each group of same-size files */
	private void processFiles(DetailLevel level) {
		int nn = this.duplicates.size();

		Trace.traceln(Trace.NORMAL, String.format( //
				"Processing chains %d detail %s", //
				nn, level.toString()));

		for (int ii = 0; ii < nn; ++ii) {
			DuplicateInfo2 dupinfo = this.duplicates.get(ii);

			if ((nn < 100) || ((ii % (nn / 100)) == 0)) {
				Trace.trace(Trace.NORMAL, ".");
//				Trace.trace(Trace.NORMAL, String.format( //
//						"  %s", Utility.formatSize(dupinfo.fileSize())));
			} else {
				// Trace.trace(Trace.NORMAL, ".");
			}

			dupinfo.processFiles(level);
		}

		Trace.traceln(Trace.NORMAL);
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

	/** Locate a context containing a given file */
	public Context getContextForFile(FileInfo file) {
		FileInfo origfile = findFile(file);

		return (origfile != null) ? origfile.getContext() : null;
	}

	/** Find an existing context with a given root folder */
	private Context findLoadedContext(String folderPath) {
		File rootfile = new File(folderPath);

		for (Context context : this.contexts) {
			if (context.getRootFile().equals(rootfile)) {
				return context;
			}
		}

		return null;
	}

	/** Create and ingest a context given a folder */
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

	/** Create a context name */
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

	/** Search for a file in all contexts */
	public FileInfo findFile(FileInfo file) {
		for (Context context : this.contexts) {
			FileInfo origfile = context.findFile(file);

			if (origfile != null) {
				return origfile;
			}
		}

		return null;
	}

	/** TODO defunct - analyze duplicate info */
	public void analyzeDuplicates() {
		if (this.modelAvailable) {
			// Too early during initialization to do this properly
			return;
		}

		// for (Context context : contexts) {
		// context.restartAnalysis();
		// }

		for (Context context : this.contexts) {
			context.analyzeContextDuplicates();
		}

		Analyzer.analyzeGlobalDuplicates(this.contexts);
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
