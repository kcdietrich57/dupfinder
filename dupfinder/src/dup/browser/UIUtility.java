package dup.browser;

import java.net.URL;

import javax.swing.Icon;
import javax.swing.ImageIcon;

public final class UIUtility {
	public static Icon getIcon(String imageName, String altText) {
		String imgLocation = "images/" + imageName + ".gif";
		URL imageURL = Controller.class.getResource(imgLocation);

		return (imageURL != null) ? new ImageIcon(imageURL, altText) : null;
	}
}
