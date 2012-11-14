import java.awt.*;
import java.awt.image.*;
import java.awt.event.*;
import java.io.*;
import java.awt.datatransfer.*;
import ij.*;
import ij.process.*;
import ij.measure.*;
import ij.plugin.filter.Analyzer;
import ij.plugin.filter.Filler;
import ij.plugin.filter.PlugInFilter;
import ij.text.TextWindow;
import ij.gui.*;

/** Driver for RatioExplorer. */

public class Ratio_Mito_Explorer implements PlugInFilter {
	ImagePlus imq;

    public int setup(String arg, ImagePlus imq) {
    	this.imq = imq;
        return DOES_ALL;
    }

    public void run(ImageProcessor ip) {
 		if (IJ.versionLessThan("1.28"))
			return;
        RatioPlot fp = new RatioPlot("Ratio Explorer",imq);
        IJ.showStatus("run RE");
 	}
}



class RatioPlot extends ImageWindow implements Measurements, ActionListener, FocusListener,ClipboardOwner,
				Runnable{
	static final int WIN_WIDTH = 600;
	static final int WIN_HEIGHT = 240;
	static final int PLOT_WIDTH = 540;
	static final int PLOT_HEIGHT = 128;
	static final int XMARGIN = 20;
	static final int YMARGIN = 20;

	protected Rectangle frame = null;
	protected Button timecourse, addt50, list50, allTimecourse;
	protected TextField threshold1TF, threshold2TF, subtractedSlopeTF;
    protected Label threshold1Label, threshold2Label, subtractedSlopeLabel;
	protected Checkbox deadCheckBox;
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
	protected boolean color;
    protected boolean isDead;

	protected int numPoints;
	protected int timestep; 
	protected double[]ratioList;
    protected int[]t50list;
    protected double[]slope50list;
    protected int[][]t50posList;
    protected int t50index, slope50index;
	protected final int t50listLength=300;
	protected double[]  ratioListNorm;
    protected double[][]  ratioListBlock;
    protected int blockIndex;
    protected final int blockSize=100;
	protected ImagePlus ratioImage;
    protected ImagePlus mitoImage;

	protected int threshold1Frame;
	protected int ratio50Frame;
	protected int threshold2Frame;
    protected int threshold1;
    protected int threshold2;
    protected double subtractedSlope;

	protected double ratioMaxValue;
	protected double ratioMinValue;
    protected double slope50;
    
    ImageProcessor ratioIP;
    ImagePlus tempIP;
    ImageProcessor ip;
    ImageStack ratioStack;
    CustomCanvas overlay;
    CustomCanvas overlayM;
    
    
	/** Displays a plot using the specified title. */
	public RatioPlot(String title, ImagePlus id) {
		super(NewImage.createByteImage(title, WIN_WIDTH, WIN_HEIGHT, 1, NewImage.FILL_WHITE));
		thread = new Thread(this, "Ratio_Mito_Explorer");
		setup();
		thread.start();
	}


	public void showPlot() {
        ratioStack=ratioImage.getStack();
		ratioList=new double[numPoints];
		ratioListNorm=new double[numPoints];
		timestep=(int)(PLOT_WIDTH/numPoints);

		try{            
            for(int i=0; i<numPoints; i++) {		
				ratioIP = ratioStack.getProcessor(i+1);
				Rectangle roi=ratioStack.getRoi();

                double ratio=0.0;
                int backgroundPixels=0;
                

                for (int y=roi.y; y<roi.y+roi.height; y++) { //Loop for Y-Values of roi
                    for (int x=roi.x; x<roi.x+roi.width; x++) { //Loop for X-Values of roi
                        if (ratioIP.getPixelValue(x,y)==0)
                            backgroundPixels++;
                        else
                            ratio= ratio+ratioIP.getPixelValue(x,y);           // calculate running total of the  ratio inside roi            
                    }
                }

                ratio=ratio/(roi.height*roi.width-backgroundPixels);

                ratioList[i]=ratio;   

            }


		 	ratioMaxValue=0;
			ratioMinValue=100000;

            /* find the minimum and maximum ratio values */
			for (int i=0; i<numPoints;i++){
				if (ratioList[i]>ratioMaxValue){
					ratioMaxValue=ratioList[i];
				}

				if (ratioList[i]<ratioMinValue){
					ratioMinValue=ratioList[i];
				}
			}
		
            /* create a list of normalized ratio values */
			for (int i=0; i<numPoints;i++){
				if (ratioMaxValue-ratioMinValue > 0)
					ratioListNorm[i]= (ratioList[i]-ratioMinValue-i*subtractedSlope)/(ratioMaxValue-ratioMinValue);
				else
					ratioListNorm[i]=0;
			}
		
			boolean foundThreshold1=false;
			boolean found50=false;
			boolean foundThreshold2=false;

            /* find the time points in the list at which the normalized ratio first crosses certain values */
			for (int i=0; i<numPoints; i++){
				if (!foundThreshold1 && ratioListNorm[i] >= ((double)threshold1/100)){
					threshold1Frame=i;
					foundThreshold1=true;
				}
				if (!found50 && ratioListNorm[i] >= 0.5){
					ratio50Frame=i;
					found50=true;
				}
				if (!foundThreshold2 && ratioListNorm[i] >= ((double)threshold2/100)){
					threshold2Frame=i;
					foundThreshold2=true;
				}
			}

            /* calculate the slope of the normalized time course at the point where it crosses 0.5  */
            if(foundThreshold1==true && foundThreshold2==true){
                slope50=(ratioListNorm[threshold2Frame]-ratioListNorm[threshold1Frame])/(threshold2Frame-threshold1Frame);
            }
            else{
                slope50=0;
            }
            
         	ip = imp.getProcessor();
			
            if(ip == null){
				shutDown();
				return;
			}
            
			drawPlot(ip);
			this.imp.setTitle("Ratio Explorer");      
			this.imp.updateAndDraw();			
            
		} catch (java.lang.NullPointerException e) {
			this.imp.setTitle("FRET Explorer :exception");
			//shutDown();
			//clear(this.imp.getProcessor());
		} catch (java.lang.IllegalArgumentException ea){
			clear(this.imp.getProcessor());
		} catch (java.lang.ArrayIndexOutOfBoundsException eo){
			clear(this.imp.getProcessor());
		}
	}

	public void run() {
		while (!done) {
			try {Thread.sleep(100);}
			catch(InterruptedException e) {}
			showPlot();
		}
	}

    public void windowClosing(WindowEvent e) {
		super.windowClosing(e);
		shutDown();
	}

	public void shutDown(){
		done = true;
	}

	public void setup() {

        t50list=new int[t50listLength];
        slope50list=new double[t50listLength];
        t50posList= new int[t50listLength][4];
        t50index=0;
        slope50index=0;
        threshold1=40;
        threshold2=70;
        subtractedSlope=0.0;
        


		ratioImage=WindowManager.getImage("Ratio");
        mitoImage=WindowManager.getImage("Mito");
		overlay=new CustomCanvas(ratioImage);
        overlayM=new CustomCanvas(mitoImage);
		new StackWindow(ratioImage, overlay);
        new StackWindow(mitoImage, overlayM);

        ratioStack=ratioImage.getStack();
		numPoints=ratioStack.getSize();
       
        ratioListBlock=new double[numPoints][blockSize];
        blockIndex=0;
        
        Panel parameters=new Panel();
        parameters.setLayout(new GridBagLayout());
        threshold1TF= new TextField(Integer.toString(threshold1));
        threshold1TF.addActionListener(this);
        threshold1TF.addFocusListener(this);
        threshold1Label=new Label("Threshold 1");
        threshold2TF=new TextField(Integer.toString(threshold2));
        threshold2TF.addActionListener(this);
        threshold2TF.addFocusListener(this);
        threshold2Label=new Label("Threshold 2");
        subtractedSlopeTF=new TextField(d2s(subtractedSlope));
        subtractedSlopeTF.addActionListener(this);
        subtractedSlopeLabel=new Label("Subtracted Slope");
        parameters.add(threshold1Label);
        parameters.add(threshold1TF);
        parameters.add(threshold2Label);
        parameters.add(threshold2TF);
        parameters.add(subtractedSlopeLabel);
        parameters.add(subtractedSlopeTF);
        deadCheckBox=new Checkbox("Dead", true);
        parameters.add(deadCheckBox);
        add(parameters);

		Panel buttons=new Panel();
		buttons.setLayout(new GridLayout(1,4));
		timecourse = new Button("Timecourse");
		timecourse.addActionListener(this);
		buttons.add(timecourse);
		addt50 = new Button("Add cell");
		addt50.addActionListener(this);
		buttons.add(addt50);
		list50=new Button("List cells");
		list50.addActionListener(this);
		buttons.add(list50);
        allTimecourse=new Button("All timecourses");
		allTimecourse.addActionListener(this);
		buttons.add(allTimecourse);

		add(buttons);

		pack();
	
    }

	protected void drawPlot(ImageProcessor ip) {
		clear(ip);
		int x, y;
		if(ip == null){
			shutDown();
			return;
		}
		ip.setColor(Color.black);
		ip.setLineWidth(1);
		decimalPlaces = Analyzer.getPrecision();

        drawCurves(ip);
   

        
  		drawText(ip);

	}

	void clear(ImageProcessor ip){
		if(ip == null)return;
		if(color){
			int white = 0xffffff;
			int[] pixels = (int[])ip.getPixels();
			int n = pixels.length;
			for (int i = 0; i < n; i++) pixels[i] = white;
		} else {
			byte white = (byte)255;
			byte[] pixels = (byte[])ip.getPixels();
			int n = pixels.length;
			for (int i = 0; i < n; i++) pixels[i] = white;
		}
	}


	void drawCurves(ImageProcessor ip) {
		frame = new Rectangle(XMARGIN, YMARGIN, PLOT_WIDTH, PLOT_HEIGHT);
		ip.drawRect(frame.x-1, frame.y, frame.width+2, frame.height+1);
		
		ip.setColor(Color.gray);
		ip.drawLine(XMARGIN,(int)(PLOT_HEIGHT*0.5)+YMARGIN,XMARGIN+PLOT_WIDTH,(int)(PLOT_HEIGHT*0.5)+YMARGIN);

        ip.drawLine(XMARGIN+ratio50Frame*timestep+(int)(timestep*0.5/slope50),YMARGIN,XMARGIN+ratio50Frame*timestep-(int)(timestep*0.5/slope50),YMARGIN+PLOT_HEIGHT);
		ip.setColor(Color.black);	
		
		
		for (int i = 1; i<numPoints; i++) {
			
			ip.setColor(Color.black);
			if(i==ratio50Frame){
                ip.setLineWidth(2);
                ip.setColor(Color.gray);
                ip.drawRect(i*timestep+XMARGIN-2, PLOT_HEIGHT-(int)(ratioListNorm[i]*PLOT_HEIGHT)+YMARGIN-2,5,5);
                ip.setLineWidth(1);
            }
            else if (i==threshold1Frame || i==threshold2Frame){
                ip.setLineWidth(2);
                ip.drawRect(i*timestep+XMARGIN-2, PLOT_HEIGHT-(int)(ratioListNorm[i]*PLOT_HEIGHT)+YMARGIN-2,5,5);
                ip.setLineWidth(1);
            }
            else{
			//ip.drawLine((i-1)*timestep+XMARGIN, PLOT_HEIGHT-(int)(ratioListNorm[i-1]*PLOT_HEIGHT)+YMARGIN, i*timestep+XMARGIN, PLOT_HEIGHT-(int)(ratioListNorm[i]*PLOT_HEIGHT)+YMARGIN);
			ip.drawRect(i*timestep+XMARGIN-2, PLOT_HEIGHT-(int)(ratioListNorm[i]*PLOT_HEIGHT)+YMARGIN-2,4,4);
            }
		
		}

	}
	

	void drawText(ImageProcessor ip) {
		ip.setFont(new Font("SansSerif",Font.PLAIN,12));
       
		int col1 = XMARGIN + 5;
		int col2 = XMARGIN + PLOT_WIDTH/2;
		int row1 = YMARGIN+PLOT_HEIGHT+50;
		int row2 = row1 + 15;

		ip.drawString("t50: " + ratio50Frame, col1, row1);
		//ip.drawString("slope50: " + d2s(slope50), col1, row2);

		ip.setFont(new Font("SansSerif",Font.PLAIN,10));
		ip.drawString(d2s(0), XMARGIN, YMARGIN+PLOT_HEIGHT+15);
		ip.drawString(d2s(numPoints), XMARGIN + numPoints*timestep, YMARGIN+PLOT_HEIGHT+15);
        ip.drawString("Frame", XMARGIN+numPoints*timestep/2-10, YMARGIN+PLOT_HEIGHT+25);

		ip.drawString(d2s(ratioMinValue),XMARGIN + PLOT_WIDTH + 2, YMARGIN+PLOT_HEIGHT+8);
		ip.drawString(d2s(ratioMaxValue),XMARGIN + PLOT_WIDTH + 2, YMARGIN+8);

	}

	String d2s(double d) {
		if ((int)d==d)
			return IJ.d2s(d,0);
		else
			return IJ.d2s(d,decimalPlaces);
	}

	int getWidth(double d, ImageProcessor ip) {
		return ip.getStringWidth(d2s(d));
	}

	void showTimecourse() {
		StringBuffer sb = new StringBuffer();
		for (int i=0; i< numPoints; i++)
			sb.append((i+1)+"\t"+d2s(ratioList[i])+"\t"+d2s(ratioListNorm[i])+"\n");
		TextWindow tw = new TextWindow(getTitle(), "frame\tRaw Ratio\tNormalized Ratio", sb.toString(), 200, 400);

	}

    void showRawBlock() {
		StringBuffer sb = new StringBuffer();
        for (int i=0; i< numPoints; i++){     
            for (int j=0; j<blockSize; j++){
                sb.append(d2s(ratioListBlock[i][j])+"\t");
                //sb.append(d2s(ratioList[i])+"\t");
            }
            sb.append("\n");      
        }

     
        StringBuffer headings = new StringBuffer();
         	for (int k=0; k<blockSize; k++){
                 headings.append( "Cell " + d2s(k+1)+"\t");
             }

		TextWindow tw = new TextWindow(getTitle(), headings.toString(), sb.toString(), 200, 400);

	}
    

    /* adds the current t50 and slope 50 to the list of t50s and slope50s */
    void addT50(){
        if(deadCheckBox.getState())
            t50list[t50index]=ratio50Frame;  
        else
            t50list[t50index]=0;
        t50index++;
        
        if(blockIndex<blockSize){
             for(int i=0;i<numPoints;i++){
             ratioListBlock[i][blockIndex]=ratioList[i];
             }
             blockIndex++;
        }
        
        
        
        slope50list[slope50index]=slope50;
        slope50index++;
        
        Rectangle roi=ratioStack.getRoi();
        overlay.addPosition(roi.x, roi.y, roi.height, roi.width,deadCheckBox.getState());
        overlayM.addPosition(roi.x, roi.y, roi.height, roi.width,deadCheckBox.getState());
        
        /*add ratioList to next column of ratioBlock*/
    }
    
    /* displays the list of t50s and slope50s */
    void show50List(){
        StringBuffer sb = new StringBuffer();
		for (int i=0; i< t50list.length; i++)
			sb.append(t50list[i]+"\t"+d2s(slope50list[i])+"\n");
		TextWindow tw = new TextWindow(getTitle(), "t50\tslope50", sb.toString(), 200, 400);
    }


	public void actionPerformed(ActionEvent e) {
		Object b = e.getSource();
		if (b instanceof TextField) {
			handleText((TextField)b);
		} else {
			if (b==timecourse)
				showTimecourse();
			else if (b==addt50)
				addT50();
			else if (b==list50)
				show50List();
            else if (b==allTimecourse)
				showRawBlock();
            else if(b instanceof TextField) 
                handleText((TextField)b);    
		}
	}

	public void focusGained(FocusEvent e) {
		Component c = e.getComponent();
		if (c instanceof TextField)
			((TextField)c).selectAll();
	}

	public void focusLost(FocusEvent e) {
		Component c = e.getComponent();
		if (c instanceof TextField){
			handleText((TextField)c);
		}
	}

	protected void handleText(TextField b){
            if (b==threshold1TF){
                threshold1 = readNumber(threshold1TF);
                threshold1TF.setText(Integer.toString(threshold1));
                showPlot();
            }
            else if (b==threshold2TF){
                threshold2 = readNumber(threshold2TF);
                threshold2TF.setText(Integer.toString(threshold2));
                showPlot();
            }
            else if (b==subtractedSlopeTF){
                subtractedSlope=readDouble(subtractedSlopeTF);
                subtractedSlopeTF.setText(d2s(subtractedSlope));
                showPlot();
            }
	}

	public int readNumber(TextField tf){
		String s = tf.getText();
		int result;
		try {
			Integer Int = new Integer(s.trim());
			result = Int.intValue();
		} catch (NumberFormatException e) {
			result = -1;
		}
		return result;
	}

	public double readDouble(TextField tf){
		String s = tf.getText();
		double result;
		try {
			Double d = new Double(s.trim());
			result = d.doubleValue();
		} catch (NumberFormatException e) {
			result = -1;
		}
		return result;
	}

	public void lostOwnership(Clipboard clipboard, Transferable contents) {}



    class CustomCanvas extends ImageCanvas {
    	int[]xPosition;
        int[]yPosition;
        int[]height;
        int[]width;
        boolean[]dead;
        static final int number=200;
        int index;
	
        CustomCanvas(ImagePlus imp) {
            super(imp);
            xPosition=new int[number];
            yPosition=new int[number];
            height=new int[number];
            width=new int[number];
            dead=new boolean[number];
            index=0;
        }

        public void paint(Graphics g) {
            super.paint(g);
            drawOverlay(g);
         }

        public void addPosition(int x, int y, int h, int w, boolean d){
            xPosition[index]=x;
            yPosition[index]=y;
            height[index]=h;
            width[index]=w;
            dead[index]=d;
            index++;
            repaint();
        }
        
        void drawOverlay(Graphics g) {
            for(int i=0; i<number; i++) {
                if(dead[i]==true)
                    g.setColor(Color.red);
                else
                   g.setColor(Color.green);
                g.drawRect(xPosition[i],yPosition[i],width[i],height[i]);
                g.drawString(Integer.toString(i+1), xPosition[i]+2,yPosition[i]+10);
            }
       }   
    }//Custom Canvas 
}


