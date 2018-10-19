package dup.browser;

import java.awt.Color;
import java.awt.Component;

import javax.swing.Icon;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeCellRenderer;

import dup.analyze.DetailLevel;
import dup.model.Context;
import dup.model.Database;
import dup.model.FileInfo;
import dup.model.FolderInfo;

public class FoldersTreeCellRenderer extends DefaultTreeCellRenderer {
	private static final long serialVersionUID = 1L;

	public Component getTreeCellRendererComponent( //
			JTree tree, //
			Object value, //
			boolean sel, //
			boolean expanded, //
			boolean leaf, //
			int row, //
			boolean hasFocus) {
		super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

		String name = value.toString();
		Icon icon = null;

		setBackgroundNonSelectionColor(Color.WHITE);

		if (value instanceof FolderInfo) {
			name = folderString((FolderInfo) value);
		} else if (value instanceof FileInfo) {
			FileInfo file = (FileInfo) value;
			name = getDetailLevelName(file.getDetailLevel()) + " " //
					+ file.getName();

			if (file.getSize() == 0) {
				icon = UIUtility.getIcon("imgEmptyFile", "");
			} else if (file.hasContextDuplicates()) {
				String iconname = (file.hasGlobalDuplicates()) ? "imgDuplicateFile" : "imgLocalDupFile";
				icon = UIUtility.getIcon(iconname, "");
			} else if (file.hasGlobalDuplicates()) {
				icon = UIUtility.getIcon("imgGlobalDupFile", "");
			}
		} else if (value instanceof Context) {
			Context context = (Context) value;

			name = getDetailLevelName(context.getDetailLevel()) + " " //
					+ context.getName() + " (" + context.getRootFile() + ")";

			if (context.isDirty()) {
				name += " DIRTY";
			}

			name = folderString(context.getRoot(), name);
		} else if (value instanceof Database) {
			name = "Database";
		}

		setText(name);

		if (icon != null) {
			setIcon(icon);
		}

		return this;
	}

	private String folderString(FolderInfo folder) {
		return folderString(folder, folder.getName());
	}

	private String folderString(FolderInfo folder, String name) {
		boolean hasdup = folder.getTreeDupCount() > 0;

		if (!hasdup) {
			return name;
		}

		int fc = folder.getFileCount();
		int fd = folder.getDupCount();
		int fp = (fc == 0) ? 0 : (fd * 100) / fc;
		int tc = folder.getTreeFileCount();
		int td = folder.getTreeDupCount();
		int tp = (tc == 0) ? 0 : (td * 100) / tc;

		String ttt = " " + fd + "/" + fc + "(" + fp + "%)" //
				+ " : " + td + "/" + tc + "(" + tp + "%)";
		// "[" + folder.getTreeDupCount() + "]";

		setToolTipText(ttt);

		if (tp >= 90) {
			setBackgroundNonSelectionColor(Color.PINK);
		} else if (tp >= 75) {
			setBackgroundNonSelectionColor(Color.YELLOW);
		}

		return name + " " + ttt;
	}

	private static String getDetailLevelName(DetailLevel detail) {
		switch (detail) {
		case None:
			return "[N]";
		case Prefix:
			return "[P]";
		case Sample:
			return "[S]";
		case Size:
			return "[Z]";
		default:
			return "???";
		}
	}
}
