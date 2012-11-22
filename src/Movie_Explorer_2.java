import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GUI;
import ij.gui.ImageCanvas;
import ij.gui.ImageWindow;
import ij.gui.NewImage;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.gui.PolygonRoi;
import ij.measure.Measurements;
import ij.plugin.filter.Analyzer;
import ij.process.ImageProcessor;
import ij.text.TextWindow;

import java.awt.Button;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;

import au.com.bytecode.opencsv.CSVReader;

import ij.plugin.frame.*;

// List box to allow user to pick which image to get ROI information from
// List box to allow user to pick image to get timecourse information from
// Switch to allow user to flag a keyframe as the frame of MOMP

/**
 * @author JohnBachman, JohnAlbeck
 *
 */
public class Movie_Explorer_2 extends PlugInFrame implements Measurements, ActionListener,
		KeyListener, FocusListener, ClipboardOwner, Runnable {

	/** The width from one timepoint to the next in the plot, measured in pixels */
	//protected int timestep;
	/** The number of decimal places for export of saved timecourses */
	//protected int decimalPlaces = Analyzer.getPrecision();

	ArrayList FrameNumberList;

	// Constants related to plotting
	static final int WIN_WIDTH = 800;
	static final int WIN_HEIGHT = 240;
	static final int PLOT_WIDTH = 740;
	static final int PLOT_HEIGHT = 180;
	static final int XMARGIN = 20;
	static final int YMARGIN = 20;
	static final Color CURRENT_FRAME_COLOR = new Color(0.0f, 0.0f, 1.0f);
	static final Color KEYFRAME_INDICATOR_COLOR = new Color(0.7f, 0.7f, 0.7f);
	static final Color MOMP_COLOR = new Color(1.0f, 0.0f, 0.0f);
	static final Color LAST_COLOR = new Color(0.0f, 0.0f, 0.0f);
	/** Indicates whether the plotting window itself is a color image */
	static final boolean COLOR = true;

	/** The plot refresh interval, in milliseconds */
	static final int REFRESH_INTERVAL = 100;

	// Constants related to gui
	static final String PLOT_CURRENT_ROI = "Plot Current Region";
	static final String PLOT_CURRENT_ROI_SD = "Plot Current Region, SD";
	static final String PLOT_SELECTED_TRACK = "Plot Selected Track";
	static final String PLOT_SELECTED_TRACK_SD = "Plot Selected Track, SD";
	static final String PLOT_SELECTED_CONTROL_SUBTRACTED = "Plot Selected, Control Subtracted";	
	static final String PLOT_ALL_TRACKS = "Plot All Tracks";
	static final String PLOT_ALL_TRACKS_SD = "Plot All Tracks, SD";
	static final String PLOT_ALL_TRACKS_NORMALIZED_MIN = "Plot All Tracks, Normalized Min";
	static final String PLOT_ALL_TRACKS_NORMALIZED = "Plot All Tracks, Normalized Min/Max";
	static final String PLOT_ALL_CONTROL_SUBTRACTED = "Plot All Tracks, Control Subtracted";	
	
	static final String SHOW_ACTIVE_FOR_SELECTED = "Show active region for selected track"; 
	static final String SHOW_ALL_FOR_SELECTED = "Show all regions for selected track";
	static final String SHOW_ACTIVE_FOR_ALL = "Show active regions for all tracks";
	static final String SHOW_ALL_FOR_ALL = "Show regions for all tracks";
	
	static final String PLOT_MEAN = "Mean";
	static final String PLOT_STANDARD_DEVIATION = "SD";
	
	// GUI Components
	protected Rectangle frame = null;
	protected JButton addNewTrack, addKeyframe, allTimecourse, clearAllTimecourses, showFrameNumber;
	protected JButton addTrackWithLast; 	// Just for Jeremie
	protected JCheckBox DrawLinesCheckBox; 
	protected JCheckBox DrawPointsCheckBox;
	protected JCheckBox PlotAllTimecoursesCheckBox;
	protected JCheckBox LowPassFilterCheckBox;
	protected JComboBox roiImageCbx;
	protected JComboBox valueImageCbx;
	protected JComboBox savedCellCbx;
	protected JList keyframeLst;
	protected JComboBox plotTypeCbx;
	protected JButton saveTracksBtn, loadTracksBtn;
	protected JButton deleteKeyframeBtn;
	protected JButton deleteTrackBtn;
	protected JComboBox overlayCbx;
	protected JButton updateImgListBtn;
	protected JFileChooser fc;
	protected JLabel filenameLbl; // Says which tracks file is loaded
	protected JButton loadControlBtn; // To load the control timecourse
	protected JLabel controlFileLbl; // Says which control timecourse file is loaded

	// Can probably go to TimecoursePlotter
	/** The title of the plugin */
	protected String title;
  /** The thread for the showPlot() update loop. */
  protected Thread thread;
  /** Boolean flag to stop the update loop when the plugin is closed. */
  protected boolean done;

	/** The image that we're getting the trajectories from */
	private ImagePlus valueImg;
	/** The image that we're getting the ROIs from */
	private ImagePlus roiImg;
	/** The number of slices in the image stack (i.e., timepoints) */
	protected int numPoints;
	/** Keeps track of which "slice" in the stack we're looking at, i.e. which timepoint */
	int TheCurrentSlice = -1;
	
	/** The control timecourse to be subtracted from the trajectories in the image. */
	protected double[] controlTimecourse;
	
	JCheckBox mompChk;
	JCheckBox lastChk;
	protected CellTrackList cellTracks = new CellTrackList();
	protected CellTrack currentTrack = null;
	
	/** Logging switches. */
	private static boolean logging = false;
	private static boolean loopLogging = false;
	
	/** The instance of the associated timecourse plotter. */
	TimecoursePlotter tcp;
	
	/** Constructor. Displays a plot using the specified title.
	 * 
	 * @param title The title of the window
	 * @param id
	 */
	public Movie_Explorer_2() {
		super("Movie Explorer 2");
		
		// Call the constructor for ImageWindow
		//super(NewImage.createRGBImage(title, WIN_WIDTH, WIN_HEIGHT, 1,
		//		NewImage.FILL_WHITE));
		setup();					// Build the GUI
		this.tcp = new TimecoursePlotter(this);

		thread = new Thread(this, "Movie Explorer 2");
		thread.start();		// Start running showPlot()
	}

	/**
	 * The main run loop, refreshes the trajectory plot every 200ms.
	 */
	public void run() {
		while (!done) {
			try {
				logloop("me2 run method");
				showPlot();
				Thread.sleep(REFRESH_INTERVAL);
			} catch (InterruptedException e) {
				log("InterruptedException in run method!");
				log(e.toString());
			}
			catch (Exception e) {
				log("Exception in run method!");
				log(e.toString());
			}
		}
	}
	
	/**
	 * Gets the trajectory information from the affiliated image and plots it. The
	 * method has to be synchronized because the "Get Timecourses" function tries
	 * to read the ValueList while showPlot may be trying to update it.
	 */
	public synchronized void showPlot() {
		try {
			if (tcp == null) {
				log("tcp is null");
				shutDown();
				return;
			}
			
			// The ImageProcessor for the Timecourse Plotter
			ImageProcessor myImageProcessor = tcp.getImageProcessor();

			if (myImageProcessor == null) {
				log("myImageProcessor is null");
				shutDown();
				return;
			}
	
			//ValueList = new double[numPoints]; 				
			//ValueListNorm = new double[numPoints];
			TheCurrentSlice  = this.roiImg.getCurrentSlice();
			logloop("showPlot: CurrentSlice " + TheCurrentSlice);
			//Rectangle roi = roiImg.getStack().getRoi();
		  Roi roi = roiImg.getRoi(); // will be null if no roi set
 	
		  logloop("About to test if valueimg is roiImg");
			if (!valueImg.equals(roiImg)) {
				logloop("Setting Roi on roiImg");
				valueImg.setRoi(roi); 
			}

			logloop("About to create the temp track");
			// Create the temporary track
			TempCellTrack tempTrack = new TempCellTrack(new Keyframe(1, roi), this.valueImg);
			
			drawPlot(myImageProcessor, tempTrack);
			logloop("showPlot: returned from drawPlot");
			//super.imp.setTitle(this.title); // Why do this every iteration?
	
			//this.valueImgOverlay.repaint();
			//log("showPlot: returned from overlay.repaint");
			//valueImg.updateAndDraw(); ---
	
			this.updateOverlay();
			
			// Updates the image from the data in the ImageProcessor
			tcp.getImagePlus().updateAndDraw();

		}
		catch (Exception ex) {
			log("showPlot exception:");
			log(ex.getMessage());
			log(ex.toString());
		}
	}

	/**
	 * Blanks out the entire plotting window.
	 */
	void clear(ImageProcessor ip) {
		logloop("in ImagePlotter2.clear");
		if (ip == null)
			return;
		if (COLOR) {
			int white = 0xffffff;
			int[] pixels = (int[]) ip.getPixels();
			int n = pixels.length;
			for (int i = 0; i < n; i++)
				pixels[i] = white;
		} else {
			byte white = (byte) 255;
			byte[] pixels = (byte[]) ip.getPixels();
			int n = pixels.length;
			for (int i = 0; i < n; i++)
				pixels[i] = white;
		}
	}

	/**
	 * Draws the trajectory and saves the pixel data  for the trajectory
	 * in the ImageProcessor object.
	 */
	protected void drawPlot(ImageProcessor ip, TempCellTrack tempTrack) {
		logloop("in drawPlot");
		if (ip == null) {
			shutDown();
			return;
		}

		clear(ip);

		double[] valuesToPlot = new double[numPoints];

		int timestep = (int) (PLOT_WIDTH / this.numPoints);

		// Draw first and last frame numbers
		ip.setFont(new Font("SansSerif", Font.PLAIN, 10));
		ip.drawString(d2s(1), XMARGIN, YMARGIN + PLOT_HEIGHT + 15);
		ip.drawString(d2s(numPoints), XMARGIN + numPoints * timestep, YMARGIN
				+ PLOT_HEIGHT + 15);
		ip.drawString("Frame", XMARGIN + numPoints * timestep / 2 - 10, YMARGIN
				+ PLOT_HEIGHT + 25);

		// Draw plot border
		ip.setColor(Color.black);
		ip.setLineWidth(1);
		frame = new Rectangle(XMARGIN, YMARGIN, PLOT_WIDTH, PLOT_HEIGHT);
		ip.drawRect(frame.x - 1, frame.y, frame.width + 2, frame.height + 1);

		// Draw horizontal line at 50% mark
		ip.setColor(Color.gray);
		ip.drawLine(XMARGIN, (int) (PLOT_HEIGHT * 0.5) + YMARGIN, XMARGIN
				+ PLOT_WIDTH, (int) (PLOT_HEIGHT * 0.5) + YMARGIN);
				
		// The min and max values of the plots
		double minVal = -100;
		double maxVal = 100;

		// should we plot all saved timecourses?
		//if (PlotAllTimecoursesCheckBox.isSelected() && this.cellTracks.getSize() > 0) {
		if ((plotTypeCbx.getSelectedItem().equals(PLOT_ALL_TRACKS) ||
         plotTypeCbx.getSelectedItem().equals(PLOT_ALL_TRACKS_SD) ||
  			 plotTypeCbx.getSelectedItem().equals(PLOT_ALL_TRACKS_NORMALIZED_MIN) ||
				 plotTypeCbx.getSelectedItem().equals(PLOT_ALL_TRACKS_NORMALIZED) ||
				 plotTypeCbx.getSelectedItem().equals(PLOT_ALL_CONTROL_SUBTRACTED)) 
				&& this.cellTracks.getSize() > 0) {
			logloop("Plotting all timecourses");
			
			// --------------------------------------------------------------------
			// THIS IS A HACK. THIS CODE SHOULD REALLY BE IMPLEMENTED IN THE
			// CELL TRACK LIST AND THE CONTROL SUBTRACTED TIMECOURSES SHOULD BE
			// CALCULATED WHENEVER UPDATETIMECOURSES IS CALLED.
			// Find the minimum background subtracted value across all timecourses
			double ctrlSubMinVal = Double.POSITIVE_INFINITY;
			double ctrlSubMaxVal = Double.NEGATIVE_INFINITY;
			
			// Set the max and min values for normalization of the plots
			if (plotTypeCbx.getSelectedItem().equals(PLOT_ALL_CONTROL_SUBTRACTED)) {
				for (int b = 0; b < this.cellTracks.getSize(); b++) {
					CellTrack track = this.cellTracks.getElementAt(b);
					// First, get the timecourse normalized to its minimum
					double[] values = track.getNormMinTimecourse();
					// Subtract out the control timecourse and check to see if it
					// is a new min or max
					for (int i = 0; i < values.length; i++) {						
						values[i] -= controlTimecourse[i];
						if (values[i] < ctrlSubMinVal) {
							ctrlSubMinVal = values[i];
						}
						if (values[i] > ctrlSubMaxVal) {
							ctrlSubMaxVal = values[i];
						}
					}
				}
			}

			// Iterate over all tracks
			for (int b = 0; b < this.cellTracks.getSize(); b++) {
				CellTrack track = this.cellTracks.getElementAt(b);

				if (plotTypeCbx.getSelectedItem().equals(PLOT_ALL_CONTROL_SUBTRACTED)) {
					minVal = ctrlSubMinVal;
					maxVal = ctrlSubMaxVal;

					// First, get the timecourse normalized to its minimum
					valuesToPlot = track.getNormMinTimecourse();
					// Now subtract out the control timecourse, which has also been
					// normalized to its minimum (presumably!)
					for (int i = 0; i < valuesToPlot.length; i++) {						
						valuesToPlot[i] -= controlTimecourse[i];
					}					
				}
				else if (plotTypeCbx.getSelectedItem().equals(PLOT_ALL_TRACKS)) {
					minVal = cellTracks.getMinValue();
					maxVal = cellTracks.getMaxValue();
					valuesToPlot = track.getTimecourse();
				}
				else if (plotTypeCbx.getSelectedItem().equals(PLOT_ALL_TRACKS_SD)) {
					minVal = cellTracks.getMinSdValue();
					maxVal = cellTracks.getMaxSdValue();
					valuesToPlot = track.getSdTimecourse();
				}
				else if (plotTypeCbx.getSelectedItem().equals(PLOT_ALL_TRACKS_NORMALIZED_MIN)) {
					minVal = 0;
					maxVal = cellTracks.getNormMaxValueForMaxTrack();
					valuesToPlot = track.getNormMinTimecourse();
				}
				else if (plotTypeCbx.getSelectedItem().equals(PLOT_ALL_TRACKS_NORMALIZED)) { // i.e., all tracks, normalized to min/max
					minVal = 0;
					maxVal = 1;
					valuesToPlot = track.getNormTimecourse();
				}
				else if (this.savedCellCbx.getSelectedItem() instanceof CellTrack) {
					logloop("Not plotting, no tracks saved.");
				}
				else {
					log("Error! Unrecognized plot selection.");
				}

				// Low pass filter?
				if (LowPassFilterCheckBox.isSelected()) {
					valuesToPlot = lowPassFilter(valuesToPlot);
				}
				
				// Iterate over all values in the timecourse
				for (int i = 0; i < valuesToPlot.length; i++) {
					double thisVal = (double) ((valuesToPlot[i] - minVal) / (maxVal - minVal));

					// Set the color to highlight the currently selected track
					if (this.savedCellCbx.getSelectedItem() instanceof CellTrack &&
							savedCellCbx.getSelectedItem().equals(track)) {
						ip.setColor(new Color(1.0f, 0.0f, 0.0f));
					}
					// The color for all other tracks
					else {
						// ip.setColor(getColor(thisVal)); // for rainbow color
						ip.setColor(new Color(0.0f, 0.0f, 0.0f));
					}

					// Draw Points
					if (DrawPointsCheckBox.isSelected()) {
						ip.fillOval((i+1) * timestep + XMARGIN - 2, PLOT_HEIGHT
								- (int) (thisVal * PLOT_HEIGHT) + YMARGIN - 2, 4, 4);
					}

					// Draw Lines
					if (DrawLinesCheckBox.isSelected()) {
						if (i != 0) {
							double lastVal = (double) ((valuesToPlot[i - 1] - minVal) / (maxVal - minVal));

							//ip.setColor(getColor(thisVal));
							int x1 = i * timestep + XMARGIN;
							int x2 = (i+1) * timestep + XMARGIN;
							int y1 = PLOT_HEIGHT - (int) (lastVal * PLOT_HEIGHT) + YMARGIN;
							int y2 = PLOT_HEIGHT - (int) (thisVal * PLOT_HEIGHT) + YMARGIN;
							ip.drawLine(x1, y1, x2, y2);
						}
					}
				}
			}
		} // --end plot saved timecourses
		else { // only plot the current timecourse
			if ((plotTypeCbx.getSelectedItem().equals(PLOT_SELECTED_TRACK) ||
					plotTypeCbx.getSelectedItem().equals(PLOT_SELECTED_TRACK_SD) ||
					plotTypeCbx.getSelectedItem().equals(PLOT_SELECTED_CONTROL_SUBTRACTED))
					&& this.savedCellCbx.getSelectedItem() instanceof CellTrack) {
				logloop("Plotting selected track");
				CellTrack track = (CellTrack) savedCellCbx.getSelectedItem();

				if (plotTypeCbx.getSelectedItem().equals(PLOT_SELECTED_TRACK)) {
					valuesToPlot = track.getTimecourse();
					maxVal = track.getMaxValue();
					minVal = track.getMinValue();
				}
				else if (plotTypeCbx.getSelectedItem().equals(PLOT_SELECTED_TRACK_SD)) {
					valuesToPlot = track.getSdTimecourse();
					maxVal = track.getMaxSdValue();
					minVal = track.getMinSdValue();
				}
				else if (plotTypeCbx.getSelectedItem().equals(PLOT_SELECTED_CONTROL_SUBTRACTED)) {
					// -------------------------------------------------------------------
					// THIS IS A HACK. THIS CODE SHOULD REALLY BE IMPLEMENTED IN THE
					// CELL TRACK LIST AND THE CONTROL SUBTRACTED TIMECOURSES SHOULD BE
					// CALCULATED WHENEVER UPDATETIMECOURSES IS CALLED.
					minVal = Double.POSITIVE_INFINITY;
					maxVal = Double.NEGATIVE_INFINITY;
					valuesToPlot = track.getNormMinTimecourse();
					for (int i = 0; i < valuesToPlot.length; i++) {
						valuesToPlot[i] -= this.controlTimecourse[i];
						if (valuesToPlot[i] < minVal) {
							minVal = valuesToPlot[i];
						}
						if (valuesToPlot[i] > maxVal) {
							maxVal = valuesToPlot[i];
						}
					}
					// Normalize the control subtracted values!
					for (int i = 0; i < valuesToPlot.length; i++) {
						valuesToPlot[i] = (valuesToPlot[i] - minVal) / (maxVal - minVal);
					}					
					// -------------------------------------------------------------------
				}
				else if (this.savedCellCbx.getSelectedItem() instanceof CellTrack) {
					logloop("Not plotting, no tracks saved.");
				}
				else {
					log("Error! Unrecognized plot selection.");
				}
			
				// If we're plotting the selected track, draw vertical lines at every
				// keyframe
				//List<Integer> frameNumbers = track.getKeyframeNumbers();
				Set<Keyframe> keyframes = track.getKeyframes();				
				for (Keyframe kf : keyframes) {
					if (kf.isMomp())
						ip.setColor(MOMP_COLOR);
					else if (kf.isLast())
						ip.setColor(LAST_COLOR);
					else
						ip.setColor(KEYFRAME_INDICATOR_COLOR);

					int frameNumber = kf.getFrame();
					ip.drawLine(frameNumber * timestep + XMARGIN, YMARGIN + 1,
						frameNumber * timestep + XMARGIN, (PLOT_HEIGHT + YMARGIN - 1));
				}
				ip.setColor(Color.black);
			}
			else if (plotTypeCbx.getSelectedItem().equals(PLOT_CURRENT_ROI_SD)) {
				logloop("Plotting SD of current ROI");
				valuesToPlot = tempTrack.getSdTimecourse();
				maxVal = tempTrack.getMaxSdValue();
				minVal = tempTrack.getMinSdValue();
			}
			else if (plotTypeCbx.getSelectedItem().equals(PLOT_CURRENT_ROI)) {
				logloop("Plotting current ROI");
				//valuesToPlot = ValueListNorm;
				valuesToPlot = tempTrack.getTimecourse();
				maxVal = tempTrack.getMaxValue();
				minVal = tempTrack.getMinValue();
				logloop("got value to Plot: length is " + valuesToPlot.length);
			}
			else if (this.savedCellCbx.getSelectedItem() instanceof CellTrack) {
				logloop("Not plotting, no tracks saved.");
			}
			else {
				log("Error! Unrecognized plot option.");
			}

			if (LowPassFilterCheckBox.isSelected()) {
				valuesToPlot = lowPassFilter(valuesToPlot);
				//valuesToPlot = lowPassFilter(ValueListNorm);
			}

			// Iterate over all values in the timecourse
			for (int i = 0; i < valuesToPlot.length; i++) {
				double thisVal = (double) ((valuesToPlot[i] - minVal) / (maxVal - minVal));

				if (DrawPointsCheckBox.isSelected()) {
					ip.setColor(getColor(thisVal));
					ip.fillOval((i+1) * timestep + XMARGIN - 2, PLOT_HEIGHT
							- (int) (thisVal * PLOT_HEIGHT) + YMARGIN - 2, 4, 4);
				}

				if (DrawLinesCheckBox.isSelected()) {
					if (i != 0) {
						double lastVal = (double) ((valuesToPlot[i - 1] - minVal) / (maxVal - minVal));

						ip.setColor(getColor(thisVal));
						int x1 = i * timestep + XMARGIN;
						int x2 = (i+1) * timestep + XMARGIN;
						int y1 = PLOT_HEIGHT - (int) (lastVal * PLOT_HEIGHT) + YMARGIN;
						int y2 = PLOT_HEIGHT - (int) (thisVal * PLOT_HEIGHT) + YMARGIN;
						ip.drawLine(x1, y1, x2, y2);
					}
				}
			}
		} // -- end plot current timecourse

		// drawing the current frame line
		ip.setColor(CURRENT_FRAME_COLOR);
		ip.drawLine(TheCurrentSlice * timestep + XMARGIN, YMARGIN + 1,
				TheCurrentSlice * timestep + XMARGIN, (PLOT_HEIGHT + YMARGIN - 1));
		ip.setColor(Color.black);

		// Draw min and max values for y-axis
		ip.drawString(d2s(minVal), XMARGIN + PLOT_WIDTH + 2, YMARGIN
				+ PLOT_HEIGHT + 8);
		ip.drawString(d2s(maxVal), XMARGIN + PLOT_WIDTH + 2, YMARGIN + 8);
	}
	
	/**
	 * Gets the open images and returns it as an array of ImagePlusWrapper
	 * objects.
	 */
	private ImagePlusWrapper[] getOpenImages() {
		int[] wList;
		wList = WindowManager.getIDList();
		if (wList == null || wList.length < 1) {
			IJ.showMessage("You need at least one image open...");
			return null;
		}
		// Store the image objects and titles for the open images
		//String[] titles = new String[wList.length];
		ImagePlusWrapper[] openImages = new ImagePlusWrapper[wList.length];
		for (int i = 0; i < wList.length; i++) {
			ImagePlusWrapper tempImg = new ImagePlusWrapper(WindowManager.getImage(wList[i]));
			/*
			if (tempImg != null)
				titles[i] = tempImg.getTitle();
			else
				titles[i] = "(null)";
			*/
			openImages[i] = tempImg;
		}
		return openImages;
	}
	
	/**
	 * Sets up the GUI.
	 */
	public void setup() {

		if (IJ.versionLessThan("1.27w"))
			return;

		//FrameNumberList = new ArrayList();

		// Get the list of open images		
		ImagePlusWrapper[] openImages = getOpenImages();
		this.setValueImage(openImages[0].getImage());
		this.setRoiImage(openImages[0].getImage());
		
		if (this.valueImg == null) {
			IJ.showMessage(this.title, "Out of memory");
			return;
		}
		
		fc = new JFileChooser();
		
		JPanel masterPanel = new JPanel(new GridBagLayout());
		GridBagConstraints c; // to be used for all GridBag constraints
		
		JPanel chooser = new JPanel();
		//chooser.setBorder(BorderFactory.createLineBorder(Color.black));
		chooser.setLayout(new FlowLayout());
		this.roiImageCbx = new JComboBox(openImages);
		this.valueImageCbx = new JComboBox(openImages);
		this.roiImageCbx.addActionListener(this);
		this.valueImageCbx.addActionListener(this);
		JLabel roiChooserLbl = new JLabel("Region Image: ");
		JLabel valueChooserLbl = new JLabel("Timecourse Image: ");
		chooser.add(valueChooserLbl);
		chooser.add(valueImageCbx);
		chooser.add(roiChooserLbl);
		chooser.add(roiImageCbx);
		//
		c = new GridBagConstraints();
		c.gridx = 0;
		c.gridy = 0;
		c.gridwidth = 2;
		c.anchor = GridBagConstraints.FIRST_LINE_START;
		c.fill = GridBagConstraints.HORIZONTAL;
		masterPanel.add(chooser, c);		
		//super.add(chooser);

		JPanel options = new JPanel();
		//options.setLayout(new GridLayout(1,5));
		options.setLayout(new FlowLayout());
		//options.setBorder(BorderFactory.createLineBorder(Color.black));
		//
		updateImgListBtn = new JButton("Update Image Lists");
		updateImgListBtn.addActionListener(this);
		options.add(updateImgListBtn);
		//
		String[] plotTypes = {PLOT_CURRENT_ROI,
				PLOT_CURRENT_ROI_SD,
				PLOT_SELECTED_TRACK,
				PLOT_SELECTED_CONTROL_SUBTRACTED,
				PLOT_SELECTED_TRACK_SD,
				PLOT_ALL_TRACKS,
				PLOT_ALL_TRACKS_SD,
				PLOT_ALL_TRACKS_NORMALIZED_MIN,
				PLOT_ALL_TRACKS_NORMALIZED,
				PLOT_ALL_CONTROL_SUBTRACTED};
		plotTypeCbx = new JComboBox(plotTypes);
		plotTypeCbx.setSelectedItem(PLOT_CURRENT_ROI);
		options.add(plotTypeCbx);
		//
		DrawLinesCheckBox = new JCheckBox("Draw Lines");
		DrawLinesCheckBox.setSelected(true);
		options.add(DrawLinesCheckBox);
		//
		DrawPointsCheckBox = new JCheckBox("Draw Points");
		DrawPointsCheckBox.setSelected(true);
		options.add(DrawPointsCheckBox);
		//
		LowPassFilterCheckBox = new JCheckBox("Low-Pass Filter");
		LowPassFilterCheckBox.setSelected(false);
		options.add(LowPassFilterCheckBox);
		//
		//showActiveRegionChk.setSelected(false);
		//options.add(showActiveRegionChk);
		//
		c = new GridBagConstraints();
		c.gridx = 0;
		c.gridy = 1;
		c.gridwidth = 2;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.anchor = GridBagConstraints.FIRST_LINE_START;
		masterPanel.add(options, c);
	
		JPanel control = new JPanel();
		//options.setLayout(new GridLayout(1,5));
		control.setLayout(new FlowLayout());
		//control.setBorder(BorderFactory.createLineBorder(Color.black));
		//
		loadControlBtn = new JButton("Load Control Timecourse...");
		loadControlBtn.addActionListener(this);
		control.add(loadControlBtn);
		controlFileLbl = new JLabel("Control Timecourse File: None Loaded");
		control.add(controlFileLbl);
		c = new GridBagConstraints();
		c.gridx = 0;
		c.gridy = 2;
		//c.fill = GridBagConstraints.HORIZONTAL;
		c.anchor = GridBagConstraints.FIRST_LINE_START;
		masterPanel.add(control, c);
		
		JPanel overlayPnl = new JPanel();
		overlayPnl.setLayout(new FlowLayout());
		//overlayPnl.setBorder(BorderFactory.createLineBorder(Color.black));
		JLabel overlayLbl = new JLabel("Region Display Options: ");
		overlayPnl.add(overlayLbl);
		String[] overlayOptions = {SHOW_ACTIVE_FOR_SELECTED, 
				SHOW_ALL_FOR_SELECTED, SHOW_ACTIVE_FOR_ALL, SHOW_ALL_FOR_ALL};
		overlayCbx = new JComboBox(overlayOptions);
		overlayPnl.add(overlayCbx);
		
		JLabel plotOptsLbl = new JLabel("Plot Options: ");
		overlayPnl.add(plotOptsLbl);
		String[] plotOptions = {PLOT_MEAN, PLOT_STANDARD_DEVIATION};
		JComboBox plotOptionsCbx = new JComboBox(plotOptions);
		overlayPnl.add(plotOptionsCbx);
		
		c = new GridBagConstraints();
		c.gridx = 0;
		c.gridy = 3;
		c.gridwidth = 4;
		//c.fill = GridBagConstraints.HORIZONTAL;
		c.anchor = GridBagConstraints.FIRST_LINE_START;
		masterPanel.add(overlayPnl, c);
			
		JPanel savedCells = new JPanel();
		savedCells.setLayout(new GridBagLayout());
		//savedCells.setBorder(BorderFactory.createLineBorder(Color.black));
		//
		c = new GridBagConstraints();
		c.gridx = 0;
		c.gridy = 0;
		c.anchor = GridBagConstraints.FIRST_LINE_END;
		JLabel stLabel = new JLabel("Saved Track: ");
		savedCells.add(stLabel, c);
		//
		c = new GridBagConstraints();
		c.gridx = 1;
		c.gridy = 0;
		c.anchor = GridBagConstraints.FIRST_LINE_START;
		String[] emptyList = {"(none saved)"};
		this.savedCellCbx = new JComboBox(emptyList);
		this.savedCellCbx.addActionListener(this);
		savedCells.add(this.savedCellCbx, c);
		//
		c = new GridBagConstraints();
		c.gridx = 0;
		c.gridy = 1;
		c.anchor = GridBagConstraints.FIRST_LINE_END;
		JLabel kfLabel = new JLabel("Keyframes: ");
		savedCells.add(kfLabel, c);
		//
		c = new GridBagConstraints();
		c.gridx = 1;
		c.gridy = 1;
		c.anchor = GridBagConstraints.FIRST_LINE_START;
		this.keyframeLst = new JList();
		JScrollPane scrollpane = new JScrollPane(this.keyframeLst); 
		savedCells.add(scrollpane, c);
		//
		c = new GridBagConstraints();
		c.gridx = 2;
		c.gridy = 0;
		c.gridheight = 2;
		JPanel addDeleteButtons = new JPanel(new GridLayout(6,1));
		addNewTrack = new JButton("Add New Track");
		addNewTrack.addActionListener(this);
		addDeleteButtons.add(addNewTrack);
	
		// For Jeremie 11/11/11
		addTrackWithLast = new JButton("Add Track With Last");
		addTrackWithLast.addActionListener(this);
		addDeleteButtons.add(addTrackWithLast);
		
		addKeyframe = new JButton("Add Keyframe");
		addKeyframe.addActionListener(this);
		addKeyframe.addKeyListener(this);
		addDeleteButtons.add(addKeyframe);
		//
		JPanel mompLast = new JPanel(new GridLayout(1,2));
		mompChk = new JCheckBox("MOMP");
		mompLast.add(mompChk);
		lastChk = new JCheckBox("Last");
		mompLast.add(lastChk);
		addDeleteButtons.add(mompLast);
		//
		deleteTrackBtn = new JButton("Delete Track");
		deleteTrackBtn.addActionListener(this);
		addDeleteButtons.add(deleteTrackBtn);
		deleteKeyframeBtn = new JButton("Delete Keyframe");
		deleteKeyframeBtn.addActionListener(this);
		addDeleteButtons.add(deleteKeyframeBtn);
		clearAllTimecourses = new JButton("Clear Tracks");
		clearAllTimecourses.addActionListener(this);
		addDeleteButtons.add(clearAllTimecourses);
		savedCells.add(addDeleteButtons, c);
		//
		c = new GridBagConstraints();
		c.gridx = 0;
		c.gridy = 4;
		c.anchor = GridBagConstraints.FIRST_LINE_START;
		masterPanel.add(savedCells, c);
		//super.add(savedCells);

		JPanel savePanel = new JPanel(new GridLayout(5,1));
		//savePanel.setBorder(BorderFactory.createLineBorder(Color.black));
		allTimecourse = new JButton("Get Plotted Timecourses");
		allTimecourse.addActionListener(this);
		savePanel.add(allTimecourse);
		showFrameNumber = new JButton("Show Frame Numbers");
		showFrameNumber.addActionListener(this);
		savePanel.add(showFrameNumber);
		saveTracksBtn = new JButton("Save Tracks...");
		saveTracksBtn.addActionListener(this);
		savePanel.add(saveTracksBtn);
		loadTracksBtn = new JButton("Load Tracks...");
		loadTracksBtn.addActionListener(this);
		savePanel.add(loadTracksBtn);		
		filenameLbl =	new JLabel("Tracks File: None Loaded");		// GET RID OF THIS AND USE A FILE CHOOSER
		savePanel.add(filenameLbl);
		//this.text.setText("enter filename here");
		//
		c = new GridBagConstraints();
		c.gridx = 1;
		c.gridy = 4;
		c.anchor = GridBagConstraints.FIRST_LINE_START;
		c.fill = GridBagConstraints.BOTH;
		masterPanel.add(savePanel, c);
		//super.add(savePanel);

		/* TODO: Should probably get rid of this, but it seems to have made
		 * the layout display correctly!!!
		 */
		//JPanel testPanel = new JPanel(new FlowLayout());
		//Component comp = (Component) new ImageCanvas(new ImagePlus("/Users/johnbachman/Desktop/12Ratio.jpg"));
		//masterPanel.add(testPanel);
		/* end section to get rid of */
		
		//super.add(masterPanel);
		super.add(masterPanel);
		
		/*
		Panel labels = new Panel();
		labels.setLayout(new GridLayout(1,2));
		JLabel regionLbl = new JLabel("Region: ");
		JLabel frameLbl = new JLabel("Frame: ");
		labels.add(regionLbl);
		labels.add(frameLbl);
		super.add(labels);
		*/
		super.pack();
		super.setResizable(true);
		GUI.center(this);
		super.show();
	}

	/**
	 * Sets the value image (the image from which we are extracting timecourse
	 * information and intensity values) and updates related class variables
	 * accordingly.
	 * 
	 * @param valueImg The ImagePlus object from which to extract intensity values.
	 */
	void setValueImage(ImagePlus valueImg) {
		this.valueImg = valueImg;
		//this.valueImgOverlay = new CustomCanvas(valueImg);
		//this.valueImg
		this.numPoints = valueImg.getStack().getSize();
		this.cellTracks.setValueImageForTracks(valueImg);
		
		if (this.controlTimecourse == null) {
			this.controlTimecourse = new double[this.numPoints];
		}
		else if (this.controlTimecourse.length != this.numPoints) {
			log("setValueImage: Error! The newly set timecourse image has a");
			log("  different number of points than the loaded control file.");
			log("  Resetting the control timecourse to 0.");
			this.controlTimecourse = new double[this.numPoints];
		}
			
	}
	
	/**
	 * Sets the ROI image (the image to get the regions of interest from).
	 * 
	 * @param roiImg The ImagePlus object from which to get the ROIs.
	 */
	void setRoiImage(ImagePlus roiImg) {
		this.roiImg = roiImg;
	}
	
	/** 
	 * Overrides the method in the ImageWindow parent class, adding
	 * a call to close down the TimecoursePlotter.
	 */
	public void windowClosing(WindowEvent e) {
		super.windowClosing(e);
		this.tcp.windowClosing(e);
	}
	
	/**
	 * Sets a boolean flag, done, which allows the run() loop to terminate.
	 */
	public void shutDown() {
		done = true;
	}

	String d2s(double d) {
		if ((int) d == d)
			return IJ.d2s(d, 0);
		else
			return IJ.d2s(d, Analyzer.getPrecision());
	}

	int getWidth(double d, ImageProcessor ip) {
		return ip.getStringWidth(d2s(d));
	}
	
	void showFrameNumberList() {
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < this.cellTracks.getSize(); i++) {
			int val = ((Integer) FrameNumberList.get(i)).intValue();
			sb.append(d2s(val) + "\t");
			sb.append("\n");
		}

		StringBuffer headings = new StringBuffer();
		headings.append("Frame" + "\t");

		TextWindow tw = new TextWindow(getTitle(), headings.toString(),
				sb.toString(), 200, 400);
	}

	/**
	 * Displays the saved timecourses in a text window in tabular format.
	 */
	void showAllTimecourses() {
		StringBuffer sb = new StringBuffer();
		StringBuffer headings = new StringBuffer();
		/*
		for (int i = 0; i < numPoints; i++) {
			for (int j = 0; j < blockSize; j++) {
				sb.append(d2s(ratioListBlock[i][j]) + "\t");
			}
			sb.append("\n");
		}		

		StringBuffer headings = new StringBuffer();
		for (int k = 0; k < blockSize; k++) {
			headings.append("Cell " + d2s(k + 1) + "\t");
		}
		*/
		for (int i = 0; i < numPoints; i++) {
			for (int col = 0; col <= this.cellTracks.getSize(); col++) {				
				// Useful to see the frame numbers
				if (col == 0) {
					sb.append((i+1) + "\t");
					continue;
				}
				
				CellTrack track = this.cellTracks.getElementAt(col - 1);
				double[] timecourse = null;

				if (plotTypeCbx.getSelectedItem().equals(PLOT_ALL_TRACKS)) {
					timecourse = track.getTimecourse();
				}
				else if (plotTypeCbx.getSelectedItem().equals(PLOT_ALL_TRACKS_SD)) {
					timecourse = track.getSdTimecourse();
				}
				else if (plotTypeCbx.getSelectedItem().equals(PLOT_ALL_TRACKS_NORMALIZED_MIN)) {
					timecourse = track.getNormMinTimecourse();
				}
				else if (plotTypeCbx.getSelectedItem().equals(PLOT_ALL_TRACKS_NORMALIZED)) {
					timecourse = track.getNormTimecourse();
				}
				else {
					String s = "Please select \n\"" +
										 PLOT_ALL_TRACKS + "\", \n\"" +
								     PLOT_ALL_TRACKS_SD + "\", \n\"" +
								     PLOT_ALL_TRACKS_NORMALIZED_MIN + "\", or \n\"" +
								     PLOT_ALL_TRACKS_NORMALIZED + "\".";
					IJ.showMessage(s);
					return;
				}

				int lastFrame = track.getLastFrame();	
				if (i > lastFrame - 1) {
					//sb.append("0\t");
					sb.append("\t");
				}
				else {
					sb.append(d2s(timecourse[i]) + "\t");					
				}
			}
			sb.append("\n");
		}		

		headings.append("Frame Number\t");
		
		for (int k = 0; k < this.cellTracks.getSize(); k++) {
			CellTrack track = this.cellTracks.getElementAt(k);
			headings.append("Track " + track + "\t");
		}

		TextWindow tw = new TextWindow(getTitle(), headings.toString(), sb
				.toString(), 200, 400);
	}

	/**
	 * Clears the saved timecourses.
	 */
	void clearTracks() {
		//ratioListBlock = new double[numPoints][blockSize];
		//blockIndex = 0;
		//FrameNumberList = new ArrayList();
		//this.savedCellCbx.removeAllItems();

		// Reset to say "no tracks" before clearing the CellTrackList
		// (because an exception is thrown if the underlying
		// DefaultComboBoxModel is empty).
		log("Resetting saved track list to empty list");
		String[] emptyList = {"(none saved)"};
		this.savedCellCbx.setModel(new DefaultComboBoxModel(emptyList));
		log("Clearing cellTracks");
		this.cellTracks.clear();
		this.currentTrack.clear();
		this.currentTrack = null;
		this.valueImg.setOverlay(null);
		this.roiImg.setOverlay(null);
	}

	/**
	 * Adds the current timecourse to the list of saved timecourses.
	 */
	synchronized void addNewTrack() {

		// Get the ROI from the ROI image
		//Rectangle roi = this.roiImg.getStack().getRoi();
		Roi roi = this.roiImg.getRoi();
		//Roi my_roi = this.roiImg.getRoi();
		//if (my_roi instanceof PolygonRoi)
		//	log("My_roi is polygon");
		//else
		//	log("My_roi is not polygon");

			// New track, so make the first keyframe at frame 1
		Keyframe kf = new Keyframe(1, roi);

		CellTrack track = new CellTrack(this.cellTracks.getNextId(), kf, this.valueImg);
		this.currentTrack = track;
		this.cellTracks.add(track);
		this.cellTracks.setSelectedItem(track);
		this.savedCellCbx.setModel(cellTracks);
//		this.savedCellCbx.addItem(Integer.toString(blockIndex));
		this.keyframeLst.setModel(track);

		//this.valueImgOverlay.addPosition(roi.x, roi.y, roi.height, roi.width);
		//this.valueImg.updateAndDraw();
		this.updateOverlay();
		
		//Overlay ol = currentTrack.getOverlay();
		//this.valueImg.setOverlay(ol);
		//if (!valueImg.equals(roiImg)) {
		//	this.roiImg.setOverlay(ol);			
		//}
		// Copy the current timecourse into the list block
		/*
		if (blockIndex < blockSize) {
			for (int i = 0; i < numPoints; i++) {
				if (ValueList[i] < BlockMinVal)
					BlockMinVal = ValueList[i];
				if (ValueList[i] > BlockMaxVal)
					BlockMaxVal = ValueList[i];

				ratioListBlock[i][blockIndex] = ValueList[i];				
			}
			
			blockIndex++;
		}
*/
		// remembering which frame was being viewed when selecting the roi (for
		// Debbie)
		//FrameNumberList.add(new Integer(TheCurrentSlice));
	}

	/**
	 * Adds the current timecourse to the list of saved timecourses, while
	 * also adding a Keyframe marked as the last frame of the trajectory.
	 * Added to facilitate the easy entry of tracks for Jeremie 11/11/11.
	 */
	synchronized void addTrackWithLast() {
		log("In addTrackWithLast.");

		// Get the ROI from the ROI image
		//Rectangle roi = this.roiImg.getStack().getRoi();
	  Roi roi = this.roiImg.getRoi();

		// New track, so make the first keyframe at frame 1
		Keyframe kf = new Keyframe(1, roi);

		CellTrack track = new CellTrack(this.cellTracks.getNextId(), kf, this.valueImg);
		this.currentTrack = track;
		this.cellTracks.add(track);
		this.cellTracks.setSelectedItem(track);
		this.savedCellCbx.setModel(cellTracks);

		int currentSlice  = this.roiImg.getCurrentSlice();
		Keyframe kfLast = new Keyframe(currentSlice, roi, false, true);
		
		try {
			this.currentTrack.addKeyframe(kfLast);
			//this.mompChk.setSelected(false);
			//this.lastChk.setSelected(false);
		} catch (Exception ex) {
			IJ.showMessage(ex.getMessage());
		}
		
		log("Created new track with last keyframe " + currentSlice);
		
		//this.keyframeLst.setModel(this.currentTrack);
		//this.keyframeListModel.addElement(kf);

		this.keyframeLst.setModel(track);

		this.updateOverlay();		
	}

	void addKeyframe() {
		log("In addKeyframe. Current track: " + this.currentTrack);
		//Rectangle roi = this.roiImg.getStack().getRoi();
	  Roi roi = this.roiImg.getRoi();
		//this.valueImgOverlay.addPosition(roi.x, roi.y, roi.height, roi.width);
		int currentSlice  = this.roiImg.getCurrentSlice();
		boolean momp = this.mompChk.isSelected();
		boolean last = this.lastChk.isSelected();
		Keyframe kf = new Keyframe(currentSlice, roi, momp, last);
		
		try {
			this.currentTrack.addKeyframe(kf);
			this.mompChk.setSelected(false);
			this.lastChk.setSelected(false);
		} catch (Exception ex) {
			log(ex.getMessage());
			IJ.showMessage(ex.getMessage());
		}
		
		//log("Created new keyframe. Frame: " + currentSlice + ", MOMP: " + momp +
		//		", Last: " + last);
		
		//this.keyframeLst.setModel(this.currentTrack);
		//this.keyframeListModel.addElement(kf);

		this.updateOverlay();
		//Overlay ol = currentTrack.getOverlay();
		//this.valueImg.setOverlay(ol);
		//if (!valueImg.equals(roiImg)) {
		//	this.roiImg.setOverlay(ol);
		//}
	}
	
	public void actionPerformed(ActionEvent e) {
		Object b = e.getSource();
		if (b == addNewTrack)
			addNewTrack();
		else if (b == addTrackWithLast)
			addTrackWithLast();
		else if (b == addKeyframe)
			addKeyframe();
		else if (b == allTimecourse)
			showAllTimecourses();
		else if (b == clearAllTimecourses)
			clearTracks();
		else if (b == showFrameNumber)
			showFrameNumberList();
		else if (b == this.roiImageCbx) {
			log("actionPerformed: roiImageCbx selection event. About to set ROI image.");
			ImagePlusWrapper ipw = (ImagePlusWrapper) this.roiImageCbx.getSelectedItem();
			this.setRoiImage(ipw.getImage());
		}
		else if (b == this.valueImageCbx) {
			log("actionPerformed: valueImageCbx selection event. About to set value image.");
			ImagePlusWrapper ipw = (ImagePlusWrapper) this.valueImageCbx.getSelectedItem();
			this.setValueImage(ipw.getImage());
		}
		else if (b == this.savedCellCbx) {
			log("actionPerformed: Track selection event.");
			CellTrack selectedTrack = (CellTrack) this.savedCellCbx.getSelectedItem();
			this.keyframeLst.setModel(selectedTrack);
			this.currentTrack = selectedTrack;
			
			this.updateOverlay();
			//Overlay ol = selectedTrack.getOverlay();
			//this.valueImg.setOverlay(ol);
			//if (!valueImg.equals(roiImg)) {
			//	this.roiImg.setOverlay(ol);
			//}
		}
		else if (b == this.loadControlBtn) {
			log("actionPerformed: about to call loadControl");
			loadControl();
		}

		else if (b == this.saveTracksBtn) {
			log("actionPerformed: about to call saveTracks");
			saveTracks();
		}
		else if (b == this.loadTracksBtn) {
			log("actionPerformed: about to call loadTracks");
			loadTracks();			
		}
		else if (b == this.deleteTrackBtn) {
			log("actionPerformed: about to call deleteTrack");
			deleteTrack();
		}
		else if (b == this.deleteKeyframeBtn) {
			log("actionPerformed: about to call deleteKeyframe");
			deleteKeyframe();
		}
		else if (b == this.updateImgListBtn) {
			log("actionPerformed: about to call getOpenImages");
			this.valueImageCbx.setModel(new DefaultComboBoxModel(getOpenImages()));
			this.valueImageCbx.setSelectedItem(new ImagePlusWrapper(this.valueImg));
			this.roiImageCbx.setModel(new DefaultComboBoxModel(getOpenImages()));
			this.roiImageCbx.setSelectedItem(new ImagePlusWrapper(this.roiImg));
		}
	}

	public void keyPressed(KeyEvent e) {
		// TODO Auto-generated method stub		
	}
	public void keyReleased(KeyEvent e) {
		// TODO Auto-generated method stub		
	}
	public void keyTyped(KeyEvent e) {
		if (e.getKeyChar() == 'k') {
			log("time to add keyframe!");
		}
		else {
			log("key pressed was " + e.getKeyChar());
		}
	}

	/**
	 * Updates the overlay on the value and Roi images, using the current track.
	 * Checks to see if we should show the ROIs for all keyframes or only the
	 * ROI for the active keyframe.
	 * 
	 * @param track The track containing the Keyframe ROIs to display
	 */
	private void updateOverlay() {
		if (this.currentTrack == null)
			return;

		Overlay ol;
		CellTrack track = this.currentTrack;
		
		//log("updateOverlay");
		
		if (this.overlayCbx.getSelectedItem().equals(SHOW_ACTIVE_FOR_SELECTED)) {
			//log("updateOverlay: this.roiImg.getCurrentSlice");
			ol = track.getOverlayForFrame(this.roiImg.getCurrentSlice());
		}
		else if (this.overlayCbx.getSelectedItem().equals(SHOW_ALL_FOR_SELECTED)) {
			ol = track.getOverlay();			
		}
		else if (this.overlayCbx.getSelectedItem().equals(SHOW_ACTIVE_FOR_ALL)) {
			ol = cellTracks.getAllActiveOverlays(this.roiImg.getCurrentSlice());
		}
		else {
			ol = cellTracks.getAllOverlays();
		}
		
		this.valueImg.setOverlay(ol);
		if (!valueImg.equals(roiImg)) {
			this.roiImg.setOverlay(ol);
		}
	}
	
	/**
	 * Deletes the currently selected CellTrack. If no tracks have been
	 * selected, then the list will contain only an instance of a String
	 * (saying "(none selected)") and not a CellTrack; in which case
	 * no track can be deleted.
	 */
	private void deleteTrack() {
		log("In deleteTrack");
		Object o = this.savedCellCbx.getSelectedItem();
		if (o != null && o instanceof CellTrack) {
			int size = this.cellTracks.getSize();	
			log("cellTracks size is " + size);
			
			if (size == 1) {
				try {
					clearTracks();
				} catch (Exception ex) {
					log(ex.getMessage());
				}
			}
			else {
				log("deleteTrack: about to delete track: " + o);
				CellTrack selectedTrack = (CellTrack) o;
				//log(cellTracks.debugString());
				//log(selectedTrack.debugString());
				this.cellTracks.removeElement(selectedTrack);
				//log("cellTracks debugString: ");
				//log(cellTracks.debugString());				
			}
		}
		else {
			log("Either the selectedItem is null, or it's not a CellTrack");
		}

	}
	
	/**
	 * Deletes the currently selected Keyframe. If there is no Keyframe
	 * selected, it can't delete. Also, if there is only one Keyframe
	 * in the currently selected CellTrack, it also can't delete (because
	 * every CellTrack must contain at least one Keyframe).
	 */
	private void deleteKeyframe() {
		Object o = this.savedCellCbx.getSelectedItem();
		if (o != null && o instanceof CellTrack) {
			CellTrack selectedTrack = (CellTrack) o;
			if (selectedTrack.getSize() > 1) {
				Keyframe kf = (Keyframe) this.keyframeLst.getSelectedValue();
				if (kf != null) {
					if (kf.getFrame() > 1) {
						log("deleteKeyframe: about to delete keyframe: " + kf);
						selectedTrack.deleteKeyframe(kf);
						this.keyframeLst.setModel(selectedTrack);
						
						this.updateOverlay();
						//Overlay ol = selectedTrack.getOverlay();
						//this.valueImg.setOverlay(ol);
						//if (!valueImg.equals(roiImg)) {
						//	this.roiImg.setOverlay(ol);			
						//}

					}
					else {
						log("Can't delete first Keyframe");
					}
				}
				else {
					log("No Keyframe selected, can't delete");
				}
			}
			else {
				log("Only 1 Keyframe for this track, can't delete");
			}
		}
		else {
			log("Either the selectedItem is null, or it's not a CellTrack");
		}
	}
	
	private void loadControl() {
		
		this.controlTimecourse = new double[numPoints];
		
		log("loadControl: in loadControl");
		
		try {
      int returnVal = fc.showOpenDialog(this);

      if (returnVal != JFileChooser.APPROVE_OPTION) {
        log("Load Control command cancelled by user.");
        return;
      }

      File file = fc.getSelectedFile();
      String filename = file.getAbsolutePath();
      log("loadControl: filename = " + filename);
      FileInputStream fis = new FileInputStream(filename);
      
      DataInputStream in = new DataInputStream(fis);
      BufferedReader br = new BufferedReader(new InputStreamReader(in));
      String line;      
      int counter = 0;
      while ((line = br.readLine()) != null) {
       	double d = Double.parseDouble(line);
       	if (counter < controlTimecourse.length) {
       		controlTimecourse[counter] = d;
       	}
       	else {
        	controlTimecourse = new double[numPoints];
        	throw new Exception("The number of points in the timecourse file " +
        			"doesn't match the number of points in the timecourse image!");   		
       	}
       	counter++;
      }
      controlFileLbl.setText("Control Timecourse File: " + file.getName());
      
      log("loadControl: Apparent success. First/last pts:");
      log(Double.toString(controlTimecourse[0]));
      log(Double.toString(controlTimecourse[controlTimecourse.length - 1]));
		} catch(Exception ex) {
			ex.printStackTrace();
			log("loadControl: Couldn't load control timecourse!");
			log("ex.getMessage: " + ex.getMessage());
			log("ex.getCause: " + ex.getCause());
		}
	}
	
	private void saveTracks() {
    int returnVal = fc.showSaveDialog(this);

    if (returnVal != JFileChooser.APPROVE_OPTION) {
      log("Save command cancelled by user.");
      return;
    }

    File file = fc.getSelectedFile();
    String filename = file.getAbsolutePath();

		FileOutputStream fos = null;
		ObjectOutputStream out = null;
		try {
			log("saveTracks: about to try to save");
			fos = new FileOutputStream(filename);
			log("saveTracks: got new FileOutputStream");
			out = new ObjectOutputStream(fos);
			log("saveTracks: got new ObjectOutputStream");

			// For some reason, serialization of CellTrackList doesn't work correctly
			// if the CellTrackList has been set to be the model for a JComboBox.
			// Attempts to serialize it result in a null pointer exception
			// from somewhere deep inside the Java AWT API.
			// As a workaround, this code creates a dummy (empty) CellTrackList
			// and sets it to be the model for the JComboBox containing the list
			// of saved tracks. It then saves the main cell track list and then
			// re-sets it as the model for the saved track list.
			CellTrackList dummyList = new CellTrackList();
			this.savedCellCbx.setModel(dummyList);
			log("Set cbx model to be an empty CellTrackList");
			log(cellTracks.debugString());			
			out.writeObject(cellTracks);
		  out.close();
			this.savedCellCbx.setModel(this.cellTracks);
			filenameLbl.setText("Tracks File: " + filename);
		  log("saveTracks: completed save.");
		} catch(IOException ex) {
			ex.printStackTrace();
			log("Error Saving Tracks: ex.Message: " + ex.getMessage());
			log("ex.getCause: " + ex.getCause());
			StackTraceElement[] stack = ex.getStackTrace();
			log("ex.stackTrace: ");
			for (int i = 0; i < stack.length; i++) {
				log(stack[i].toString());
			}
			IJ.showMessage("Error Saving Tracks: " + ex.getMessage());
		}
	}
	
	private void loadTracks() {
		log("loadTracks: in loadTracks");
		FileInputStream fis = null;
		ObjectInputStream in = null;
		try {
      int returnVal = fc.showOpenDialog(this);

      if (returnVal != JFileChooser.APPROVE_OPTION) {
        log("Open command cancelled by user.");
        return;
      }

      File file = fc.getSelectedFile();
      String filename = file.getAbsolutePath();
      //String filename = this.text.getText();
			fis = new FileInputStream(filename);
			log("loadTracks: opened FileInputStream");
			in = new ObjectInputStream(fis);
			log("loadTracks: created ObjectInputStream");
			CellTrackList loadedTracks = (CellTrackList) in.readObject();
			log("loadTracks: read in CellTrackList object");
			in.close();

			CellTrack track = (CellTrack) loadedTracks.getElementAt(0);
			log("loadTracks: imageTitle of first track: " + track.getImageTitle());
			
			String savedTitle = track.getImageTitle();
			
			if (!track.getImageTitle().equals(this.valueImg.getTitle())) {
				String warning = "Warning: The current timecourse image (" +
						this.valueImg.getTitle() + ") \ndoes not match the image for the " +
						"loaded tracks (" + savedTitle + ")";
				
				IJ.showMessage("Current image does not match saved image", warning);
				log(warning);				
			}
			
			this.cellTracks = loadedTracks;
			this.cellTracks.setValueImageForTracks(this.valueImg);
			log("loadTracks: set the value image for the tracks");
			this.savedCellCbx.setModel(this.cellTracks);
			this.savedCellCbx.setSelectedIndex(0);
			filenameLbl.setText("Tracks File: " + file.getName());
			
		} catch(IOException ex) {
			ex.printStackTrace();
			log("loadTracks: Couldn't load tracks! IOException");
			log("ex.getMessage: " + ex.getMessage());
			log("ex.getCause: " + ex.getCause());
		} catch(ClassNotFoundException ex) {
			ex.printStackTrace();
			log("loadTracks: Couldn't load tracks! ClassNotFoundException");
			log("ex.getMessage: " + ex.getMessage());
			log("ex.getCause: " + ex.getCause());
		}
	}
	
	public void lostOwnership(Clipboard clipboard, Transferable contents) {
	}

	// These next three methods are probably implemented elsewhere and could
	// rewritten to use standard utility methods
	public Color getColor(double val) {
		float R = (float) (min(max((4d * (val - 0.25)), 0), 1d));
		float G = (float) (min(max((4d * Math.abs(val - 0.5d) - 1d), 0d), 1d));
		float B = (float) (min((max((4d * (0.75 - val)), 0)), 1d));

		return new Color(R, G, B);
	}

	static public double min(double v1, double v2) {
		if (v1 > v2)
			return v2;
		return v1;
	}

	static public double max(double v1, double v2) {
		if (v1 < v2)
			return v2;
		return v1;
	}
	// end three methods

	/**
	 * Low-pass filter the data by simply doing a 4-point running average
	 * 
	 * @author JohnBachman
	 */
	private double[] lowPassFilter(double[] data) {
		int filterLen = 4;
		int len = data.length;
		double[] avgData = new double[len];
		double windowTotal;
		double windowAverage;
		
		for (int i = 0; i < len; i++) {
			if (i < filterLen - 1) {
				windowTotal = 0;
				for (int j = 0; j <= i; j++) {
					windowTotal += data[j];
				}
				windowAverage = windowTotal / ((double) (i + 1));
				avgData[i] = windowAverage;
			}
			else {
				windowTotal = 0;
				for (int j = (i - filterLen + 1); j <= i; j++) {
					windowTotal += data[j];
				}
				windowAverage = windowTotal / ((double) filterLen);
				avgData[i] = windowAverage;
			}			
		}
		return avgData;
	} // end lowPassFilter
	
	public static void log(String s) {
		if (logging) IJ.log(s);
	}
	
	public static void logloop(String s) {
		if (loopLogging) IJ.log(s);
	}

} // end ImagePlotter
