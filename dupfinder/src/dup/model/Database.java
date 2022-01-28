package dup.model;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import dup.analyze.Checksum;
import dup.analyze.DetailLevel;
import dup.analyze.DupDiffFileInfo;
import dup.model.persist.ContextLoader;
import dup.model.persist.Persistence;
import dup.util.FileUtil;
import dup.util.Trace;
import dup.util.Utility;

public class Database {
	public static Collection<DupDiffFileInfo> NoFiles = new ArrayList<DupDiffFileInfo>();
	public static boolean skipFileComparison = false;

	public static void clear() {
		instance = null;
	}

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

	/** All files, sorted by size, duplicates grouped together */
	private List<FileInfo> files = new ArrayList<FileInfo>();

	/** Groups of duplicate (as far as we know) files, ordered by file size */
	private List<List<FileInfo>> groups = new ArrayList<List<FileInfo>>();

	private Database() {
		FileUtil.setupDBFolder();

		// this.contextMonitor = new ContextMonitor(this);
	}

	// Trivial getters/setters
	public List<Context> getContexts() {
		return this.contexts;
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

		figureDuplicates();
	}

	/** Close a context */
	public void closeContext(Context context) {
		if (this.contexts.contains(context)) {
			// TODO autosave - saveContext(context);
			this.contexts.remove(context);
			// analyzeDuplicates();
			figureDuplicates();
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

	/** Add a file (while loading/ingesting a context) */
	public void addFile(FileInfo file) {
		FileUtil.addFile(this.files, file);
	}

	public void removeFile(FileInfo file) {
		List<FileInfo> group = getAllDuplicates(file);

		if (group != null) {
			clearDuplicateInfo(group);
			
			if (group.size() < 3) {
				this.groups.remove(group);
			}

			group.remove(file);
			setDuplicateFlags(group);
		}

		this.files.remove(file);
	}

	// TODO Separate tasks when loading contexts
	// 1 Add files to context (ingest vs load)
	// 2 Add files to database
	// 3 Build dup chains
	// 4 Compare files and update dup chains
	// 5 Update UI

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

		Database db = Database.instance();

		File savedContext = Persistence.findSavedContext(new File(folderPath));
		if (savedContext != null) {
			context = ContextLoader.loadContextFromFile(savedContext);
		} else {
			contextName = getUniqueContextName(contextName);
			context = ingestNewContext(folderPath, contextName);
		}

		if (context != null) {
			int idx = 0;
			for (; idx < this.contexts.size(); ++idx) {
				Context ctx = this.contexts.get(idx);
				if (ctx.getName().compareToIgnoreCase(context.getName()) >= 0) {
					break;
				}
			}

			this.contexts.add(idx, context);

			db.figureDuplicates();

//			Trace.traceln(Trace.NORMAL, "Processing ingested files");
//			processFiles(DetailLevel.Size);
//			summarizeDuplicates();
//			processFiles(DetailLevel.Prefix);
//			summarizeDuplicates();
//			processFiles(DetailLevel.Sample);
//			summarizeDuplicates();

			// TODO the following analysis methods are defunct

			// Trace.traceln(Trace.NORMAL);
			// Trace.traceln(Trace.NORMAL, "Analyzing global duplicates...");
			// Analyzer.analyzeGlobalDuplicates(this.contexts);

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

	private int getDupCount() {
		int numdups = 0;

		for (List<FileInfo> group : this.groups) {
			numdups += group.size();
		}

		return numdups;
	}

	private void summarizeDuplicates() {
		int longchain = 0;

		for (List<FileInfo> group : this.groups) {
			if (group.size() > longchain) {
				longchain = group.size();
			}
		}

		Trace.traceln(Trace.NORMAL, String.format(//
				"%d files in %d chains (long %d)", //
				getDupCount(), this.groups.size(), longchain));
	}

//	/** Perform analysis on each group of same-size files */
//	private void processFiles(DetailLevel level) {
//	}

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
//		if (this.modelAvailable) {
//			// Too early during initialization to do this properly
//			return;
//		}
//
//		// for (Context context : contexts) {
//		// context.restartAnalysis();
//		// }
//
//		for (Context context : this.contexts) {
//			context.analyzeContextDuplicates();
//		}
//
//		Analyzer.analyzeGlobalDuplicates(this.contexts);
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

	private void clearDuplicateInfo(List<FileInfo> files) {
		for (FileInfo f : files) {
			f.clearDuplicateInfo();
		}
	}

	private List<FileInfo> makeGroup( //
			List<FileInfo> files, int start, int end) {
		List<FileInfo> newgroup = new ArrayList<>(files.subList(start, end));
		clearDuplicateInfo(newgroup);

		for (int n = start; n < end; ++n) {
			FileInfo f1 = files.get(n);

			for (int i = n + 1; i < end && f1.unique != FileInfo.BDUP; ++i) {
				FileInfo f2 = files.get(i);

				int u = (f1.contextid == f2.contextid) //
						? FileInfo.LDUP //
						: FileInfo.GDUP;

				f1.unique |= u;
				f2.unique |= u;
			}
		}

		return newgroup;
	}

	public void figureDuplicates() {
		System.out.println("Processing files........");

		int ops = normalizeDetailLevel();

//		// List of groups containing files that are dups of each other
//		this.groups.clear();
//		this.groups.add(this.files);

		partitionGroups();

		System.out.println("File processing complete.......");
		System.out.println(String.format("Worked on %d files", ops));

		int uniqueCount = 0;
		int gdupCount = 0;
		int ldupCount = 0;
		int bdupCount = 0;

		for (FileInfo f : this.files) {
			switch (f.unique) {
			case FileInfo.UNIQUE:
				++uniqueCount;
				break;
			case FileInfo.GDUP:
				++gdupCount;
				break;
			case FileInfo.LDUP:
				++ldupCount;
				break;
			case FileInfo.BDUP:
			default:
				++bdupCount;
				break;
			}
		}

		System.out.println(String.format("Total files: %d", this.files.size()));
		System.out.println(String.format( //
				"Unique files: %d gdup: %d ldup: %d bdup: %d", //
				uniqueCount, gdupCount, ldupCount, bdupCount));
		System.out.println(String.format("Duplicate groups: %d", this.groups.size()));

		long groupsSize = 0;
		int groupsFileCount = 0;

		for (List<FileInfo> group : this.groups) {
			System.out.println("========");
			for (FileInfo f : group) {
				System.out.println(String.format("  %3d: %d %s", //
						f.contextid, f.unique, f.getFullName()));
			}

			groupsFileCount += group.size();
			groupsSize += group.size() * group.get(0).getSize();
		}

		System.out.println(String.format("Duplicate files: %d", groupsFileCount));
		System.out.println(String.format("Total size: %s", Utility.formatSize(groupsSize)));

		System.out.println("======");
	}

	/** Check whether all files are duplicates by each other AFAIK. */
	private boolean allDuplicates(List<FileInfo> files) {
		if (files.size() < 2) {
			return true;
		}

		FileInfo firstfile = files.get(0);

		for (FileInfo f : files) {
			if (f.getSize() != firstfile.getSize() //
					|| !f.mayBeDuplicateOf(firstfile)) {
				return false;
			}
		}

		return true;
	}

	private boolean hasPotentialDuplicates(List<FileInfo> group) {
		for (int ii = 0; ii < group.size() - 1; ++ii) {
			FileInfo file = group.get(ii);
			if (file.isIgnoredFile()) {
				continue;
			}

			for (int jj = ii + 1; jj < group.size(); ++jj) {
				FileInfo other = group.get(jj);

				if (file.mayBeDuplicateOf(other) //
						&& (file.getDetailLevel().isLessThan(DetailLevel.MAX) //
								|| other.getDetailLevel().isLessThan(DetailLevel.MAX))) {
					return true;
				}
			}
		}

		return false;
	}

	long[] getWorkingSize(List<FileInfo> group) {
		long[] ret = new long[2];

		for (FileInfo file : group) {
			if (file.getDetailLevel().isLessThan(DetailLevel.MAX)) {
				++ret[0];
				ret[1] += file.filesize;
			}
		}

		return ret;
	}

	/**
	 * Re-analyze groups from scratch. This is necessary, for instance, when new
	 * files are introduced that may be dups of files in different groups. The
	 * easiest way to do this is to reset the subgroups and consider all same-sized
	 * files, calculating checksums as necessary to partition into duplicate groups.
	 * 
	 * @return Count of checksum calculations that were performed
	 */
	private int normalizeDetailLevel() {
		int didwork = 0;

		System.out.println("Normalizing all duplicate info...");
		if (this.files.isEmpty()) {
			return 0;
		}

		// Clear all group info
		clearDuplicateInfo(this.files);
		this.groups.clear();

		System.out.println("Building same-size chains...");

		int numfiles = 0;
		long totsize = 0;

		// Gather all same-size files into groups
		for (int start = 0; start < this.files.size();) {
			FileInfo ffile = this.files.get(start);
			int end = start + 1;

			while (end < this.files.size() && this.files.get(end).filesize == ffile.filesize) {
				++end;
			}

			if (end - start > 1) {
				// System.out.println("Found same-size files");
				List<FileInfo> newgroup = new ArrayList<>(this.files.subList(start, end));
				this.groups.add(newgroup);

				numfiles += newgroup.size();
				totsize += newgroup.size() * ffile.filesize;
			}

			start = end;
		}

		System.out.println(String.format( //
				"Found %d files in %d chains, total size %s", //
				numfiles, this.groups.size(), totsize));
		if (this.groups.isEmpty()) {
			return 0;
		}

		List<List<FileInfo>> workingGroups = new ArrayList<List<FileInfo>>();

		System.out.println("Eliminating known duplicate/unique files...");
		numfiles = 0;
		totsize = 0;
		int numfilesWorking = 0;
		long totsizeWorking = 0;

		for (List<FileInfo> group : this.groups) {
			if (hasPotentialDuplicates(group)) {
				workingGroups.add(group);

				numfiles += group.size();
				totsize += group.size() * group.get(0).filesize;

				long[] work = getWorkingSize(group);
				numfilesWorking += (int) work[0];
				totsizeWorking += work[1];
			}
		}

		System.out.println(String.format( //
				"Normalizing %d files in %d chains, total size %s ...", //
				numfiles, workingGroups.size(), totsize));
		System.out.println(String.format( //
				"Working files: %d files total size: %d ...", //
				numfilesWorking, totsizeWorking));

		// Normalize the checksums for each group
		for (int grpnum = 0; grpnum < workingGroups.size(); ++grpnum) {
			List<FileInfo> group = workingGroups.get(grpnum);

			long[] work = getWorkingSize(group);
			if (group.size() > 150) {
				System.out.println("Group ct=" + group.size() + " sz=" + group.get(0).filesize);
				System.out.println("xyzzy");
			}

			System.out.println(String.format( //
					"Group %d/%d [%s] Working files: %d/%d size %d\n  Total remaining files: %d [%s]", //
					grpnum + 1, workingGroups.size(), //
					Utility.formatSize(group.get(0).filesize), //
					work[0], group.size(), work[1], //
					numfilesWorking, //
					Utility.formatSize(totsizeWorking)));
			numfilesWorking -= (int) work[0];
			totsizeWorking -= work[1];

			// For any pair of files that may be duplicates, calculate checksums
			// as needed to determine whether they are indeed duplicates
			for (int ii = 0; ii < group.size() - 1; ++ii) {
				FileInfo file = group.get(ii);

				for (int jj = ii + 1; jj < group.size(); ++jj) {
					FileInfo other = group.get(jj);

//					System.out.println(String.format( //
//							"Comparing files\n  %s\n  %s", //
//							file.toString(), other.toString()));

					while (file.mayBeDuplicateOf(other) //
							&& (file.getDetailLevel().isLessThan(DetailLevel.MAX) //
									|| other.getDetailLevel().isLessThan(DetailLevel.MAX))) {
						if (improveChecksums( //
								(file.getDetailLevel().isLessThan(other.getDetailLevel())) //
										? file
										: other)) {
							++didwork;
						}
					}
				}
			}
		}

		return didwork;
	}

//	private DetailLevel minDetailLevel(List<FileInfo> group) {
//		DetailLevel minlevel = DetailLevel.MAX;
//
//		for (FileInfo file : group) {
//			if (minlevel.intval > file.getDetailLevel().intval) {
//				minlevel = file.getDetailLevel();
//			}
//		}
//
//		return minlevel;
//	}

	private boolean improveChecksums(FileInfo file) {
		if (file.getDetailLevel() == DetailLevel.MAX) {
			return false;
		}

		DetailLevel cur = file.getDetailLevel();
		DetailLevel next = cur.nextLevel();
//		System.out.println(String.format( //
//				"Improving detail (%s-%s) for '%s'", //
//				cur.name, next.name, file.getFullName()));
		file.calcChecksums(file.getContext(), next);

		return true;
	}

//	private boolean improveChecksums(List<FileInfo> group) {
//		DetailLevel minlevel = minDetailLevel(group);
//		if (minlevel == DetailLevel.MAX) {
//			return false;
//		}
//
//		DetailLevel nextLevel = minlevel.nextLevel();
//		for (FileInfo file : group) {
//			file.calcChecksums(file.getContext(), nextLevel);
//		}
//
//		return true;
//	}

//	private void resetDuplicateFlags(List<FileInfo> group) {
//		clearDuplicateInfo(group);
//		setDuplicateFlags(group);
//	}

	private void setDuplicateFlags(List<FileInfo> group) {
		for (int n = 0; n < group.size(); ++n) {
			FileInfo f1 = group.get(n);

			for (int i = n + 1; i < group.size() && f1.unique != FileInfo.BDUP; ++i) {
				FileInfo f2 = group.get(i);

				int u = (f1.contextid == f2.contextid) //
						? FileInfo.LDUP //
						: FileInfo.GDUP;

				f1.unique |= u;
				f2.unique |= u;
			}
		}
	}

	private int addGroup(List<FileInfo> group) {
		int idx = findGroup(group.get(0).filesize);

		if (idx < 0) {
			idx = -idx - 1;
		}

		if (idx < this.groups.size()) {
			this.groups.add(idx, group);
		} else {
			this.groups.add(group);
		}

		return idx;
	}

	/**
	 * Partition file list into groups of duplicate files, toss unique files<br>
	 * The file list must be normalized - i.e. we have calculated all necessary
	 * checksums.
	 */
	private void partitionGroups() {
		for (List<FileInfo> group : new ArrayList<List<FileInfo>>(this.groups)) {
			partitionGroup(group);
		}
	}

	private void partitionGroup(List<FileInfo> group) {
		if (hasPotentialDuplicates(group)) {
			System.out.println("ERROR: Normalize before partitioning!");
			hasPotentialDuplicates(group);
			System.exit(1);
		}

		if (group.size() < 2) {
			this.groups.remove(group);
			return;
		}

		if (allDuplicates(group)) {
			// All duplicates - no partitioning required
			setDuplicateFlags(group);
			return;
		}

		// Partition the group
		this.groups.remove(group);
		clearDuplicateInfo(group);

		int groupstart = 0;
		int groupend = groupstart + 1;

		// Sort the group to position duplicates together
		while (groupstart < group.size()) {
			FileInfo firstfile = group.get(groupstart);

			for (int n = groupstart + 1; n < group.size(); ++n) {
				FileInfo nextfile = group.get(n);
				if (firstfile.getSize() < nextfile.getSize()) {
					// Group is already sorted by size
					break;
				}

				if (firstfile.mayBeDuplicateOf(nextfile)) {
					if (n > groupend) {
						// Relocate the duplicate to the end of the subgroup
						group.set(n, group.get(groupend));
						group.set(groupend, nextfile);
					}

					++groupend;
				}
			}

			if (groupend == groupstart + 1) {
				// Single file is (still) unique, no group needed
			} else {
				List<FileInfo> newgroup = makeGroup(group, groupstart, groupend);

				if (!allDuplicates(newgroup)) {
					System.out.println("Subgroup is not normalized!");
					System.exit(1);
				}

				addGroup(newgroup);
			}

			groupstart = groupend;
			++groupend;
		}
	}

	private long groupFileSize(int grpidx) {
		return this.groups.get(grpidx).get(0).filesize;
	}

	public int findGroup(long filesize) {
		if (this.groups.isEmpty()) {
			return -1;
		}

		int first = 0;
		int last = this.groups.size() - 1;

		long fsize = groupFileSize(first);
		if (fsize == filesize) {
			return first;
		}
		if (fsize > filesize) {
			return -1;
		}

		while (last > 0 && groupFileSize(last - 1) == filesize) {
			--last;
		}

		fsize = groupFileSize(last);
		if (fsize == filesize) {
			return last;
		}
		if (fsize < filesize) {
			return -(last + 1);
		}

		int lastprobe = -1;

		for (;;) {
			int probe = (last + first + 1) / 2;
			if (probe == lastprobe) {
				return (groupFileSize(probe) < filesize) //
						? probe + 1 //
						: probe;
			}
			lastprobe = probe;

			if (groupFileSize(probe) == filesize) {
				while (probe > first && groupFileSize(probe - 1) == filesize) {
					--probe;
				}

				return probe;
			}

			if (groupFileSize(probe) < filesize) {
				first = probe;
			} else {
				last = probe;
			}
		}
	}

	public List<FileInfo> getAllDuplicates(FileInfo file) {
		if (file.isUnique()) {
			return null;
		}

		int groupidx = findGroup(file.filesize);
		if (groupidx < 0) {
			return null;
		}

		for (;; ++groupidx) {
			List<FileInfo> group = this.groups.get(groupidx);
			if (group.get(0).filesize > file.filesize) {
				return null;
			}

			if (group.contains(file)) {
				return group;
			}
		}

//		List<FileInfo> ret = null;
//
//		for (List<FileInfo> group : this.groups) {
//			if (group.contains(file)) {
//				if (ret != null) {
//					System.out.println("xyzzy - file belongs to multiple groups");
//				}
//				ret = group;
//			}
//		}
//
//		return ret;
	}

	private boolean groupIncludesContext(Context ctx, List<FileInfo> group) {
		for (FileInfo f : group) {
			if (f.contextid == ctx.id) {
				return true;
			}
		}

		return false;
	}

	public List<List<FileInfo>> getGroups(Context ctx) {
		List<List<FileInfo>> grps = new ArrayList<List<FileInfo>>();

		for (List<FileInfo> group : this.groups) {
			if (groupIncludesContext(ctx, group)) {
				grps.add(group);
			}
		}

		return grps;
	}

	public List<FileInfo> getContextDuplicates(FileInfo file) {
		if (!file.hasContextDuplicates()) {
			return null;
		}

		List<FileInfo> dups = new ArrayList<>();

		for (FileInfo f : getAllDuplicates(file)) {
			if (f.contextid == file.contextid) {
				dups.add(f);
			}
		}

		return dups;
	}

	public List<FileInfo> getGlobalDuplicates(FileInfo file) {
		if (!file.hasGlobalDuplicates()) {
			return null;
		}

		List<FileInfo> dups = new ArrayList<>();
		dups.add(file);

		for (FileInfo f : getAllDuplicates(file)) {
			if (f.contextid != file.contextid) {
				dups.add(f);
			}
		}

		return dups;
	}
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
