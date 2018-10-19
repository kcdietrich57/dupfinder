package dup.analyze;

import java.util.ArrayList;
import java.util.List;

import dup.model.Context;
import dup.model.Database;
import dup.model.FileInfo;
import dup.util.Trace;

public class BatchDetailsUpdate {
//	private static final int BATCH_SIZE = 100;

	public static class UpdateFileInfo extends FileInfo {
		private FileInfo origfile;

		public UpdateFileInfo(FileInfo orig) {
			super(orig.getFolder(), orig);

			this.origfile = orig;
		}
	}

	private Context context;
	private DetailLevel targetDetailLevel = DetailLevel.Sample;
	private DetailLevel minDetailLevel = DetailLevel.Sample;
	private List<UpdateFileInfo> updates = new ArrayList<UpdateFileInfo>();
	private int filecount = 0;

	public BatchDetailsUpdate() {
		this.context = null;
	}

	public BatchDetailsUpdate(Context context) {
		this.context = context;
	}

	public BatchDetailsUpdate(Context context, DetailLevel detail) {
		this(context);

		this.targetDetailLevel = detail;
	}

	public void setContext(Context context) {
		this.context = context;
	}

	public void setDetailLevel(DetailLevel detail) {
		this.targetDetailLevel = detail;
	}

	public void close() {
		loadDetails();
	}

	public void loadDetails() {
		if (this.size() == 0) {
			return;
		}

		Trace.traceln(Trace.VERBOSE, //
				"BatchDetailsUpdate: Processing " + size() + " files.");

		for (UpdateFileInfo ufile : this.updates) {
			if (!ufile.origfile.getDetailLevel().isLessThan(this.targetDetailLevel)) {
				continue;
			}

			Checksum.ChecksumValues checksums = Checksum.getChecksums( //
					ufile.origfile, this.context, this.targetDetailLevel);
			ufile.origfile.setChecksums(checksums);
		}

		this.filecount += this.updates.size();
		this.updates.clear();

		Trace.traceln(Trace.VERBOSE, //
				"BatchDetailsUpdate: Processed of " + this.filecount + " files.");

		Database.instance().getModel().structureChanged();
	}

	public int size() {
		return this.updates.size();
	}

	public boolean addFile(FileInfo file) {
		if (!file.getDetailLevel().isLessThan(this.targetDetailLevel)) {
			return false;
		}

		// if (size() >= BATCH_SIZE) {
		// loadDetails();
		// }

		this.updates.add(new UpdateFileInfo(file));

		if (this.minDetailLevel.isGreaterThan(file.getDetailLevel())) {
			this.minDetailLevel = file.getDetailLevel();
		}

		return true;
	}
}
