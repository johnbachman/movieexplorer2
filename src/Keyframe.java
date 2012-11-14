import java.awt.Rectangle;
import java.io.Serializable;

public class Keyframe implements Comparable<Keyframe>, Serializable {

	static final long serialVersionUID = 3L;
	
	private Rectangle roi;
	private int frame;
	private boolean momp;
	private boolean last;
	

	/**
	 * Constructor. Sets the frame and roi with the given parameters, and sets
	 * the MOMP and Last flags to false.
	 * 
	 * @param frame
	 * @param roi
	 */
	public Keyframe(int frame, Rectangle roi) {
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
	public Keyframe(int frame, Rectangle roi, boolean momp, boolean last) {
		this.frame = frame;
		this.roi = roi;
		this.momp = momp;
		this.last = last;
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
		sb.append("Frame: " + Integer.toString(this.frame) + " (" +
			Integer.toString(roi.x) + ", " + Integer.toString(roi.y) + ")");
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
	public Rectangle getRoi() {
		return roi;
	}
	public void setRoi(Rectangle roi) {
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
