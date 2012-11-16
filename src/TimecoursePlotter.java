import java.awt.event.WindowEvent;

import ij.gui.ImageWindow;
import ij.*;
import ij.gui.ImageWindow;
import ij.gui.NewImage;

public class TimecoursePlotter extends ImageWindow implements Runnable {
	static final int WIN_WIDTH = 800;
	static final int WIN_HEIGHT = 240;
	static final int PLOT_WIDTH = 740;
	static final int PLOT_HEIGHT = 180;
	static final int XMARGIN = 20;
	static final int YMARGIN = 20;
	
  /** The thread for the showPlot() update loop. */
  protected Thread thread;

  /** Boolean flag to stop the update loop when the plugin is closed. */
  protected boolean done;

	/** The plot refresh interval, in milliseconds */
	static final int REFRESH_INTERVAL = 100;

	/** Logging switches */
	private static boolean logging = true;
	private static boolean loopLogging = true;
	
	/** The instance of the associated Movie Explorer Plugin */
	Movie_Explorer_2 me2;

	public TimecoursePlotter(Movie_Explorer_2 me2) {
		// Call the constructor for ImageWindow
		super(NewImage.createRGBImage("TimecoursePlotter2", WIN_WIDTH, WIN_HEIGHT, 1,
		  		NewImage.FILL_WHITE));

		this.me2 = me2;

		thread = new Thread(this, "TimecoursePlotter2");		
		thread.start();		// Start running showPlot()
	}
	
	/**
	 * The main run loop, refreshes the trajectory plot every 200ms.
	 */
	public void run() {
		while (!done) {
			try {
				Thread.sleep(REFRESH_INTERVAL);
			} catch (InterruptedException e) {
			}
			//showPlot();
			logloop("Logging");
		}
	}
	
	/** 
	 * Overrides the method in the ImageWindow parent class, adding
	 * a call to shutDown() to terminate the run loop.
	 */
	public void windowClosing(WindowEvent e) {
		super.windowClosing(e);
		shutDown();
	}

	/**
	 * Sets a boolean flag, done, which allows the run() loop to terminate.
	 */
	public void shutDown() {
		done = true;
	}

	public static void log(String s) {
		if (logging) IJ.log(s);
	}
	public static void logloop(String s) {
		if (loopLogging) IJ.log(s);
	}
}
