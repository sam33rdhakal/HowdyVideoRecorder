package com.qd.recorder;

import java.util.Iterator;
import java.util.LinkedList;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.View;

public class ProgressView extends View
{

	public ProgressView(Context context) {
		super(context);
		init(context);
	}

	public ProgressView(Context paramContext, AttributeSet paramAttributeSet) {
		super(paramContext, paramAttributeSet);
		init(paramContext);

	}

	public ProgressView(Context paramContext, AttributeSet paramAttributeSet,
						int paramInt) {
		super(paramContext, paramAttributeSet, paramInt);
		init(paramContext);
	}

	private Paint progressPaint, firstPaint, threePaint,breakPaint;//Three color brush
	private float firstWidth = 4f, threeWidth = 1f;//Breakpoint width
	private LinkedList<Integer> linkedList = new LinkedList<Integer>();
	private float perPixel = 0l;
	private float countRecorderTime = 10000;//The total recording time

	public void setTotalTime(float time){
		countRecorderTime = time;
	}

	private void init(Context paramContext) {

		progressPaint = new Paint();
		firstPaint = new Paint();
		threePaint = new Paint();
		breakPaint = new Paint();

		// Background
		setBackgroundColor(Color.parseColor("#19000000"));

		// Color major progress
		progressPaint.setStyle(Paint.Style.FILL);
		progressPaint.setColor(Color.parseColor("#19e3cf"));

		// Flashing yellow progress
		firstPaint.setStyle(Paint.Style.FILL);
		firstPaint.setColor(Color.parseColor("#ffcc42"));

		// Progress at 3 seconds
		threePaint.setStyle(Paint.Style.FILL);
		threePaint.setColor(Color.parseColor("#12a899"));

		breakPaint.setStyle(Paint.Style.FILL);
		breakPaint.setColor(Color.parseColor("#000000"));

		DisplayMetrics dm = new DisplayMetrics();
		((Activity)paramContext).getWindowManager().getDefaultDisplay().getMetrics(dm);
		perPixel = dm.widthPixels/countRecorderTime;

		perSecProgress = perPixel;

	}

	/**
	 * Drawing state
	 *
	 */
	public static enum State {
		START(0x1),PAUSE(0x2);

		static State mapIntToValue(final int stateInt) {
			for (State value : State.values()) {
				if (stateInt == value.getIntValue()) {
					return value;
				}
			}
			return PAUSE;
		}

		private int mIntValue;

		State(int intValue) {
			mIntValue = intValue;
		}

		int getIntValue() {
			return mIntValue;
		}
	}


	private volatile State currentState = State.PAUSE;//Current status
	private boolean isVisible = true;//Flashing yellow area is visible
	private float countWidth = 0;//Every complete, the progress bar length draw
	private float perProgress = 0;//When a finger is pressed, increase the length of each progress bar
	private float perSecProgress = 0;//Corresponding pixels per millisecond
	private long initTime;//Draw timestamp Completion
	private long drawFlashTime = 0;//Flashing yellow area timestamp

	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		long curTime = System.currentTimeMillis();
		//Log.i("recorder", curTime  - initTime + "");
		countWidth = 0;
		//Every time the order will be drawn in the queue breakpoints, drawn
		if(!linkedList.isEmpty()){
			float frontTime = 0;
			Iterator<Integer> iterator = linkedList.iterator();
			while(iterator.hasNext()){
				int time = iterator.next();
				//The draw determined the starting position of the rectangle
				float left = countWidth;
				//The draw determined the rectangular end position
				countWidth += (time-frontTime)*perPixel;
				//Draw a progress bar
				canvas.drawRect(left, 0,countWidth,getMeasuredHeight(),progressPaint);
				//Draw break
				canvas.drawRect(countWidth, 0,countWidth + threeWidth,getMeasuredHeight(),breakPaint);
				countWidth += threeWidth;

				frontTime = time;
			}
			//Draw three seconds at the break
			if(linkedList.getLast() <= 4000)
				canvas.drawRect(perPixel*4000, 0,perPixel*4000+threeWidth,getMeasuredHeight(),threePaint);
		}else//Draw three seconds at the break
			canvas.drawRect(perPixel*4000, 0,perPixel*4000+threeWidth,getMeasuredHeight(),threePaint);

		//When the finger on the screen, a progress bar will grow
		if(currentState == State.START){
			perProgress += perSecProgress*(curTime - initTime );
			if(countWidth + perProgress <= getMeasuredWidth())
				canvas.drawRect(countWidth, 0,countWidth + perProgress,getMeasuredHeight(),progressPaint);
			else
				canvas.drawRect(countWidth, 0,getMeasuredWidth(),getMeasuredHeight(),progressPaint);
		}
		//Draw the yellow area twinkling, blinking once every 500ms
		if(drawFlashTime==0 || curTime - drawFlashTime >= 500){
			isVisible = !isVisible;
			drawFlashTime = System.currentTimeMillis();
		}
		if(isVisible){
			if(currentState == State.START)
				canvas.drawRect(countWidth + perProgress, 0,countWidth + firstWidth + perProgress,getMeasuredHeight(),firstPaint);
			else
				canvas.drawRect(countWidth, 0,countWidth + firstWidth,getMeasuredHeight(),firstPaint);
		}
		//End drawing glowing yellow area
		initTime = System.currentTimeMillis();
		invalidate();
	}

	/**
	 * Set the progress bar state
	 * @param state
	 */
	public void setCurrentState(State state){
		currentState = state;
		if(state == State.PAUSE)
			perProgress = perSecProgress;
	}

	/**
	 * Lift a finger to save time in the queue
	 */
	public void putProgressList(int time) {
		linkedList.add(time);
	}
}