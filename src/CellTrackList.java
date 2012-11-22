import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Overlay;
import ij.gui.Roi;

import javax.swing.DefaultComboBoxModel;

/**
 * The CellTrackList serves as a container class for cell tracks. It subclasses
 * the DefaultComboBoxModel class which allows it to be linked to a ComboBox
 * displaying the currently saved tracks.
 * 
 * @author johnbachman
 */
public class CellTrackList extends DefaultComboBoxModel
{
	/** An ID required for the class to be Serializable */
	static final long serialVersionUID = 1L;
	
	/**
	 * Adds a track.
	 * 
	 * @param track The track to add.
	 */
	public void add(CellTrack track) {
		super.addElement(track);
	}
		
	/**
	 * Clears all tracks.
	 */
	public void clear() {
		for (int i = 0; i < super.getSize(); i++) {
			CellTrack track = (CellTrack) super.getElementAt(i);
			track.clear();
		}
		super.removeAllElements();
	}
	
  /**
   * Returns the number of tracks in the list. 
   */
	public int getSize() {
		return super.getSize();
	}
	
	/**
	 * Gets the track at the specified index.
	 * 
	 * @param index The index of the track to get.
	 */
	public CellTrack getElementAt(int index) {
		return (CellTrack) super.getElementAt(index);
	}
	
	/**
	 * When the value image (from which to calculate timecourse values) changes,
	 * this updates each track object accordingly.
	 * 
	 * @param img The value image to set.
	 */
	public void setValueImageForTracks(ImagePlus img) {
		for (int i = 0; i < this.getSize(); i++) {
			CellTrack track = getElementAt(i);
			track.setImage(img);
		}
	}

	/**
	 * Gets the minimum value in all of the stored tracks.
	 */
	public double getMinValue() {
		double minValue = 1000000;
		for (int i = 0; i < this.getSize(); i++) {
			CellTrack track = getElementAt(i);
			if (track.getMinValue() < minValue)
				minValue = track.getMinValue();
		}
		return minValue;
	}
	
	/**
	 * Gets the maximum value in all of the stored tracks.
	 */
	public double getMaxValue() {
		double maxValue = -1000000;
		for (int i = 0; i < this.getSize(); i++) {
			CellTrack track = getElementAt(i);
			if (track.getMaxValue() > maxValue)
				maxValue = track.getMaxValue();
		}
		return maxValue;
	}
	
	/**
	 * Get the smallest SD value in the whole track list.
	 */
	public double getMinSdValue() {
		double minValue = Double.MAX_VALUE;
		for (int i = 0; i < this.getSize(); i++) {
			CellTrack track = getElementAt(i);
			if (track.getMinSdValue() < minValue)
				minValue = track.getMinSdValue();
		}
		return minValue;
	}

	/**
	 * Get the largest SD value in the whole track list.
	 */
	public double getMaxSdValue() {
		double maxValue = Double.MIN_VALUE;
		for (int i = 0; i < this.getSize(); i++) {
			CellTrack track = getElementAt(i);
			if (track.getMaxSdValue() > maxValue)
				maxValue = track.getMaxSdValue();
		}
		return maxValue;
	}

	/**
	 * Gets the max minus min value (i.e., the maximum value after normalizing the
	 * minimum to zero) for the track that has the maximum such value across
	 * the entire track list. Needed for proper labeling of the axes when
	 * plotting all tracks normalized to the minimums but not the maxes.
	 */
	public double getNormMaxValueForMaxTrack() {
		double normMaxValue = Double.NEGATIVE_INFINITY;
		for (int i = 0; i < this.getSize(); i++) {
			CellTrack track = getElementAt(i);
			double val = track.getMaxValue() - track.getMinValue();
			if (val > normMaxValue)
				normMaxValue = val;
		}
		return normMaxValue;	
	}
	
	/**
	 * Returns a string with all state information.
	 */
	public String debugString() {
		StringBuffer sb = new StringBuffer("-- CellTrackList: --\n");
		for (int i = 0; i < this.getSize(); i++) {
			CellTrack track = this.getElementAt(i);
			sb.append(track.debugString());  
		}
		sb.append("-- end CellTrackList debugString() --\n");
		return sb.toString();
	}

	/**
	 * Gets the next available integer ID for a new track. The reason for this
	 * is that if the user deletes a track, e.g., track 2 from the list (1, 2, 3),
	 * yielding the list (1, 3), the new track should be given a unique ID that
	 * won't lead to later collisions. The easiest way to do this (without
	 * having to persist a global counter in the ImagePlotter object) is to simply
	 * have the CellTrackList generate the ID by returning the maximum integer ID
	 * in the set of tracks, plus one.
	 */
	public int getNextId() {
		if (this.getSize() == 0) {
			return 1;
		}
		
		int maxId = Integer.MIN_VALUE;
		for (int i = 0; i < this.getSize(); i++) { 
			CellTrack track = this.getElementAt(i);
			int trackId = track.getId();
			
			if (trackId > maxId)
				maxId = trackId;
		}
		return maxId + 1;
	}
	
	/**
	 * Returns an overlay object containing ROIs for every single track and
	 * keyframe that has been saved. Useful for seeing which cells have already
	 * been tracked.
	 */
	public Overlay getAllOverlays() {
		Overlay allOverlays = new Overlay();

		// Iterate over all tracks
		for (int i = 0; i < this.getSize(); i++) { 
			CellTrack track = this.getElementAt(i);
			Overlay trackOverlay = track.getOverlay();
			
			// Iterate over all ROIs in the track
			for (int j = 0; j < trackOverlay.size(); j++) {
				allOverlays.add(trackOverlay.get(j));
			}
		}
		return allOverlays;
	}

	/**
	 * Returns an overlay object containing ROIs for every single track and
	 * keyframe that has been saved. Useful for seeing which cells have already
	 * been tracked.
	 */
	public Overlay getAllActiveOverlays(int currentFrame) {
		Overlay allOverlays = new Overlay();
		
		// Iterate over all tracks
		for (int i = 0; i < this.getSize(); i++) { 
			CellTrack track = this.getElementAt(i);
			Overlay trackOverlay = track.getOverlayForFrame(currentFrame);
			allOverlays.add(trackOverlay.get(0));
		}
		return allOverlays;
	}
	
}
