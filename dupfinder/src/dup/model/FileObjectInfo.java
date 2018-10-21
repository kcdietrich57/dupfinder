package dup.model;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Generic info about folder/file */
public abstract class FileObjectInfo {
	public final int contextid;
	private FolderInfo folder;
	private String name;

	public FileObjectInfo(FolderInfo folder, File file) {
		this(folder, file.getName());
	}

	public FileObjectInfo(FolderInfo folder, String name) {
		this.folder = folder;
		this.name = name;

		this.contextid = (folder != null) ? folder.contextid : 0;
	}

	public FileObjectInfo(int contextid, File file) {
		this.folder = null;
		this.name = file.getName();
		this.contextid = contextid;
	}

	/** For files, the file size; for folders, the tree size */
	public abstract long getSize();

	/** Build list of ancestor objects - Starting with Database */
	public Object[] getPathFromRoot() {
		List<Object> pathObjects = new ArrayList<Object>();

		buildPathFromRoot(pathObjects);

		return pathObjects.toArray();
	}

	protected final void buildPathFromRoot(List<Object> pathObjects) {
		if (this.folder != null) {
			this.folder.buildPathFromRoot(pathObjects);
		} else {
			pathObjects.add(Database.instance());
			pathObjects.add(getContext());
		}

		pathObjects.add(this);
	}

	public FolderInfo getFolder() {
		return this.folder;
	}

	/**
	 * Return the top folder in the filesystem model containing this object. i.e.
	 * the Context folder.
	 */
	public FolderInfo getRootFolder() {
		if (this.folder != null) {
			return this.folder.getRootFolder();
		}

		assert (this instanceof FolderInfo);
		return (FolderInfo) this;
	}

	/** Get the Context object representing the top folder for this object */
	public Context getContext() {
		return Database.instance().getContextForRoot(getRootFolder());
	}

	/**
	 * Return the Database's Context containing this filesystem object. If this
	 * object is not actually rooted in the Database, the corresponding Database
	 * Context is located and returned, if it exists.
	 */
	public Context getDatabaseContext() {
		Context exact = Database.instance().getContextForRoot(getRootFolder());
		if (exact != null) {
			return exact;
		}

		return Database.instance().getLikeContext(this);
	}

	public String getName() {
		return this.name;
	}

	/** Return path to this object relative to the containing Context folder */
	public String getRelativeName() {
		if (this.folder == null) {
			return "";
		}

		return this.folder.getRelativeName() + "/" + this.name;
	}

	/** Get a File object referencing this object within the current context. */
	public File getJavaFile() {
		return getJavaFile(getContext());
	}

	/**
	 * Get a Java File object referencing this object. If a context is supplied, the
	 * File is relative to that context's root folder, otherwise it is relative to
	 * this object's Context.
	 */
	public File getJavaFile(Context context) {
		return (context != null) //
				? new File(context.getRootFile(), getRelativeName()) //
				: getRelativeJavaFile();
	}

	/** Get a Java File object referencing this object relative to its context. */
	public File getRelativeJavaFile() {
		return new File(getRelativeName());
	}

	/** Get absolute path to this object within its context */
	public String getFullName() {
		File javafile = getJavaFile();

		return javafile.getAbsolutePath();
	}

//	/**
//	 * Get whether this object exists in the filesystem relative to a given context.
//	 */
//	public boolean existsInContext(Context context) {
//		File file = getJavaFile(context);
//
//		return file.exists();
//	}
}
