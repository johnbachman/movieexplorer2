import java.awt.*;
import java.awt.image.*;
import java.awt.event.*;
import java.io.*;
import javax.swing.JCheckBox;
import java.awt.datatransfer.*;
import ij.*;
import ij.process.*;
import ij.measure.*;
import ij.plugin.filter.Analyzer;
import ij.plugin.filter.Filler;
import ij.plugin.filter.PlugInFilter;
import ij.text.TextWindow;
import ij.gui.*;
import java.util.ArrayList;

/** Driver for RatioExplorer. */

public class Movie_Explorer_new implements PlugInFilter
{
	ImagePlus imq;
	
    public int setup(String arg, ImagePlus imq)
	{
		this.imq = imq;
		return DOES_ALL;
    }
	
    public void run(ImageProcessor ip)
	{
		if (IJ.versionLessThan("1.28"))
			return;
		ImagePlotter fp = new ImagePlotter("Ratio Explorer",imq);
		IJ.showStatus("run RE");
	}
}



class ImagePlotter extends ImageWindow implements Measurements, ActionListener, FocusListener,ClipboardOwner,Runnable
{
	static final int WIN_WIDTH = 800;
	static final int WIN_HEIGHT = 240;
	static final int PLOT_WIDTH = 740;
	static final int PLOT_HEIGHT = 180;
	static final int XMARGIN = 20;
	static final int YMARGIN = 20;
	
	static final Color Gray_1 = new Color(0.7f, 0.7f,0.7f);
	protected Rectangle frame = null;
	protected Button  addRegion, allTimecourse, clearAllTimecourses, showFrameNumber;
	protected TextField threshold1TF, threshold2TF, subtractedSlopeTF;
    protected Label threshold1Label, threshold2Label, subtractedSlopeLabel;
	protected JCheckBox DrawLinesCheckBox;
	protected JCheckBox DrawPointsCheckBox;
	protected JCheckBox PlotAllTimecoursesCheckBox;
	protected JCheckBox LowPassFilterCheckBox;
	private GridBagLayout grid;
	private GridBagConstraints c;
	protected Label value, count, cfpImageLabel, yfpImageLabel, ratioMethodLabel;
	protected static String defaultDirectory = null;
	protected int decimalPlaces;
	protected int digits;
	protected Calibration cal;
	public int numBins;
	
	protected Thread thread;
	protected boolean done;
	protected boolean color =  true;
    protected boolean isDead;
	
	protected int numPoints;
	protected int timestep;
	protected double[]ValueList;
	protected double[]  ValueListNorm;
	protected double[] valuesToPlot;
    protected double[][]  ratioListBlock;
    protected int blockIndex;
    protected final int blockSize=100;
	
	
	protected int threshold1Frame;
	protected int ratio50Frame;
	protected int threshold2Frame;
    protected int threshold1;
    protected int threshold2;
    protected double subtractedSlope;
	
	protected double MaxValue;
	protected double MinValue;
    protected double slope50;
    
    ImageProcessor TheImageProcessor;
    ImagePlus TheTempIP;
    ImageProcessor TheImageProcessor2;
    ImageStack TheStack;
    CustomCanvas TheCanvasOverlay;
	int TheCurrentSlice = 1;
	ImagePlus TheImage;
	int[] wList;
	String[] titles;
	
	double BlockMinVal = 1000000;
	double BlockMaxVal = -100000;
	ArrayList FrameNumberList;
    
    
	/** Displays a plot using the specified title. */
	public ImagePlotter(String title, ImagePlus id)
	{
		super(NewImage.createRGBImage(title, WIN_WIDTH, WIN_HEIGHT, 1, NewImage.FILL_WHITE));
		thread = new Thread(this, "Movie_Explorer");
		setup();
		thread.start();
	}
	
	
	public void showPlot()
	{
		
		TheCurrentSlice = TheImage.getCurrentSlice();
		TheStack=TheImage.getStack();
		ValueList=new double[numPoints];
		ValueListNorm=new double[numPoints];
		valuesToPlot = new double[numPoints];
		timestep=(int)(PLOT_WIDTH/numPoints);
		
		try
		{
			for(int i=0; i<numPoints; i++)
			{
				TheImageProcessor = TheStack.getProcessor(i+1);
				Rectangle roi=TheStack.getRoi();
				
				double value=0.0;
				int backgroundPixels=0;
				
				
				for (int y=roi.y; y<roi.y+roi.height; y++) { //Loop for Y-Values of roi
					for (int x=roi.x; x<roi.x+roi.width; x++) { //Loop for X-Values of roi
						if (TheImageProcessor.getPixelValue(x,y)<=0)
							backgroundPixels++;
						else
							value+=TheImageProcessor.getPixelValue(x,y);           // calculate running total of the  ratio inside roi
					}
				}
				
				if (roi.height*roi.width-backgroundPixels<=0)
					value= 0;
				else
					value=value/(roi.height*roi.width-backgroundPixels);
				
				if (value<0)
					value = 0;
				else if (value>100000)
					value = 1;
				
				
//				IJ.showMessage(""+roi.width+"  "+roi.height);
				ValueList[i]=value;
				
			}
			
			
			MaxValue=Double.NEGATIVE_INFINITY;
			MinValue=Double.POSITIVE_INFINITY;
			
			/* find the minimum and maximum ratio values */
			for (int i=0; i<numPoints;i++)
			{
				if (ValueList[i]>MaxValue)
					MaxValue=ValueList[i];
				if (ValueList[i]<MinValue)
					MinValue=ValueList[i];
			}
//			IJ.showStatus(""+(MinValue ));
			
			/* create a list of normalized ratio values */
			for (int i=0; i<numPoints;i++)
			{
				if (MaxValue-MinValue > 0)
					ValueListNorm[i]= (ValueList[i]-MinValue)/(MaxValue-MinValue);
				else
					ValueListNorm[i]=0;
			}
			
			TheImageProcessor2 = imp.getProcessor();
			
			if(TheImageProcessor2 == null)
			{
				shutDown();
				return;
			}
			
			drawPlot(TheImageProcessor2);
			this.imp.setTitle("Movie Explorer");
			this.imp.updateAndDraw();
			
		}
		catch (java.lang.NullPointerException e)
		{
			this.imp.setTitle("Movie Explorer :exception");
		}
		catch (java.lang.IllegalArgumentException ea)
		{
			clear(this.imp.getProcessor());
		}
		catch (java.lang.ArrayIndexOutOfBoundsException eo)
		{
			clear(this.imp.getProcessor());
		}
	}
	
	public void run()
	{
		while (!done)
		{
			try {Thread.sleep(100);}
			catch(InterruptedException e) {}
			showPlot();
		}
	}
	
	public void windowClosing(WindowEvent e)
	{
		super.windowClosing(e);
		shutDown();
	}
	
	public void shutDown()
	{
		done = true;
	}
	
	public void setup()
	{
		FrameNumberList = new ArrayList();
		
		//
		//
		//modified by Bjorn Millard 6/12/07 to allow user to choose which window to explore
		if (IJ.versionLessThan("1.27w"))
			return;
		wList = WindowManager.getIDList();
		titles = new String[wList.length];
		if (wList==null || wList.length<1)
		{
			IJ.showMessage(titles[0], "You need at least one image open...");
			return;
		}
		for (int i=0; i<wList.length; i++)
		{
			ImagePlus imp = WindowManager.getImage(wList[i]);
			if (imp!=null)
				titles[i] = imp.getTitle();
			else
				titles[i] = "";
		}
		// Get user input
		if (!showDialog())
			return;
		
		// Let the user know that something is happening...
		IJ.showStatus("A moment please...");
		
		TheImage = TheImage;
		if (TheImage==null)
		{IJ.showMessage("Movie Explorer", "Out of memory"); return;}
		//
		//
		//
		
		TheCanvasOverlay=new CustomCanvas(TheImage);
		new StackWindow(TheImage, TheCanvasOverlay);
		
		TheStack=TheImage.getStack();
		numPoints=TheStack.getSize();
		
		ratioListBlock=new double[numPoints][blockSize];
		blockIndex=0;
		
		Panel buttons=new Panel();
		buttons.setLayout(new GridLayout(1,2));
		addRegion = new Button("Add Timecourse");
		addRegion.addActionListener(this);
		buttons.add(addRegion);
		allTimecourse=new Button("Get All Timecourses");
		allTimecourse.addActionListener(this);
		buttons.add(allTimecourse);
		add(buttons);
		clearAllTimecourses=new Button("Clear All Timecourses");
		clearAllTimecourses.addActionListener(this);
		buttons.add(clearAllTimecourses);
		add(buttons);
		
		showFrameNumber=new Button("Show Frame Numbers");
		showFrameNumber.addActionListener(this);
		buttons.add(showFrameNumber);
		add(buttons);
		
		PlotAllTimecoursesCheckBox=new JCheckBox("Plot All Timecourses");
		PlotAllTimecoursesCheckBox.setSelected(false);
		add(PlotAllTimecoursesCheckBox);
		
		
		DrawLinesCheckBox=new JCheckBox("Draw Lines");
		DrawLinesCheckBox.setSelected(true);
		add(DrawLinesCheckBox);
		
		DrawPointsCheckBox=new JCheckBox("Draw Points");
		DrawPointsCheckBox.setSelected(true);
		add(DrawPointsCheckBox);
		
		LowPassFilterCheckBox=new JCheckBox("Low-Pass Filter");
		LowPassFilterCheckBox.setSelected(false);
		add(LowPassFilterCheckBox);
		
		pack();
		
	}
	
	protected void drawPlot(ImageProcessor ip)
	{
		
		clear(ip);
		
		int x, y;
		if(ip == null)
		{
			shutDown();
			return;
		}
		ip.setColor(Color.black);
		ip.setLineWidth(1);
		decimalPlaces = Analyzer.getPrecision();
		
		
		
		drawCurves(ip);
		
		drawText(ip);
		
	}
	
	void clear(ImageProcessor ip)
	{
		if(ip == null)return;
		if(color)
		{
			int white = 0xffffff;
			int[] pixels = (int[])ip.getPixels();
			int n = pixels.length;
			for (int i = 0; i < n; i++) pixels[i] = white;
		}
		else
		{
			byte white = (byte)255;
			byte[] pixels = (byte[])ip.getPixels();
			int n = pixels.length;
			for (int i = 0; i < n; i++) pixels[i] = white;
		}
	}
	
	
	void drawCurves(ImageProcessor ip)
	{
		frame = new Rectangle(XMARGIN, YMARGIN, PLOT_WIDTH, PLOT_HEIGHT);
		ip.drawRect(frame.x-1, frame.y, frame.width+2, frame.height+1);
		
		ip.setColor(Color.gray);
		ip.drawLine(XMARGIN,(int)(PLOT_HEIGHT*0.5)+YMARGIN,XMARGIN+PLOT_WIDTH,(int)(PLOT_HEIGHT*0.5)+YMARGIN);
		
		//lowpass filter
		
		//should we plot all saved timecourses?
		if (PlotAllTimecoursesCheckBox.isSelected()&&blockIndex>0)
		{
			for(int b=0;b<blockIndex;b++)
			{
				for (int i = 0; i < numPoints; i++)
					valuesToPlot[i] = ratioListBlock[i][b];
				
				if (LowPassFilterCheckBox.isSelected())
					valuesToPlot = lowPassFilter(valuesToPlot);
				
				for (int i = 3 ; i<(numPoints-1); i++)
				{
					float lastVal = (float)((valuesToPlot[i-1]-BlockMinVal)/(BlockMaxVal-BlockMinVal));
					float thisVal = (float)((valuesToPlot[i]-BlockMinVal)/(BlockMaxVal-BlockMinVal));
					if (DrawPointsCheckBox.isSelected())
					{
						
						ip.setColor(getColor(thisVal));
						//starts at 2 for lines and want to plot point 1 also
						if (i==2)
							ip.fillOval(1*timestep+XMARGIN-2, PLOT_HEIGHT-(int)(valuesToPlot[1]*PLOT_HEIGHT)+YMARGIN-2,4,4);
						ip.fillOval(i*timestep+XMARGIN-2, PLOT_HEIGHT-(int)(thisVal*PLOT_HEIGHT)+YMARGIN-2,4,4);
					}
					
					if (DrawLinesCheckBox.isSelected())
					{
						
						ip.setColor(getColor(thisVal));
						int x1 = (i-1)*timestep+XMARGIN;
						int x2 = i*timestep+XMARGIN;
						int y1 = PLOT_HEIGHT-(int)(lastVal*PLOT_HEIGHT)+YMARGIN;
						int y2 = PLOT_HEIGHT-(int)(thisVal*PLOT_HEIGHT)+YMARGIN;
						ip.drawLine(x1,y1 ,x2, y2);
					}
				}
			}
		}
		else //only plot the current timecourse
		{
			valuesToPlot = ValueListNorm;
			if (LowPassFilterCheckBox.isSelected())
				valuesToPlot = lowPassFilter(ValueListNorm);
			
			for (int i = 3; i<(numPoints-1); i++)
			{
				if (DrawPointsCheckBox.isSelected())
				{
					ip.setColor(getColor(valuesToPlot[i]));
					//starts at 2 for lines and want to plot point 1 also
					if (i==2)
						ip.fillOval(1*timestep+XMARGIN-2, PLOT_HEIGHT-(int)(valuesToPlot[1]*PLOT_HEIGHT)+YMARGIN-2,4,4);
					ip.fillOval(i*timestep+XMARGIN-2, PLOT_HEIGHT-(int)(valuesToPlot[i]*PLOT_HEIGHT)+YMARGIN-2,4,4);
				}
				
				if (DrawLinesCheckBox.isSelected())
				{
					ip.setColor(getColor(valuesToPlot[i]));
					int x1 = (i-1)*timestep+XMARGIN;
					int x2 = i*timestep+XMARGIN;
					int y1 = PLOT_HEIGHT-(int)(valuesToPlot[i-1]*PLOT_HEIGHT)+YMARGIN;
					int y2 = PLOT_HEIGHT-(int)(valuesToPlot[i]*PLOT_HEIGHT)+YMARGIN;
					ip.drawLine(x1,y1 ,x2, y2);
				}
			}
		}
		
		//drawing the current slice line
		ip.setColor(Gray_1);
		ip.drawLine(TheCurrentSlice*timestep+XMARGIN,YMARGIN+1,TheCurrentSlice*timestep+XMARGIN,(PLOT_HEIGHT+YMARGIN-1));
		ip.setColor(Color.black);
		
	}
	
	
	void drawText(ImageProcessor ip)
	{
		ip.setFont(new Font("SansSerif",Font.PLAIN,12));
		
		int col1 = XMARGIN + 5;
		int col2 = XMARGIN + PLOT_WIDTH/2;
		int row1 = YMARGIN+PLOT_HEIGHT+50;
		int row2 = row1 + 15;
		
		
		ip.setFont(new Font("SansSerif",Font.PLAIN,10));
		ip.drawString(d2s(0), XMARGIN, YMARGIN+PLOT_HEIGHT+15);
		ip.drawString(d2s(numPoints), XMARGIN + numPoints*timestep, YMARGIN+PLOT_HEIGHT+15);
		ip.drawString("Frame", XMARGIN+numPoints*timestep/2-10, YMARGIN+PLOT_HEIGHT+25);
		
		ip.drawString(d2s(MinValue),XMARGIN + PLOT_WIDTH + 2, YMARGIN+PLOT_HEIGHT+8);
		ip.drawString(d2s(MaxValue),XMARGIN + PLOT_WIDTH + 2, YMARGIN+8);
		
	}
	
	String d2s(double d)
	{
		if ((int)d==d)
			return IJ.d2s(d,0);
		else
			return IJ.d2s(d,decimalPlaces);
	}
	
	int getWidth(double d, ImageProcessor ip)
	{
		return ip.getStringWidth(d2s(d));
	}
	
	void showFrameNumberList()
	{
		StringBuffer sb = new StringBuffer();
		for (int i=0; i< blockIndex; i++)
		{
			int val = ((Integer)FrameNumberList.get(i)).intValue();
			sb.append(d2s(val)+"\t");
			sb.append("\n");
		}
		
		StringBuffer headings = new StringBuffer();
		headings.append( "Frame" +"\t");
		
		TextWindow tw = new TextWindow(getTitle(), headings.toString(), sb.toString(), 200, 400);
		
	}
	
	void showRawBlock()
	{
		StringBuffer sb = new StringBuffer();
		for (int i=0; i< numPoints; i++)
		{
			for (int j=0; j<blockSize; j++)
			{
				sb.append(d2s(ratioListBlock[i][j])+"\t");
			}
			sb.append("\n");
		}
		
		
		StringBuffer headings = new StringBuffer();
		for (int k=0; k<blockSize; k++)
		{
			headings.append( "Cell " + d2s(k+1)+"\t");
		}
		
		TextWindow tw = new TextWindow(getTitle(), headings.toString(), sb.toString(), 200, 400);
		
	}
	
	void resetRawBlock()
	{
		ratioListBlock = new double[numPoints][blockSize];
		blockIndex = 0;
		FrameNumberList = new ArrayList();
	}
	
	
	/* adds the current t50 and slope 50 to the list of t50s and slope50s */
	void addT50()
	{
		if(blockIndex<blockSize)
		{
			for(int i=0;i<numPoints;i++)
			{
				if (ValueList[i]<BlockMinVal)
					BlockMinVal = ValueList[i];
				if (ValueList[i]>BlockMaxVal)
					BlockMaxVal = ValueList[i];
				
				ratioListBlock[i][blockIndex]=ValueList[i];
			}
			
			blockIndex++;
		}
		
		//remembering which frame was being viewed when selecting the roi (for Debbie)
		FrameNumberList.add(new Integer(TheCurrentSlice));
		
		Rectangle roi=TheStack.getRoi();
		TheCanvasOverlay.addPosition(roi.x, roi.y, roi.height, roi.width);
	}
	
	public void actionPerformed(ActionEvent e)
	{
		Object b = e.getSource();
		if (b==addRegion)
			addT50();
		else if (b==allTimecourse)
			showRawBlock();
		else if (b==clearAllTimecourses)
			resetRawBlock();
		else if (b==showFrameNumber)
			showFrameNumberList();
	}
	
	public void focusGained(FocusEvent e)
	{
		Component c = e.getComponent();
		if (c instanceof TextField)
				((TextField)c).selectAll();
	}
	
	public void focusLost(FocusEvent e)
	{
		Component c = e.getComponent();
		if (c instanceof TextField)
		{
			handleText((TextField)c);
		}
	}
	
	protected void handleText(TextField b)
	{
		if (b==threshold1TF)
		{
			threshold1 = readNumber(threshold1TF);
			threshold1TF.setText(Integer.toString(threshold1));
			showPlot();
		}
		else if (b==threshold2TF)
		{
			threshold2 = readNumber(threshold2TF);
			threshold2TF.setText(Integer.toString(threshold2));
			showPlot();
		}
		else if (b==subtractedSlopeTF)
		{
			subtractedSlope=readDouble(subtractedSlopeTF);
			subtractedSlopeTF.setText(d2s(subtractedSlope));
			showPlot();
		}
	}
	
	public int readNumber(TextField tf)
	{
		String s = tf.getText();
		int result;
		try
		{
			Integer Int = new Integer(s.trim());
			result = Int.intValue();
		}
		catch (NumberFormatException e)
		{
			result = -1;
		}
		return result;
	}
	
	public double readDouble(TextField tf)
	{
		String s = tf.getText();
		double result;
		try
		{
			Double d = new Double(s.trim());
			result = d.doubleValue();
		}
		catch (NumberFormatException e)
		{
			result = -1;
		}
		return result;
	}
	
	public void lostOwnership(Clipboard clipboard, Transferable contents) {}
	
	
	
	class CustomCanvas extends ImageCanvas
	{
		int[]xPosition;
		int[]yPosition;
		int[]height;
		int[]width;
		boolean[]dead;
		static final int number=200;
		int index;
		
		CustomCanvas(ImagePlus imp)
		{
			super(imp);
			xPosition=new int[number];
			yPosition=new int[number];
			height=new int[number];
			width=new int[number];
			dead=new boolean[number];
			index=0;
		}
		
		public void paint(Graphics g)
		{
			super.paint(g);
			drawOverlay(g);
		}
		
		public void addPosition(int x, int y, int h, int w)//, boolean d)
		{
			xPosition[index]=x;
			yPosition[index]=y;
			height[index]=h;
			width[index]=w;
//			dead[index]=d;
			index++;
			repaint();
		}
		
		void drawOverlay(Graphics g)
		{
			for(int i=0; i<number; i++)
			{
				g.setColor(Color.green);
				g.drawRect(xPosition[i],yPosition[i],width[i],height[i]);
				g.drawString(Integer.toString(i+1), xPosition[i]+2,yPosition[i]+10);
			}
		}
	}//Custom Canvas
	
	
	public boolean showDialog()
	{
		GenericDialog gd = new GenericDialog("Select Image");
		gd.addChoice("Image1 (or Stack1):", titles, titles[0]);
		gd.showDialog();
		if (gd.wasCanceled())
			return false;
		
		int i1Index = gd.getNextChoiceIndex();
		TheImage = WindowManager.getImage(wList[i1Index]);
		
		return true;
	}
	
	ImagePlus duplicateImage(ImagePlus img1)
	{
		ImageStack stack1 = img1.getStack();
		int width = stack1.getWidth();
		int height = stack1.getHeight();
		int n = stack1.getSize();
		ImageStack stack2 = img1.createEmptyStack();
		try
		{
			for (int i=1; i<=n; i++)
			{
				ImageProcessor ip1 = stack1.getProcessor(i);
				ImageProcessor ip2 = ip1.duplicate();
				ip2 = ip2.convertToFloat();
				stack2.addSlice(stack1.getSliceLabel(i), ip2);
			}
		}
		catch(OutOfMemoryError e)
		{
			stack2.trim();
			stack2 = null;
			return null;
		}
		ImagePlus img2 =  new ImagePlus("Ratio", stack2);
		return img2;
	}
	
	
	ImagePlus replicateImage(ImagePlus img1, int n)
	{
		ImageProcessor ip1 = img1.getProcessor();
		int width = ip1.getWidth();
		int height = ip1.getHeight();
		ImageStack stack2 = img1.createEmptyStack();
		try
		{
			for (int i=1; i<=n; i++)
			{
				ImageProcessor ip2 = ip1.duplicate();
				ip2 = ip2.convertToFloat();
				stack2.addSlice(null, ip2);
			}
		}
		catch(OutOfMemoryError e)
		{
			stack2.trim();
			stack2 = null;
			return null;
		}
		ImagePlus img2 =  new ImagePlus("Ratio", stack2);
		return img2;
	}
	
	
	public Color getColor(double val)
	{
		float R  = (float)(min(max((4d*(val-0.25)), 0), 1d));
		float G= (float)(min(max((4d*Math.abs(val-0.5d)-1d), 0d), 1d));
		float B = (float)(min((max((4d*(0.75-val)), 0)), 1d));
		
		return new Color(R,G,B);
	}
	
	
	static public double min(double v1, double v2)
	{
		if (v1>v2)
			return v2;
		return v1;
	}
	static public double max(double v1, double v2)
	{
		if (v1<v2)
			return v2;
		return v1;
	}
	
	/** Low-pass filter the data by simply doing a 5-point running average
	 * @author BLM*/
	private double[] lowPassFilter(double[] data)
	{
		int filterLen = 4;
		int len = data.length;
		double[] avgData = new double[len];
		double val =0;
		for (int i = 0; i < len; i++)
		{
			if (i<(int)(filterLen/2f))
			{
//				for (int j = i; j < (int)(i+filterLen); j++)
//					val+=data[j];
//				val=val/(filterLen);
//				avgData[i] = val;
			}
			else if(i > len-(int)(filterLen/2f))
			{
//				for (int j = i; j < len; j++)
//					val+=data[j-i];
//				val=val/(filterLen);
//				avgData[i] = val;
			}
			else
			{
				for (int j = (i-(int)(filterLen/2f)); j < (i+(int)(filterLen/2f)); j++)
					val+=data[j];
				
				val=val/(1+filterLen);
				avgData[i] = val;
			}
			
		}
		
		return avgData;
	}
	
}




