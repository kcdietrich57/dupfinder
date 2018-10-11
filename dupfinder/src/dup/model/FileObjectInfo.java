package dup.model;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public abstract class FileObjectInfo {
	private FolderInfo folder;
	private String name;

	public abstract long getSize();

	public FileObjectInfo(FolderInfo folder, File file) {
		this(folder, file.getName());
	}

	public FileObjectInfo(FolderInfo folder, String name) {
		this.folder = folder;
		this.name = name;
	}

	public Object[] getPathFromRoot() {
		List<Object> pathObjects = new ArrayList<Object>();

		getPathFromRoot(pathObjects);

		return pathObjects.toArray();
	}

	protected void getPathFromRoot(List<Object> pathObjects) {
		if (this.folder != null) {
			this.folder.getPathFromRoot(pathObjects);
		} else {
			pathObjects.add(Database.instance());
			pathObjects.add(getContext());
		}

		pathObjects.add(this);
	}

	public FolderInfo getFolder() {
		return this.folder;
	}

	public FolderInfo getRootFolder() {
		if (this.folder != null) {
			return this.folder.getRootFolder();
		}

		assert (this instanceof FolderInfo);
		return (FolderInfo) this;
	}

	public Context getContext() {
		return Database.instance().getContextForRoot(getRootFolder());
	}

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

	public String getRelativeName() {
		if (this.folder == null) {
			return "";
		}

		return this.folder.getRelativeName() + "/" + this.name;
	}

	public File getJavaFile() {
		return getJavaFile(getContext());
	}

	public File getJavaFile(Context context) {
		return (context != null) ? new File(context.getRootFile(), getRelativeName()) : getRelativeJavaFile();
	}

	public File getRelativeJavaFile() {
		return new File(getRelativeName());
	}

	public String getFullName() {
		File javafile = getJavaFile();

		return javafile.getAbsolutePath();
	}

	public boolean existsInContext(Context context) {
		File file = getJavaFile(context);

		return file.exists();
	}
}
