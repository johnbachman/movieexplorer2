import ij.IJ;
import ij.ImagePlus;


public class TempCellTrack {
	private CellTrack track;
	private int id = -1;
	
	public TempCellTrack(Keyframe kf, ImagePlus img) {
		this.track = new CellTrack(id, kf, img);
	}
	
	public double[] getTimecourse() {
		return track.getTimecourse();
	}

	public double[] getSdTimecourse() {
		return track.getSdTimecourse();
	}

	public double[] getNormSdTimecourse() {
		return track.getNormSdTimecourse();
	}

	public double[] getNormTimecourse() {
		return track.getNormTimecourse();
	}
	
	public double getMinValue() {
		return track.getMinValue();
	}
	
	public double getMaxValue() {
		return track.getMaxValue();
	}

	public double getMinSdValue() {
		return track.getMinSdValue();
	}

	public double getMaxSdValue() {
		return track.getMaxSdValue();
	}

}
