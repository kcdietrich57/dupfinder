package dup.analyze;

import javax.tools.FileObject;

import dup.model.FolderInfo;

public class Statistics {
	public long wastedBytes = 0;
	public double wastePercentage = 0.0;
	public int[] longDupChains = null;
	public FileObject[] largestDupFiles = null;
	public int[] largestDupChains = null;
	public FolderInfo[] foldersWithMostDupFiles = null;
	public FolderInfo[] foldersWithMostWastedSpace = null;
	public FolderInfo[] foldersWithHighestDupFilePercentage = null;

	public Statistics() {
	}
}
