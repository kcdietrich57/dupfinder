package dup.model;

public class ModelUtil {
	/** Determine the lowest common level of detail for a file/subtree */
	public static DetailLevel getMinimumDetailLevel(Object[] objs) {
		DetailLevel minDetail = DetailLevel.Sample;

		for (Object selobj : objs) {
			DetailLevel d = minDetail;

			if (selobj instanceof FileInfo) {
				d = ((FileInfo) selobj).getDetailLevel();
			} else if (selobj instanceof FolderInfo) {
				d = ((FolderInfo) selobj).getMinimumDetailLevel();
			} else if (selobj instanceof Context) {
				d = ((Context) selobj).getDetailLevel();
			}

			if (d.isLessThan(minDetail)) {
				minDetail = d;
			}
		}

		return minDetail;
	}
}
