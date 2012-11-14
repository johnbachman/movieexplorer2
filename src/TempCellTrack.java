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
	
	public double[] getNormTimecourse() {
		return track.getNormTimecourse();
	}
	
	public double getMinValue() {
		//IJ.log("TempCellTrack.getMinValue: about to call CellTrack.getMinValue");
		return track.getMinValue();
	}
	
	public double getMaxValue() {
		return track.getMaxValue();
	}
}
