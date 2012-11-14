import ij.ImagePlus;

/**
 * A wrapper class allowing for a nicer display of image titles in the
 * ROI image and value image chooser lists.
 * 
 * @author johnbachman
 */
public class ImagePlusWrapper {
	private ImagePlus ip;

	public ImagePlusWrapper(ImagePlus ip) {
		this.ip = ip;
	}
	
	public ImagePlus getImage() {
		return this.ip;
	}
	
	public String getTitle() {
		return this.ip.getTitle();
	}

	public String toString() {
		return this.ip.getTitle();
	}
	
	@Override
	public boolean equals(Object o) {
		if (o instanceof ImagePlusWrapper) {
			ImagePlusWrapper ipw = (ImagePlusWrapper) o;
			if (this.ip.equals(ipw.getImage())) {
				return true;
			}
			else {
				return false;
			}
		}
		else {
			return false;
		}
	}

	@Override
	public int hashCode() {
		return this.ip.hashCode();
	}

}
