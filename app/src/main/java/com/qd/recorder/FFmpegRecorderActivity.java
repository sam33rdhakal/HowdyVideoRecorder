package com.qd.recorder;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.provider.MediaStore.Video;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.googlecode.javacv.FrameRecorder;
import com.googlecode.javacv.cpp.opencv_core.IplImage;
import com.qd.recorder.ProgressView.State;
import com.qd.videorecorder.R;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.ShortBuffer;
import java.util.Collections;
import java.util.List;

import static com.googlecode.javacv.cpp.opencv_core.IPL_DEPTH_8U;


/**
 * RecorderParameter
 */
public class FFmpegRecorderActivity extends Activity implements OnClickListener, OnTouchListener
{

	private final static String CLASS_LABEL = "RecordActivity";
	private final static String LOG_TAG = CLASS_LABEL;

	private PowerManager.WakeLock mWakeLock;
	// Store the address of the video file
	private String strVideoPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "rec_video.mp4";
	//Video file object
	private File fileVideoPath = null;
	//Video files stored in the system url
	private Uri uriVideoPath = null;
	//Determine whether the need to record, pause recording when click Next
	private boolean rec = false;
	//Determine the need for recording, fingers pressed to continue, when lifting pause
	boolean recording = false;
	//Determine whether to start the recording, set to true when first pressed screen
	boolean isRecordingStarted = false;
	//If the flash
	boolean isFlashOn = false;
	TextView txtTimer, txtRecordingSize;
	//Respectively, the flash button, Cancel button, the Next button, turn rear camera button
	Button flashIcon = null, cancelBtn, nextBtn, switchCameraIcon = null;
	boolean nextEnabled = false;

	//Record video and save audio classes
	private volatile NewFFmpegFrameRecorder videoRecorder;

	//Determine whether it is front camera
	private boolean isPreviewOn = false;
	//The quality of the current recording will affect the video resolution and file size
	private int currentResolution = CONSTANTS.RESOLUTION_MEDIUM_VALUE;
	private Camera mCamera;

	//Preview screen width and height and width and height
	private int previewWidth = 480, screenWidth = 640;
	private int previewHeight = 480, screenHeight = 480;

	//Audio sample rate, recorderParameters there will be defaults
	private int sampleRate = 44100;
	//Record audio class called System
	private AudioRecord audioRecord;
	//Record audio thread
	private AudioRecordRunnable audioRecordRunnable;
	private Thread audioThread;
	//Start and stop recording audio tag
	volatile boolean runAudioThread = true;

	//Camera and its parameters
	private Camera cameraDevice;
	private CameraView cameraView;
	Parameters cameraParameters = null;
	//IplImage objects that store the camera returns byte [], and the image width and height, depth, channel, etc.
	private IplImage yuvIplImage = null;
	//Respectively, the default camera (rear), the default resolution calling camera, the selected camera (front or rear)
	int defaultCameraId = -1, defaultScreenResolution = -1, cameraSelection = 0;

	private Dialog dialog = null;
	//SurfaceView contains the display camera data
	RelativeLayout topLayout = null;

	//The first record of the time the screen is pressed
	long firstTime = 0;
	//Lift your finger is the time
	long startPauseTime = 0;
	//Each time you press the pause time between the fingers and lift
	long totalPauseTime = 0;
	//Lift your finger is the time
	long pausedTime = 0;
	//Total pause time
	long stopPauseTime = 0;
	//Total time effective recording
	long totalTime = 0;
	//Video frame rate
	private int frameRate = 24;
	//The maximum recording time
	private int recordingTime = 10000;
	//The shortest time recorded
	private int recordingMinimumTime = 4000;
	//another scene
	private int recordingChangeTime = 4000;

	boolean recordFinish = false;
	private Dialog creatingProgress;

	//Audio timestamp
	private volatile long mAudioTimestamp = 0L;
	private long mLastAudioTimestamp = 0L;
	private volatile long mAudioTimeRecorded;
	private long frameTime = 0L;
	//Data structure of each frame
	private SavedFrames lastSavedframe = new SavedFrames(null, 0L);
	//Video time stamp
	private long mVideoTimestamp = 0L;
	//When saved video files
	private boolean isRecordingSaved = false;
	private boolean isFinalizing = false;

	//Progress bar
	private ProgressView progressView;
	//Image capture of the first frame
	private String imagePath = null;
	private RecorderState currentRecorderState = RecorderState.PRESS;
	private ImageView stateImageView;

	private byte[] firstData = null;
	private byte[] bufferByte;

	private RecorderThread recorderThread;

	private Handler mHandler;

	private void initHandler()
	{
		mHandler = new Handler()
		{
			@Override
			public void dispatchMessage(Message msg)
			{
				switch (msg.what)
				{
					case 2:
//					int resId = 0;
//					if(currentRecorderState == RecorderState.PRESS){
//						resId = R.drawable.video_text01;
//					}else if(currentRecorderState == RecorderState.LOOSEN){
//						resId = R.drawable.video_text02;
//					}else if(currentRecorderState == RecorderState.CHANGE){
//						resId = R.drawable.video_text03;
//					}else if(currentRecorderState == RecorderState.SUCCESS){
//						resId = R.drawable.video_text04;
//					}
//					stateImageView.setImageResource(resId);
						break;
					case 3:
						if (!recording)
						{
							initiateRecording(true);
						}
						else
						{
							//Update pause time
							stopPauseTime = System.currentTimeMillis();
							totalPauseTime = stopPauseTime - startPauseTime - ((long) (1.0 / (double) frameRate) * 1000);
							pausedTime += totalPauseTime;
						}
						rec = true;
						//Start progress bar grow
						progressView.setCurrentState(State.START);
						//setTotalVideoTime();
						break;
					case 4:
						//Set the progress bar pause state
						progressView.setCurrentState(State.PAUSE);
						//Add a timestamp to the progress bar suspended queue
						progressView.putProgressList((int) totalTime);
						rec = false;
						startPauseTime = System.currentTimeMillis();
						if (totalTime >= recordingMinimumTime)
						{
							currentRecorderState = RecorderState.SUCCESS;
							mHandler.sendEmptyMessage(2);
						}
						else if (totalTime >= recordingChangeTime)
						{
							currentRecorderState = RecorderState.CHANGE;
							mHandler.sendEmptyMessage(2);
						}
						break;
					case 5:
						currentRecorderState = RecorderState.SUCCESS;
						mHandler.sendEmptyMessage(2);
						break;
					default:
						break;
				}
			}
		};
	}

	//neon library optimized for opencv
	static
	{
		System.loadLibrary("checkneon");
	}

	public native static int checkNeonFromJNI();

	private boolean initSuccess = false;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_recorder);

		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, CLASS_LABEL);
		mWakeLock.acquire();

		DisplayMetrics displaymetrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
		//Find screen dimensions
		screenWidth = displaymetrics.widthPixels;
		screenHeight = displaymetrics.heightPixels;

		initHandler();

		initLayout();
	}

	@Override
	public boolean dispatchTouchEvent(MotionEvent ev)
	{
		if (!initSuccess)
		{
			return false;
		}
		return super.dispatchTouchEvent(ev);
	}


	@Override
	protected void onResume()
	{
		super.onResume();
		mHandler.sendEmptyMessage(2);

		if (mWakeLock == null)
		{
			//Get wake lock, keep the screen lit
			PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
			mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, CLASS_LABEL);
			mWakeLock.acquire();
		}
	}

	@Override
	protected void onPause()
	{
		super.onPause();
		if (!isFinalizing)
		{
			finish();
		}

		if (mWakeLock != null)
		{
			mWakeLock.release();
			mWakeLock = null;
		}
	}

	@Override
	protected void onDestroy()
	{
		super.onDestroy();
		//Log.i("video", this.getLocalClassName()+"—destory");
		recording = false;
		runAudioThread = false;

		releaseResources();

		if (cameraView != null)
		{
			cameraView.stopPreview();
			if (cameraDevice != null)
			{
				cameraDevice.setPreviewCallback(null);
				cameraDevice.release();
			}
			cameraDevice = null;
		}
		firstData = null;
		mCamera = null;
		cameraView = null;
		if (mWakeLock != null)
		{
			mWakeLock.release();
			mWakeLock = null;
		}
	}

	private void initLayout()
	{
		//stateImageView = (ImageView) findViewById(R.id.recorder_surface_state);

		progressView = (ProgressView) findViewById(R.id.recorder_progress);
		progressView.setTotalTime(recordingTime);
		cancelBtn = (Button) findViewById(R.id.recorder_cancel);
		cancelBtn.setOnClickListener(this);
		nextBtn = (Button) findViewById(R.id.recorder_next);
		nextBtn.setOnClickListener(this);
		//txtTimer = (TextView)findViewById(R.id.txtTimer);
		flashIcon = (Button) findViewById(R.id.recorder_flashlight);
		switchCameraIcon = (Button) findViewById(R.id.recorder_frontcamera);
		flashIcon.setOnClickListener(this);

		if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT))
		{
			switchCameraIcon.setVisibility(View.VISIBLE);
		}
		initCameraLayout();
	}

	private void initCameraLayout()
	{
		new AsyncTask<String, Integer, Boolean>()
		{

			@Override
			protected Boolean doInBackground(String... params)
			{
				boolean result = setCamera();

				if (!initSuccess)
				{

					initVideoRecorder();
					startRecording();

					initSuccess = true;
				}

				return result;
			}

			@Override
			protected void onPostExecute(Boolean result)
			{
				if (!result || cameraDevice == null)
				{
					//FuncCore.showToast(FFmpegRecorderActivity.this, "Unable to connect to camera");
					finish();
					return;
				}

				topLayout = (RelativeLayout) findViewById(R.id.recorder_surface_parent);
				if (topLayout != null && topLayout.getChildCount() > 0)
				{
					topLayout.removeAllViews();
				}

				cameraView = new CameraView(FFmpegRecorderActivity.this, cameraDevice);

				handleSurfaceChanged();
				if (recorderThread == null)
				{
					recorderThread = new RecorderThread(yuvIplImage, videoRecorder, previewHeight * previewWidth * 3 / 2, frameRate * (recordingTime / 1000));
					recorderThread.start();
				}
				//Set the width and height surface
				RelativeLayout.LayoutParams layoutParam1 = new RelativeLayout.LayoutParams(screenWidth, (int) (screenWidth * (previewWidth / (previewHeight * 1f))));
				layoutParam1.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
				//int margin = Util.calculateMargin(previewWidth, screenWidth);
				//layoutParam1.setMargins(0,margin,0,margin);

				RelativeLayout.LayoutParams layoutParam2 = new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
				layoutParam2.topMargin = screenWidth;

				View view = new View(FFmpegRecorderActivity.this);
				view.setFocusable(false);
				view.setBackgroundColor(Color.BLACK);
				view.setFocusableInTouchMode(false);

				topLayout.addView(cameraView, layoutParam1);
				topLayout.addView(view, layoutParam2);

				topLayout.setOnTouchListener(FFmpegRecorderActivity.this);

				switchCameraIcon.setOnClickListener(FFmpegRecorderActivity.this);
				if (cameraSelection == CameraInfo.CAMERA_FACING_FRONT)
				{
					flashIcon.setVisibility(View.GONE);
				}
				else
				{
					flashIcon.setVisibility(View.VISIBLE);
				}
			}

		}.execute("start");
	}

	private boolean setCamera()
	{
		try
		{

			if (Build.VERSION.SDK_INT > Build.VERSION_CODES.FROYO)
			{
				int numberOfCameras = Camera.getNumberOfCameras();

				CameraInfo cameraInfo = new CameraInfo();
				for (int i = 0; i < numberOfCameras; i++)
				{
					Camera.getCameraInfo(i, cameraInfo);
					if (cameraInfo.facing == cameraSelection)
					{
						defaultCameraId = i;
					}
				}
			}
			stopPreview();
			if (mCamera != null)
			{
				mCamera.release();
			}

			if (defaultCameraId >= 0)
			{
				cameraDevice = Camera.open(defaultCameraId);
			}
			else
			{
				cameraDevice = Camera.open();
			}

		}
		catch (Exception e)
		{
			return false;
		}
		return true;
	}


	private void initVideoRecorder()
	{
		strVideoPath = Util.createFinalPath(this);//Util.createTempPath(tempFolderPath);

		RecorderParameters recorderParameters = Util.getRecorderParameter(currentResolution);
		sampleRate = recorderParameters.getAudioSamplingRate();
		frameRate = recorderParameters.getVideoFrameRate();
		frameTime = (1000000L / frameRate);

		fileVideoPath = new File(strVideoPath);
		videoRecorder = new NewFFmpegFrameRecorder(strVideoPath, 480, 480, 1);
		videoRecorder.setFormat(recorderParameters.getVideoOutputFormat());
		videoRecorder.setSampleRate(recorderParameters.getAudioSamplingRate());
		videoRecorder.setFrameRate(recorderParameters.getVideoFrameRate());
		videoRecorder.setVideoCodec(recorderParameters.getVideoCodec());
		videoRecorder.setVideoQuality(recorderParameters.getVideoQuality());
		videoRecorder.setAudioQuality(recorderParameters.getVideoQuality());
		videoRecorder.setAudioCodec(recorderParameters.getAudioCodec());
		videoRecorder.setVideoBitrate(recorderParameters.getVideoBitrate());
		videoRecorder.setAudioBitrate(recorderParameters.getAudioBitrate());

		audioRecordRunnable = new AudioRecordRunnable();
		audioThread = new Thread(audioRecordRunnable);
	}

	public void startRecording()
	{

		try
		{
			videoRecorder.start();
			audioThread.start();

		}
		catch (NewFFmpegFrameRecorder.Exception e)
		{
			e.printStackTrace();
		}
	}

	/**
	 * Stop Recording
	 */
	public class AsyncStopRecording extends AsyncTask<Void, Integer, Void>
	{

		private ProgressBar bar;
		private TextView progress;

		@Override
		protected void onPreExecute()
		{
			isFinalizing = true;
			recordFinish = true;
			runAudioThread = false;

			//Creating the progress bar
			creatingProgress = new Dialog(FFmpegRecorderActivity.this, R.style.Dialog_loading_noDim);
			Window dialogWindow = creatingProgress.getWindow();
			WindowManager.LayoutParams lp = dialogWindow.getAttributes();
			lp.width = (int) (getResources().getDisplayMetrics().density * 240);
			lp.height = (int) (getResources().getDisplayMetrics().density * 80);
			lp.gravity = Gravity.CENTER;
			dialogWindow.setAttributes(lp);
			creatingProgress.setCanceledOnTouchOutside(false);
			creatingProgress.setContentView(R.layout.activity_recorder_progress);

			progress = (TextView) creatingProgress.findViewById(R.id.recorder_progress_progresstext);
			bar = (ProgressBar) creatingProgress.findViewById(R.id.recorder_progress_progressbar);
			creatingProgress.show();


			//txtTimer.setVisibility(View.INVISIBLE);
			//handler.removeCallbacks(mUpdateTimeTask);
			super.onPreExecute();
		}

		@Override
		protected void onProgressUpdate(Integer... values)
		{
			progress.setText(values[0] + "%");
			bar.setProgress(values[0]);
		}

		/**
		 * Based on byte [] data in the synthesis of a bitmap,
		 * Cut into 480 * 480, and rotated 90 degrees, the Save to File
		 *
		 * @param data
		 */
		private void getFirstCapture(byte[] data)
		{

			String captureBitmapPath = CONSTANTS.CAMERA_FOLDER_PATH;

			captureBitmapPath = Util.createImagePath(FFmpegRecorderActivity.this);
			YuvImage localYuvImage = new YuvImage(data, 17, previewWidth, previewHeight, null);
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			FileOutputStream outStream = null;

			try
			{
				File file = new File(captureBitmapPath);
				if (!file.exists())
				{
					file.createNewFile();
				}
				localYuvImage.compressToJpeg(new Rect(0, 0, previewWidth, previewHeight), 100, bos);
				Bitmap localBitmap1 = BitmapFactory.decodeByteArray(bos.toByteArray(),
						0, bos.toByteArray().length);

				bos.close();

				Matrix localMatrix = new Matrix();
				if (cameraSelection == 0)
				{
					localMatrix.setRotate(90.0F);
				}
				else
				{
					localMatrix.setRotate(270.0F);
				}

				Bitmap localBitmap2 = Bitmap.createBitmap(localBitmap1, 0, 0,
						localBitmap1.getHeight(),
						localBitmap1.getHeight(),
						localMatrix, true);

				ByteArrayOutputStream bos2 = new ByteArrayOutputStream();
				localBitmap2.compress(Bitmap.CompressFormat.JPEG, 100, bos2);

				outStream = new FileOutputStream(captureBitmapPath);
				outStream.write(bos2.toByteArray());
				outStream.close();

				localBitmap1.recycle();
				localBitmap2.recycle();

				isFirstFrame = false;
				imagePath = captureBitmapPath;
			}
			catch (FileNotFoundException e)
			{
				isFirstFrame = true;
				e.printStackTrace();
			}
			catch (IOException e)
			{
				isFirstFrame = true;
				e.printStackTrace();
			}
		}


		@Override
		protected Void doInBackground(Void... params)
		{
			if (firstData != null)
			{
				getFirstCapture(firstData);
			}

			recorderThread.stopRecord(this);

			isFinalizing = false;
			if (videoRecorder != null && recording)
			{
				recording = false;
				releaseResources();
			}
			publishProgress(100);
			return null;
		}

		public void publishProgressFromOther(int progress)
		{
			publishProgress(progress);
		}

		@Override
		protected void onPostExecute(Void result)
		{
			if (!isFinishing())
			{
				creatingProgress.dismiss();
			}
			registerVideo();
			returnToCaller(true);
			videoRecorder = null;
		}

	}

	/**
	 * Pop-up box will be discarded video
	 */
	private void showCancellDialog()
	{
		Util.showDialog(FFmpegRecorderActivity.this, "Discard", "Do you want to discard the video？", 2, new Handler()
		{
			@Override
			public void dispatchMessage(Message msg)
			{
				if (msg.what == 1)
				{
					videoTheEnd(false);
				}
			}
		});
	}

	@Override
	public void onBackPressed()
	{
		if (recording)
		{
			showCancellDialog();
		}
		else
		{
			videoTheEnd(false);
		}
	}

	/**
	 * Record audio thread
	 */
	class AudioRecordRunnable implements Runnable
	{

		int bufferSize;
		short[] audioData;
		int bufferReadResult;
		private final AudioRecord audioRecord;
		public volatile boolean isInitialized;
		private int mCount = 0;

		private AudioRecordRunnable()
		{
			bufferSize = AudioRecord.getMinBufferSize(sampleRate,
					AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
			audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate,
					AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
			audioData = new short[bufferSize];
		}

		/**
		 * shortBuffer contains the audio data and the starting position
		 *
		 * @param shortBuffer
		 */
		private void record(ShortBuffer shortBuffer)
		{
			try
			{
				if (videoRecorder != null)
				{
					this.mCount += shortBuffer.limit();
					videoRecorder.record(0, new Buffer[]{shortBuffer});
				}
			}
			catch (FrameRecorder.Exception localException)
			{

			}
			return;
		}

		/**
		 * Update Audio timestamp
		 */
		private void updateTimestamp()
		{
			if (videoRecorder != null)
			{
				int i = Util.getTimeStampInNsFromSampleCounted(this.mCount);
				if (mAudioTimestamp != i)
				{
					mAudioTimestamp = i;
					mAudioTimeRecorded = System.nanoTime();
				}
			}
		}

		public void run()
		{
			android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
			this.isInitialized = false;
			if (audioRecord != null)
			{
				//Determining whether the audio recording is initialized
				while (this.audioRecord.getState() == 0)
				{
					try
					{
						Thread.sleep(100L);
					}
					catch (InterruptedException localInterruptedException)
					{
					}
				}
				this.isInitialized = true;
				this.audioRecord.startRecording();
				while (((runAudioThread) || (mVideoTimestamp > mAudioTimestamp)) && (mAudioTimestamp < (1000 * recordingTime)))
				{
					updateTimestamp();
					bufferReadResult = this.audioRecord.read(audioData, 0, audioData.length);
					if ((bufferReadResult > 0) && ((recording && rec) || (mVideoTimestamp > mAudioTimestamp)))
					{
						record(ShortBuffer.wrap(audioData, 0, bufferReadResult));
					}
				}
				this.audioRecord.stop();
				this.audioRecord.release();
			}
		}
	}

	//Get picture of the first frame
	private boolean isFirstFrame = true;


	/**
	 * Content display of the camera, and the camera returns to each frame of data
	 */
	class CameraView extends SurfaceView implements SurfaceHolder.Callback, PreviewCallback
	{

		private SurfaceHolder mHolder;


		public CameraView(Context context, Camera camera)
		{
			super(context);
			mCamera = camera;
			cameraParameters = mCamera.getParameters();
			mHolder = getHolder();
			mHolder.addCallback(CameraView.this);
			mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
			mCamera.setPreviewCallbackWithBuffer(CameraView.this);
		}


		@Override
		public void surfaceCreated(SurfaceHolder holder)
		{
			try
			{
				stopPreview();
				mCamera.setPreviewDisplay(holder);
			}
			catch (IOException exception)
			{
				mCamera.release();
				mCamera = null;
			}
		}

		public void surfaceChanged(SurfaceHolder holder, int format, int width, int height)
		{
			if (isPreviewOn)
			{
				mCamera.stopPreview();
			}
			handleSurfaceChanged();
			startPreview();
			mCamera.autoFocus(null);
		}

		@Override
		public void surfaceDestroyed(SurfaceHolder holder)
		{
			try
			{
				mHolder.addCallback(null);
				mCamera.setPreviewCallback(null);

			}
			catch (RuntimeException e)
			{
			}
		}

		public void startPreview()
		{
			if (!isPreviewOn && mCamera != null)
			{
				isPreviewOn = true;
				mCamera.startPreview();
			}
		}

		public void stopPreview()
		{
			if (isPreviewOn && mCamera != null)
			{
				isPreviewOn = false;
				mCamera.stopPreview();
			}
		}

		private byte[] rotateYUV420Degree90(byte[] data, int imageWidth, int imageHeight)
		{

			final byte[] yuv = new byte[previewWidth * previewHeight * 3 / 2];
			// Rotate the Y luma
			int i = 0;
			for (int x = 0; x < imageWidth; x++)
			{
				for (int y = imageHeight - 1; y >= 0; y--)
				{
					yuv[i] = data[y * imageWidth + x];
					i++;
				}

			}
			// Rotate the U and V color components
			i = imageWidth * imageHeight * 3 / 2 - 1;
			for (int x = imageWidth - 1; x > 0; x = x - 2)
			{
				for (int y = 0; y < imageHeight / 2; y++)
				{
					yuv[i] = data[(imageWidth * imageHeight) + (y * imageWidth) + x];
					i--;
					yuv[i] = data[(imageWidth * imageHeight) + (y * imageWidth) + (x - 1)];
					i--;
				}
			}
			return yuv;
		}

		private byte[] rotateYUV420Degree180(byte[] data, int imageWidth, int imageHeight)
		{
			byte[] yuv = new byte[imageWidth * imageHeight * 3 / 2];
			int i = 0;
			int count = 0;

			for (i = imageWidth * imageHeight - 1; i >= 0; i--)
			{
				yuv[count] = data[i];
				count++;
			}

			i = imageWidth * imageHeight * 3 / 2 - 1;
			for (i = imageWidth * imageHeight * 3 / 2 - 1; i >= imageWidth
					* imageHeight; i -= 2)
			{
				yuv[count++] = data[i - 1];
				yuv[count++] = data[i];
			}
			return yuv;
		}

		private byte[] rotateYUV420Degree270(byte[] data, int imageWidth, int imageHeight)
		{
			final byte[] yuv = new byte[previewWidth * previewHeight * 3 / 2];
			int wh = 0;
			int uvHeight = 0;
			if (imageWidth != 0 || imageHeight != 0)
			{
				wh = imageWidth * imageHeight;
				uvHeight = imageHeight >> 1;//uvHeight = height / 2
			}

			//Rotation
			int k = 0;
			for (int i = 0; i < imageWidth; i++)
			{
				int nPos = 0;
				for (int j = 0; j < imageHeight; j++)
				{
					yuv[k] = data[nPos + i];
					k++;
					nPos += imageWidth;
				}
			}

			for (int i = 0; i < imageWidth; i += 2)
			{
				int nPos = wh;
				for (int j = 0; j < uvHeight; j++)
				{
					yuv[k] = data[nPos + i];
					yuv[k + 1] = data[nPos + i + 1];
					k += 2;
					nPos += imageWidth;
				}
			}
			//This part can be rotated 270 degrees, but the wrong color image
			//	    // Rotate the Y luma
			//	    int i = 0;
			//	    for(int x = imageWidth-1;x >= 0;x--)
			//	    {
			//	        for(int y = 0;y < imageHeight;y++)
			//	        {
			//	            yuv[i] = data[y*imageWidth+x];
			//	            i++;
			//	        }
			//
			//	    }
			//	    // Rotate the U and V color components
			//		i = imageWidth*imageHeight;
			//	    for(int x = imageWidth-1;x > 0;x=x-2)
			//	    {
			//	        for(int y = 0;y < imageHeight/2;y++)
			//	        {
			//	            yuv[i] = data[(imageWidth*imageHeight)+(y*imageWidth)+x];
			//	            i++;
			//	            yuv[i] = data[(imageWidth*imageHeight)+(y*imageWidth)+(x-1)];
			//	            i++;
			//	        }
			//	    }
			return rotateYUV420Degree180(yuv, imageWidth, imageHeight);
		}

		public byte[] cropYUV420(byte[] data, int imageW, int imageH, int newImageH)
		{
			int cropH;
			int i, j, count, tmp;
			byte[] yuv = new byte[imageW * newImageH * 3 / 2];

			cropH = (imageH - newImageH) / 2;

			count = 0;
			for (j = cropH; j < cropH + newImageH; j++)
			{
				for (i = 0; i < imageW; i++)
				{
					yuv[count++] = data[j * imageW + i];
				}
			}

			//Cr Cb
			tmp = imageH + cropH / 2;
			for (j = tmp; j < tmp + newImageH / 2; j++)
			{
				for (i = 0; i < imageW; i++)
				{
					yuv[count++] = data[j * imageW + i];
				}
			}

			return yuv;
		}

		@Override
		public void onPreviewFrame(byte[] data, Camera camera)
		{
			//Calculate the timestamp
			long frameTimeStamp = 0L;
			if (mAudioTimestamp == 0L && firstTime > 0L)
			{
				frameTimeStamp = 1000L * (System.currentTimeMillis() - firstTime);
			}
			else if (mLastAudioTimestamp == mAudioTimestamp)
			{
				frameTimeStamp = mAudioTimestamp + frameTime;
			}
			else
			{
				long l2 = (System.nanoTime() - mAudioTimeRecorded) / 1000L;
				frameTimeStamp = l2 + mAudioTimestamp;
				mLastAudioTimestamp = mAudioTimestamp;
			}

			//Recording video
			if (recording && rec)
			{
				if (lastSavedframe != null
						&& lastSavedframe.getFrameBytesData() != null && yuvIplImage != null)
				{
					//Save the picture to a frame
					if (isFirstFrame)
					{
						isFirstFrame = false;
						firstData = data;
					}
					//More than the minimum time, click the Next button
					totalTime = System.currentTimeMillis() - firstTime - pausedTime - ((long) (1.0 / (double) frameRate) * 1000);
					Log.d("TIME", totalTime + "");
					if (!nextEnabled && totalTime >= recordingChangeTime)
					{
						nextEnabled = true;
						nextBtn.setEnabled(true);
					}

					if (nextEnabled && totalTime >= recordingMinimumTime)
					{
						mHandler.sendEmptyMessage(5);
					}

					if (currentRecorderState == RecorderState.PRESS && totalTime >= recordingChangeTime)
					{
						currentRecorderState = RecorderState.LOOSEN;
						mHandler.sendEmptyMessage(2);
					}

					mVideoTimestamp += frameTime;
					if (lastSavedframe.getTimeStamp() > mVideoTimestamp)
					{
						mVideoTimestamp = lastSavedframe.getTimeStamp();
					}

					recorderThread.putByteData(lastSavedframe);
				}
				byte[] tempData = rotateYUV420Degree90(data, previewWidth, previewHeight);
				if (cameraSelection == 1)
				{
					tempData = rotateYUV420Degree270(data, previewWidth, previewHeight);
				}
				lastSavedframe = new SavedFrames(tempData, frameTimeStamp);
			}

			mCamera.addCallbackBuffer(bufferByte);
		}
	}

	@Override
	public boolean onTouch(View v, MotionEvent event)
	{

		if (!recordFinish)
		{
			if (totalTime < recordingTime)
			{
				switch (event.getAction())
				{
					case MotionEvent.ACTION_DOWN:
						// If MediaRecorder not been initialized
						// Perform initialization
						mHandler.removeMessages(3);
						mHandler.removeMessages(4);
						mHandler.sendEmptyMessageDelayed(3, 300);
						break;
					case MotionEvent.ACTION_UP:
						mHandler.removeMessages(3);
						mHandler.removeMessages(4);
						if (rec)
						{
							mHandler.sendEmptyMessage(4);
						}

						break;
				}
			}
			else
			{
				//If the recording time exceeds the maximum time, save video
				rec = false;
				saveRecording();
			}
		}
		return true;
	}

	/**
	 * Turn off the camera preview
	 */
	public void stopPreview()
	{
		if (isPreviewOn && mCamera != null)
		{
			isPreviewOn = false;
			mCamera.stopPreview();

		}
	}

	private void handleSurfaceChanged()
	{
		if (mCamera == null)
		{
			//showToast(this, "Unable to connect to camera");
			finish();
			return;
		}
		//Get all the support camera resolutions
		List<Camera.Size> resolutionList = Util.getResolutionList(mCamera);
		if (resolutionList != null && resolutionList.size() > 0)
		{
			Collections.sort(resolutionList, new Util.ResolutionComparator());
			Camera.Size previewSize = null;
			if (defaultScreenResolution == -1)
			{
				boolean hasSize = false;
				//If the camera supports 640 * 480, 640 * 480 then forcibly set
				for (int i = 0; i < resolutionList.size(); i++)
				{
					Size size = resolutionList.get(i);
					if (size != null && size.width == 640 && size.height == 480)
					{
						previewSize = size;
						hasSize = true;
						break;
					}
				}
				//If not set to the middle of the
				if (!hasSize)
				{
					int mediumResolution = resolutionList.size() / 2;
					if (mediumResolution >= resolutionList.size())
					{
						mediumResolution = resolutionList.size() - 1;
					}
					previewSize = resolutionList.get(mediumResolution);
				}
			}
			else
			{
				if (defaultScreenResolution >= resolutionList.size())
				{
					defaultScreenResolution = resolutionList.size() - 1;
				}
				previewSize = resolutionList.get(defaultScreenResolution);
			}
			//Get calculated camera resolution
			if (previewSize != null)
			{
				previewWidth = previewSize.width;
				previewHeight = previewSize.height;
				cameraParameters.setPreviewSize(previewWidth, previewHeight);
				if (videoRecorder != null)
				{
					videoRecorder.setImageWidth(previewWidth);
					videoRecorder.setImageHeight(previewHeight);
				}

			}
		}

		bufferByte = new byte[previewWidth * previewHeight * 3 / 2];

		mCamera.addCallbackBuffer(bufferByte);

		//Set the preview frame rate
		cameraParameters.setPreviewFrameRate(frameRate);
		// Build a IplImage object used to record video
		// And opencv in the same way as cvCreateImage

		yuvIplImage = IplImage.create(previewHeight, previewWidth, IPL_DEPTH_8U, 2);

		// 8 bit system version does not support this focus
		if (Build.VERSION.SDK_INT > Build.VERSION_CODES.FROYO)
		{
			mCamera.setDisplayOrientation(Util.determineDisplayOrientation(FFmpegRecorderActivity.this, defaultCameraId));
			List<String> focusModes = cameraParameters.getSupportedFocusModes();
			if (focusModes != null)
			{
				Log.i("video", Build.MODEL);
				if (((Build.MODEL.startsWith("GT-I950"))
						|| (Build.MODEL.endsWith("SCH-I959"))
						|| (Build.MODEL.endsWith("MEIZU MX3"))) && focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE))
				{

					cameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
				}
				else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO))
				{
					cameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
				}
				else
				{
					cameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_FIXED);
				}
			}
		}
		else
		{
			mCamera.setDisplayOrientation(90);
		}
		mCamera.setParameters(cameraParameters);

	}

	@Override
	public void onClick(View v)
	{
		//Next
		if (v.getId() == R.id.recorder_next)
		{
			if (isRecordingStarted)
			{
				rec = false;
				saveRecording();
			}
			else
			{
				initiateRecording(false);
			}
		}
		else if (v.getId() == R.id.recorder_flashlight)
		{
			if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH))
			{
				//showToast(this, "The flash can not be opened");
				return;
			}
			//Flash
			if (isFlashOn)
			{
				isFlashOn = false;
				flashIcon.setSelected(false);
				cameraParameters.setFlashMode(Parameters.FLASH_MODE_OFF);
			}
			else
			{
				isFlashOn = true;
				flashIcon.setSelected(true);
				cameraParameters.setFlashMode(Parameters.FLASH_MODE_TORCH);
			}
			mCamera.setParameters(cameraParameters);
		}
		else if (v.getId() == R.id.recorder_frontcamera)
		{
			//Conversion Camera
			cameraSelection = ((cameraSelection == CameraInfo.CAMERA_FACING_BACK) ? CameraInfo.CAMERA_FACING_FRONT : CameraInfo.CAMERA_FACING_BACK);
			initCameraLayout();

			if (cameraSelection == CameraInfo.CAMERA_FACING_FRONT)
			{
				flashIcon.setVisibility(View.GONE);
			}
			else
			{
				flashIcon.setVisibility(View.VISIBLE);
				if (isFlashOn)
				{
					cameraParameters.setFlashMode(Parameters.FLASH_MODE_TORCH);
					mCamera.setParameters(cameraParameters);
				}
			}
		}
		else if (v.getId() == R.id.recorder_cancel)
		{
			if (recording)
			{
				showCancellDialog();
			}
			else
			{
				videoTheEnd(false);
			}
		}
	}


	/**
	 * End recording
	 *
	 * @param isSuccess
	 */
	public void videoTheEnd(boolean isSuccess)
	{
		releaseResources();
		if (fileVideoPath != null && fileVideoPath.exists() && !isSuccess)
		{
			fileVideoPath.delete();
		}

		returnToCaller(isSuccess);
	}

	/**
	 * Setting Back to Results
	 *
	 * @param valid
	 */
	private void returnToCaller(boolean valid)
	{
		try
		{
			setActivityResult(valid);
			if (valid)
			{
				Intent intent = new Intent(this, FFmpegPreviewActivity.class);
				intent.putExtra("path", strVideoPath);
				intent.putExtra("imagePath", imagePath);
				startActivity(intent);
			}
		}
		catch (Throwable e)
		{
			e.printStackTrace();
		}
		finally
		{
			finish();
		}
	}

	private void setActivityResult(boolean valid)
	{
		Intent resultIntent = new Intent();
		int resultCode;
		if (valid)
		{
			resultCode = RESULT_OK;
			resultIntent.setData(uriVideoPath);
		}
		else
		{
			resultCode = RESULT_CANCELED;
		}

		setResult(resultCode, resultIntent);
	}

	/**
	 * Sign up to the system we recorded video files, this file will be displayed in the sd card
	 */
	private void registerVideo()
	{
		Uri videoTable = Uri.parse(CONSTANTS.VIDEO_CONTENT_URI);

		Util.videoContentValues.put(Video.Media.SIZE, new File(strVideoPath).length());
		try
		{
			uriVideoPath = getContentResolver().insert(videoTable, Util.videoContentValues);
		}
		catch (Throwable e)
		{
			uriVideoPath = null;
			strVideoPath = null;
			e.printStackTrace();
		}
		finally
		{
		}
		Util.videoContentValues = null;
	}


	/**
	 * Save recorded video files
	 */
	private void saveRecording()
	{
		if (isRecordingStarted)
		{
			runAudioThread = false;
			if (!isRecordingSaved)
			{
				isRecordingSaved = true;
				new AsyncStopRecording().execute();
			}
		}
		else
		{
			videoTheEnd(false);
		}
	}

	/**
	 * Determine the total time recorded

	 private synchronized void setTotalVideoTime(){
	 if(totalTime > 0)
	 txtTimer.setText(Util.getRecordingTimeFromMillis(totalTime));

	 } */

	/**
	 * The release of resources, stop recording video and audio
	 */
	private void releaseResources()
	{
		recorderThread.finish();
		isRecordingSaved = true;
		try
		{
			if (videoRecorder != null)
			{
				videoRecorder.stop();
				videoRecorder.release();
			}
		}
		catch (com.googlecode.javacv.FrameRecorder.Exception e)
		{
			e.printStackTrace();
		}

		yuvIplImage = null;
		videoRecorder = null;
		lastSavedframe = null;

		//progressView.putProgressList((int) totalTime);
		progressView.setCurrentState(State.PAUSE);
	}

	/**
	 * When first pressed, recording data initialization
	 *
	 * @param isActionDown
	 */
	private void initiateRecording(boolean isActionDown)
	{
		isRecordingStarted = true;
		firstTime = System.currentTimeMillis();

		recording = true;
		totalPauseTime = 0;
		pausedTime = 0;
	}

	public enum RecorderState
	{
		PRESS(1), LOOSEN(2), CHANGE(3), SUCCESS(4);

		static RecorderState mapIntToValue(final int stateInt)
		{
			for (RecorderState value : RecorderState.values())
			{
				if (stateInt == value.getIntValue())
				{
					return value;
				}
			}
			return PRESS;
		}

		private int mIntValue;

		RecorderState(int intValue)
		{
			mIntValue = intValue;
		}

		int getIntValue()
		{
			return mIntValue;
		}
	}
}