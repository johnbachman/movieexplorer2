import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.measure.Measurements;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import java.awt.Rectangle;

import javax.swing.AbstractListModel;

public class CellTrack extends AbstractListModel {

	/** An ID required for the class to be Serializable */
	static final long serialVersionUID = 2L;
	
	/** A number uniquely identifying this cell track. */
	private int id;
	/** The sorted set of Keyframes; duplicates are not allowed. */
	private SortedSet<Keyframe> keyframes = new TreeSet<Keyframe>();
	/** The image from which to compute the timecourse. */
	private transient ImagePlus img;
	/** The title of the image */
	private String imageTitle;
	/** The timecourse associated with this cell. */
	private double[] timecourse;
	/** The timecourse normalized to its maximum and minimum values so that its
	 * minimum value is 0 and its maximum value is 1. */
	private double[] normTimecourse;
	/** The timecourse normalized so that its minimum value is 0, and a maximum
	 * value of max - min. */
	private double[] normMinTimecourse;
	/** The maximum value in the timecourse. */
	private double maxValue;
	/** The minimum value in the timecourse. */
	private double minValue;

	/** The timecourse of Standard Deviation values */
	private double[] sdTimecourse;
	/** The maximum SD value in the SD timecourse. */
	private double maxSdValue;
	/** The minimum SD value in the SD timecourse. */
	private double minSdValue;
	/** The SD timecourse normalized to its maximum and minimum values so that its
	 * minimum value is 0 and its maximum value is 1. */
	private double[] normSdTimecourse;

	/** The measurements to make on the region. */
	int defaultMeasurements = Measurements.AREA + Measurements.STD_DEV + 
												    Measurements.MEAN;

  /**
	 * Constructor: sets fields, computes the timecourse, and notifies
	 * associated ListDataListeners (i.e., lists of keyframes) of its
	 * contents.
	 * 
	 * @param id A number uniquely identifying this track.
	 * @param kf An initial keyframe.
	 * @param img The image from which to calculate the timecourse values.
	 */
	public CellTrack(int id, Keyframe kf, ImagePlus img) {
		this.id = id;
		this.img = img;
		this.imageTitle = img.getTitle();
		
		if (keyframes.add(kf)) {
			Movie_Explorer_2.logloop("CellTrack: about to update timecourse");
			updateTimecourse();
			fireContentsChanged(this, 0, getSize());
		}
	}

	/**
	 * Adds a Keyframe to the sorted set of Keyframes. Note that a Keyframe
	 * will not be added if it is for the same frame as an existing Keyframe.
	 * Recomputes the timecourse values using the new Keyframe and
	 * notifies ListDataListeners of the change to the set of keyframes.
	 * 
	 * Throws an exception if adding the keyframe would result in more than one
	 * frame with the same frame number, or more than one frame flagged as
	 * MOMP or Last.
	 * 
	 * @param kf The Keyframe to add.
	 */
	public void addKeyframe(Keyframe newKf) throws Exception {
		// Check to make sure that we're not duplicating MOMP or Last
		if (newKf.isLast()) {
			for (Keyframe kf : keyframes) {
				if (kf.isLast()) {
					throw new Exception("Cannot have more than one frame flagged as \"Last.\"");
				}
			}
		}
		if (newKf.isMomp()) {
			for (Keyframe kf : keyframes) {
				if (kf.isMomp()) {
					throw new Exception("Cannot have more than one frame flagged as MOMP.");
				}
			}
		}
		
		// Add the keyframe, and throw an exception if there is already a keyframe
		// for the same frame number.
		if (keyframes.add(newKf)) {
			updateTimecourse();
			fireContentsChanged(this, 0, getSize());
		}
		else {
			throw new Exception("Cannot add a keyframe with the same frame number " +
					"as one that already exists.");
		}
	}

	/**
	 * Deletes a Keyframe from the set of Keyframes. Returns a boolean
	 * indicating whether the keyframe was deleted. Notifies listeners
	 * of the change.
	 * 
	 * @param kf The Keyframe to deleted.
	 * @return True if the Keyframe was deleted, false otherwise.
	 */
	public boolean deleteKeyframe(Keyframe kf) {
		boolean wasRemoved = keyframes.remove(kf);
		updateTimecourse();
		fireContentsChanged(this, 0, getSize());
		return wasRemoved;
	}
	
	/**
	 * Clears the set of Keyframes and notifies listeners.
	 */
	public void clear() {
		keyframes.clear();
		this.timecourse = new double[this.img.getStackSize()];
		this.normTimecourse = new double[this.img.getStackSize()];
		fireContentsChanged(this, 0, getSize());
	}

	/** 
	 * Sets the image from which to calculate timecourse values.
	 * 
	 * @param img The image from which to calculate timecourse values.
	 */
	public void setImage(ImagePlus img) {
		this.img = img;
		this.imageTitle = img.getTitle();
		updateTimecourse();
	}

	/**
	 * Gets the title of the image from which the timecourse was calculated.
	 * 
	 * @return The title of the image from which the timecourse was calculated.
	 */
	public String getImageTitle() {
		return this.imageTitle;
	}

	/**
	 * Gets a clone of the current timecourse. This implies, of course, that
	 * the caller of this method cannot use the returned reference to modify
	 * the contents of the timecourse array.
	 * 
	 * @return The timecourse.
	 */
	public double[] getTimecourse() {
		return this.timecourse.clone();
	}
	
	/**
	 * Gets a clone of the current standard deviation timecourse.
	 *
	 * @return The standard deviation timecourse.
	 */
	public double[] getSdTimecourse() {
		return this.sdTimecourse.clone();
	}

	/**
	 * Gets a clone of the current normalized standard deviation timecourse.
	 *
	 * @return The normalized standard deviation timecourse.
	 */
	public double[] getNormSdTimecourse() {
		return this.normSdTimecourse.clone();
	}

	/**
	 * Gets a clone of the normalized timecourse. As with getTimecourse,
	 * this implies that the caller of this method cannot use the returned
	 * reference to modify the contents of the timecourse array.
	 * 
	 * @return The normalized timecourse.
	 */
	public double[] getNormTimecourse() {
		return this.normTimecourse.clone();
	}

	/**
	 * Gets a clone of the normalized minimum timecourse. This timecourse has
	 * had all of its values subtracted by the minimum timecourse value, so that
	 * the minimum value is 0 and the maximum value is max - min.
	 * As with getTimecourse, the caller of this method cannot use the returned
	 * reference to modify the contents of the timecourse array.
	 * 
	 * @return The normalized timecourse.
	 */
	public double[] getNormMinTimecourse() {
		return this.normMinTimecourse.clone();
	}

	
	/**
	 * Returns the frame number of the "last" frame in the timecourse,
	 * if there is one; otherwise returns the number of points in the image
	 * stack.
	 */
	public int getLastFrame() {
		int lastFrame = img.getStackSize();
		
		for (Keyframe kf : keyframes) {
			if (kf.isLast()) {
				lastFrame = kf.getFrame();
			}
		}
		return lastFrame;
	}
	
	
	/**
	 * Recomputes the timecourse for this CellTrack using the associated image.
	 */
	private void updateTimecourse() {
		ImageProcessor imgProcessor;
		ImageStack imgStack = img.getStack();
		//int numPoints = img.getStackSize();
		int numPoints = this.getLastFrame();

		Calibration cal = new Calibration(img);

		// Re-initialize the trajectory values
		this.timecourse = new double[numPoints]; 		
		this.normTimecourse = new double[numPoints];
		this.normMinTimecourse = new double[numPoints];
		this.sdTimecourse = new double[numPoints];
		this.normSdTimecourse = new double[numPoints];
		this.maxValue = Double.NEGATIVE_INFINITY;
		this.minValue = Double.POSITIVE_INFINITY;
		this.maxSdValue = Double.NEGATIVE_INFINITY;
		this.minSdValue = Double.POSITIVE_INFINITY;

		// If (for an inexplicable reason) there are no keyframes, let the 
		// timecourse be as initialized, as full of zeros
		if (this.keyframes.isEmpty()) {
			return;
		}

		Iterator<Keyframe> iter = this.keyframes.iterator();
		Keyframe nextKf = iter.next();
		Roi roi = nextKf.getRoi();

		// Loop over all timepoints
		for (int i = 1; i <= numPoints; i++) {
			// Get the image processor for the slice
			imgProcessor = imgStack.getProcessor(i);
			imgProcessor.setRoi(roi);
			ImageStatistics stats = ImageStatistics.getStatistics(imgProcessor,
																						defaultMeasurements, cal);
		
			if (i >= nextKf.getFrame()) {
				roi = nextKf.getRoi();
				if (iter.hasNext())
					nextKf = iter.next();
			}

			// Append the SD
			this.sdTimecourse[i-1] = stats.stdDev;

			// Update the min and max SD values (for normalization)
			if (stats.stdDev > this.maxSdValue) {
					this.maxSdValue = stats.stdDev;
			}
			if (stats.stdDev < this.minSdValue) {
				this.minSdValue = stats.stdDev;
			}

			// Append the Mean
			double value = stats.mean;

			if (value < 0) {
				value = 0;
			}
			else if (value > 100000) {
				value = 1;
			}
			
			this.timecourse[i-1] = value;

			// Update the min and max values (for normalization)
			if (value > this.maxValue) {
					this.maxValue = value;
			}
			if (value < this.minValue) {
				this.minValue = value;
			}
			
			
		} // end for loop
		
		// Create a list of normalized ratio values
		for (int i = 0; i < numPoints; i++) {
			// Normalize the mean
			if (maxValue - minValue > 0) {
				normTimecourse[i] = (timecourse[i] - minValue) / (maxValue - minValue);
				normMinTimecourse[i] = timecourse[i] - minValue;
			}
			else {
				normTimecourse[i] = 0;
			}

			// Normalize the SD
			if (maxSdValue - minSdValue > 0) {
				normSdTimecourse[i] = (sdTimecourse[i] - minSdValue) / (maxSdValue - minSdValue);
			}
			else {
				normSdTimecourse[i] = 0;
			}
		}
	} // end getTimecourse


	/**
	 * Creates and returns an ImageJ overlay object containing all of the ROIs
	 * for the keyframes in this cell track.
	 * 
	 * @return An overlay with an ROI for each Keyframe
	 */
	public Overlay getOverlay() {
		Overlay ol = new Overlay();

		Iterator<Keyframe> kfIter = keyframes.iterator();
		while (kfIter.hasNext()) {
			Keyframe kf = kfIter.next();
			//Roi roi = new Roi(kf.getRoi());
		  Roi roi = (Roi) kf.getRoi().clone();
			roi.setName(Integer.toString(kf.getFrame()));
			ol.addElement(roi);
			ol.drawLabels(true);
		}
		return ol;
	}

	/**
	 * Returns the SortedSet of keyframes for this track.
	 * 
	 * @return The SortedSet of keyframes
	 */
	public SortedSet<Keyframe> getKeyframes() {
		return this.keyframes;
	}

	/**
	 * Returns an array of all of the frame numbers in the set of keyframes
	 * for this track.
	 * 
	 * @return An integer array of frame numbers.
	 */
	public List<Integer> getKeyframeNumbers() {
		List<Integer> frameNumbers = new ArrayList<Integer>();

		// Exploits autoboxing and for-each loop features
		for (Keyframe kf : this.keyframes) {
			frameNumbers.add(kf.getFrame());
		}
		return frameNumbers;
	}

	
	
	/**
	 * Gets the overlay for the given frame based on the set of keyframes.
	 * 
	 * @param frame The frame to get the overlay for
	 * @return An overlay containing a Roi for the keyframe active at the
	 * given frame.
	 */
	public Overlay getOverlayForFrame(int frame) {
		Overlay ol = null;
		
		for (Keyframe kf : this.keyframes) {
			if (frame >= kf.getFrame()) {
				//ol = new Overlay(new Roi(kf.getRoi()));
				ol = new Overlay((Roi) kf.getRoi().clone());
			}
		}
		return ol;
	}
	
	public double getMaxValue() {
		return maxValue;
	}

	public double getMinValue() {
		//IJ.log("CellTrack.getMinValue, minValue = " + minValue);
		return minValue;
	}

	public double getMinSdValue() {
		return minSdValue;
	}

	public double getMaxSdValue() {
		return maxSdValue;
	}

	public int getId() {
		return id;
	}

	public String toString() {
		return Integer.toString(this.id);
	}

	public String debugString() {
		StringBuffer sb = new StringBuffer("-- CellTrack: --\n");
		sb.append("id: " + id + "\n");
		sb.append("img: " + img.getTitle() + "\n");
		sb.append("timecourse: " + timecourse + "\n");
		sb.append("normTimecourse: " + normTimecourse + "\n");
		sb.append("maxValue: " + maxValue + "\n");
		sb.append("minValue: " + minValue + "\n");
		sb.append("Keyframes: \n");
		
		Iterator<Keyframe> kfIter = keyframes.iterator();
		while (kfIter.hasNext()) {
			Keyframe kf = kfIter.next();
			sb.append(kf.debugString());
		}
		sb.append("-- end CellTrack debugString() --\n");
		return sb.toString();
	}
	
	// Methods from AbstractListModel
	/**
	 * Returns the number of Keyframes in the list.
	 */
	public int getSize() {
		return keyframes.size();
	}
	
	public Object getElementAt(int index) {
		return keyframes.toArray()[index];
	}



}
