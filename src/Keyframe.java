import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import ij.gui.Roi;
import ij.gui.PolygonRoi;
import ij.io.RoiDecoder;
import ij.io.RoiEncoder;

import java.awt.Rectangle;

public class Keyframe implements Comparable<Keyframe>, Serializable {

	static final long serialVersionUID = 3L;
	
	private int frame;
	private boolean momp;
	private boolean last;
	private transient Roi roi; // See comments below for serialization methods
	
	/**
	 * Constructor. Sets the frame and roi with the given parameters, and sets
	 * the MOMP and Last flags to false.
	 * 
	 * @param frame
	 * @param roi
	 */
	public Keyframe(int frame, Roi roi) {
		this(frame, roi, false, false);
	}
	
	/**
	 * Constructor.
	 * 
	 * @param frame
	 * @param roi
	 * @param momp
	 * @param last
	 */
	public Keyframe(int frame, Roi roi, boolean momp, boolean last) {
		this.frame = frame;
		this.roi = roi;
		this.momp = momp;
		this.last = last;
	}
 	
	/**
	 * Implementation of custom writeObject method for serialization. This is
	 * necessary because direct serialization of the Roi object results in a
	 * java.io.NotSerializableException for sun.java2d.SunGraphics2D (which
	 * must be used somewhere by the geometry objects used by the ImageJ
	 * Roi class).
	 *
	 * As a workaround, this method uses the ImageJ RoiEncoder class to convert
	 * the Roi object to a byte array, and writes the number of bytes to the
	 * ObjectOutputStream before writing the bytes themselves. This way the
	 * number of bytes to read can be determined by reading the int, then
	 * a byte array of the appropriate size can be created, the Roi bytes can
	 * be read, and then finally decoded back to an Roi using the RoiDecoder
	 * class.
	 *
	 * @param oos
	 */
	private void writeObject(ObjectOutputStream oos) {
		try {
			// Write the integer and boolean fields
			Movie_Explorer_2.log("About to call Keyframe writeobject for Keyframe");
			oos.writeInt(frame);
			oos.writeBoolean(momp);
			oos.writeBoolean(last);
			Movie_Explorer_2.log("Wrote int, bool, bool, about to call write for ROI");

			// Write the ROI as a byte array
			byte[] roiBytes = RoiEncoder.saveAsByteArray(roi);
			Movie_Explorer_2.log(Integer.toString(roiBytes.length) + " bytes to write");
			oos.writeInt(roiBytes.length);
			oos.write(roiBytes);
			Movie_Explorer_2.log("ROI successfully written");
		}
		catch (Exception e) {
			Movie_Explorer_2.log("Kf writeobject exception: " + e.toString());
		}
	}

	/**
	 * Implementation of custom readObject method for serialization. This is
	 * necessary because direct serialization of the Roi object results in a
	 * java.io.NotSerializableException for sun.java2d.SunGraphics2D (which
	 * must be used somewhere by the geometry objects used by the ImageJ
	 * Roi class).
	 *
	 * As a workaround, this method uses the ImageJ RoiEncoder class to convert
	 * the Roi object to a byte array, and writes the number of bytes to the
	 * ObjectOutputStream before writing the bytes themselves. This way the
	 * number of bytes to read can be determined by reading the int, then
	 * a byte array of the appropriate size can be created, the Roi bytes can
	 * be read, and then finally decoded back to an Roi using the RoiDecoder
	 * class.
	 *
	 * @param ois
	 */
	private void readObject(ObjectInputStream ois) {
		try {
			// Read the integer and boolean fields
			Movie_Explorer_2.log("About to call Keyframe readObject for Keyframe");
			this.frame = ois.readInt();
			this.momp = ois.readBoolean();
			this.last = ois.readBoolean();
			Movie_Explorer_2.log("Read int, bool, bool, about to call read for ROI");

			// Read the Roi as a byte array
			int numBytes = ois.readInt();
			Movie_Explorer_2.log(Integer.toString(numBytes) + " bytes to read");
			byte[] roiBytes = new byte[numBytes];
			int numBytesRead = ois.read(roiBytes);
			Movie_Explorer_2.log(Integer.toString(numBytesRead) + " bytes read");			
			this.roi = RoiDecoder.openFromByteArray(roiBytes);
			Movie_Explorer_2.log("ROI successfully read");
		}
		catch (Exception e) {
			Movie_Explorer_2.log("Kf writeobject exception: " + e.toString());
		}
	}

	/**
	 * Implementation of the Comparable interface, allows the Keyframes to
	 * be sorted in frame order.
	 */
	public int compareTo(Keyframe otherKf) {
		if (this.frame < otherKf.getFrame()) {
			return -1;
		}
		else if (this.frame > otherKf.getFrame()) {
			return 1;
		}
		else {
			return 0;
		}
	}

	// Equals, hashCode, toString //////////////////////////
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + frame;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		final Keyframe other = (Keyframe) obj;
		if (frame != other.frame)
			return false;
		return true;
	}
	
	public String toString() {
		StringBuffer sb = new StringBuffer();
		Rectangle rect = roi.getBounds();
		sb.append("Frame: " + Integer.toString(this.frame) + " (" +
			Integer.toString(rect.x) + ", " + Integer.toString(rect.y) + ")");
		
		if (momp) {
			sb.append(", MOMP");
		}
		if (last) {
			sb.append(", Last");
		}
		return sb.toString();
	}
	
	public String debugString() {
		StringBuffer sb = new StringBuffer("-- Keyframe: --\n");
		sb.append(roi.toString() + "\n");
		sb.append("Frame: " + this.frame + "\n");
		sb.append("MOMP: " + this.momp + "\n");
		sb.append("-- end Keyframe debugString() --\n");
		return sb.toString();
	}
	
	// Getters and Setters //////////////////////////////
	public Roi getRoi() {
		return roi;
	}
	public void setRoi(Roi roi) {
		this.roi = roi;
	}
	public int getFrame() {
		return frame;
	}
	public void setFrame(int frame) {
		this.frame = frame;
	}
	public boolean isMomp() {
		return momp;
	}
	public boolean isLast() {
		return last;
	}

}
