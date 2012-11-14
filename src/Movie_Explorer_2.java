import ij.IJ;
import ij.ImagePlus;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;

/** Driver for MovieExplorer. */
public class Movie_Explorer_2 implements PlugInFilter {
	/** Set to the currently active image */
	ImagePlus imq;

	public int setup(String arg, ImagePlus imq) {
		this.imq = imq;
		return DOES_ALL;
	}

	public void run(ImageProcessor ip) {
		if (IJ.versionLessThan("1.28"))
			return;
		ImagePlotter2 fp = new ImagePlotter2("Movie Explorer 2.0", imq);
		IJ.showStatus("run Movie_Explorer_2.0");
	}
}

