/***
   Copyright (c) 2013 CommonsWare, LLC
  Portions Copyright (C) 2007 The Android Open Source Project

  Licensed under the Apache License, Version 2.0 (the "License"); you may
  not use this file except in compliance with the License. You may obtain
  a copy of the License at
    http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */

package com.commonsware.cwac.camera;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import android.R;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.media.MediaRecorder;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class CameraView extends ViewGroup implements
Camera.PictureCallback {
	static final String TAG="CWAC-Camera";
	private PreviewStrategy previewStrategy;
	private Camera.Size previewSize;
	private Camera camera;
	private Camera.Size preferredPicSize;
	private Camera.Size preferredVidSize;
	private boolean inPreview=false;
	private CameraHost host=null;
	private OnOrientationChange onOrientationChange=null;
	private int displayOrientation=-1;
	private int outputOrientation=-1;
	private int cameraId=-1;
	private MediaRecorder recorder=null;
	private Camera.Parameters previewParams=null;
	private boolean needBitmap=false;
	private boolean needByteArray=false;
	private boolean mTempVar = true;
	private Context mContext = null;

	public CameraView(Context context) {
		super(context);
		mContext = context;
		onOrientationChange=new OnOrientationChange(context);
	}

	public CameraHost getHost() {
		return(host);
	}

	// must call this after constructor, before onResume()

	public void setHost(CameraHost host) {
		this.host=host;

//		if (host.getDeviceProfile().useTextureView()) {
//			previewStrategy=new TexturePreviewStrategy(this);
//		}
//		else {
			previewStrategy=new SurfacePreviewStrategy(this);
//		}
	}

	public void onResume() {
		addView(previewStrategy.getWidget());
		
		if (camera == null) {
			try {
				cameraId=getHost().getCameraId();
				camera=Camera.open(cameraId);
				
				preferredPicSize = getPreferedPicSize();
				preferredVidSize = getPreferedVidSize();

				if (getActivity().getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
					onOrientationChange.enable();
				}


				setCameraDisplayOrientation(cameraId, camera);
			}
			catch (Exception e){
				e.printStackTrace();
			}
			
		}
	}

	public void onPause() {
		previewDestroyed();
		removeView(previewStrategy.getWidget());
		
	}
	
	public void onDestroy() {
		previewDestroyed();
		removeView(previewStrategy.getWidget());
	}

	public int getDisplayOrientation() {
		return(displayOrientation);
	}

	public void lockToLandscape(boolean enable) {
		if (enable) {
			getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
			onOrientationChange.enable();
		}
		else {
			getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
			onOrientationChange.disable();
		}

		setCameraDisplayOrientation(cameraId, camera);
	}

	@Override
	public void onPictureTaken(byte[] data, Camera camera) {
		camera.stopPreview();
		camera.setParameters(previewParams);

		if (data != null) {
			FileOutputStream fos;

			try {
				String filepath = mContext.getFilesDir().getAbsolutePath();
				File f = new File( filepath, "tmpsd34.jpg");                
				fos = new FileOutputStream(f);
				fos.write(data);
				fos.close();
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			//              new ImageCleanupTask(data, cameraId, getHost(),
			//                              getContext().getCacheDir(), needBitmap,
			//                              needByteArray, displayOrientation).start();
		}

	}

	public void restartPreview() {
		if (!inPreview) {
			startPreview();
		}
	}

	public void stopPreviewManual(){
		stopPreview();
	}
	public void takePicture(boolean needBitmap, boolean needByteArray, boolean flash)
	{
		takePicture(needBitmap, needByteArray, this, flash);
	}

	public void takePicture(boolean needBitmap, boolean needByteArray, Camera.PictureCallback jpeg, boolean flash) {
		Log.d("Profiling","Started takepicture " + String.valueOf(new Date().getTime()));
		if (inPreview) {

			this.needBitmap=needBitmap;
			this.needByteArray=needByteArray;

			previewParams=camera.getParameters();

			Camera.Parameters pictureParams=camera.getParameters();
			if (flash)
				pictureParams.setFlashMode(Parameters.FLASH_MODE_ON);
			else
				pictureParams.setFlashMode(Parameters.FLASH_MODE_OFF);
			
			
			if( preferredPicSize != null)
				pictureParams.setPictureSize(preferredPicSize.width, preferredPicSize.height);
			
			pictureParams.setPictureFormat(ImageFormat.JPEG);
			try{
				camera.setParameters(getHost().adjustPictureParameters(pictureParams));
			} catch (Exception e){
				Log.e(TAG,"Error setting parameters", e);
			}

			camera.takePicture(getHost().getShutterCallback(), null, jpeg);
			inPreview=false;
			mTempVar = false;

		}
		Log.d("Profiling","Ended takepicture " + String.valueOf(new Date().getTime()));
	}

	public boolean isRecording() {
		return(recorder != null);
	}

	public void record(boolean flash, boolean ffc) throws Exception {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
			throw new UnsupportedOperationException(
					"Video recording supported only on API Level 11+");
		}
		Parameters parameters = null;
		if(camera != null){
			parameters = camera.getParameters();
			if(flash)
				parameters.setFlashMode(Parameters.FLASH_MODE_TORCH);
			else
				parameters.setFlashMode(Parameters.FLASH_MODE_OFF);
			
			
//			parameters.setPreviewSize(previewSize.width, previewSize.height);
			
			camera.setParameters(parameters);
		    
		}
		stopPreview();
		camera.unlock();

		try { 


					
			recorder=new MediaRecorder();
			recorder.setCamera(camera);
			getHost().configureRecorderAudio(cameraId, recorder);
//			if(!ffc){
	
				recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
				recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
//				if(!ffc){

//					recorder.setVideoSize(640, 480);
//				}else{
					recorder.setVideoSize(previewSize.width, previewSize.height);
//				}
				
				
					
				
//				recorder.setVideoEncodingBitRate(24);
				recorder.setVideoFrameRate(30);
//				recorder.setVideoEncodingBitRate(437500*8);
				recorder.setVideoEncodingBitRate(200000*8);
				
				recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
				
				recorder.setAudioChannels(2);
				recorder.setAudioEncodingBitRate(12200);
				recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);

				
				//recorder.setMaxDuration(9000);
			if(!ffc){
				recorder.setOrientationHint(90);
			}else{
////				recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
//////				
////				CamcorderProfile profile = CamcorderProfile.get(Camera.CameraInfo.CAMERA_FACING_FRONT,CamcorderProfile.QUALITY_HIGH);
////				profile.audioCodec = MediaRecorder.AudioEncoder.AAC;
////				profile.videoCodec = MediaRecorder.VideoEncoder.H264;
////				recorder.setProfile(profile);
////				recorder.setMaxDuration(9000);
				recorder.setOrientationHint(270);
			}
			
			recorder.setOutputFile(mContext.getApplicationContext().getFilesDir()+"/pingTesting.mp4");
			
			previewStrategy.attach(recorder);
			recorder.prepare();
			recorder.start();
			
			
		}
		catch (IOException e) {
			recorder.release();
			recorder=null;
			throw e;
		}
	}

	public void stopRecording() throws IOException {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
			throw new UnsupportedOperationException(
					"Video recording supported only on API Level 11+");
		}

		MediaRecorder tempRecorder=recorder;

		recorder=null;
		tempRecorder.stop();
	
		tempRecorder.release();
		camera.reconnect();
	}

	public void autoFocus() {
		if (inPreview) {
			camera.autoFocus(getHost());
		}
	}

	public void cancelAutoFocus() {
		camera.cancelAutoFocus();
	}

	public boolean isAutoFocusAvailable() {
		return(inPreview);
	}

	public String getFlashMode() {
		return(camera.getParameters().getFlashMode());
	}

	// based on CameraPreview.java from ApiDemos

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		
		final int width=
				resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec);
		final int height=
				resolveSize(getSuggestedMinimumHeight(), heightMeasureSpec);
		setMeasuredDimension(width, height);

		if (previewSize == null && camera != null) {
			if (getHost().mayUseForVideo()) {
				Camera.Size deviceHint=
						DeviceProfile.getInstance()
						.getPreferredPreviewSizeForVideo(getDisplayOrientation(),
								width,
								height,
								camera.getParameters());

				previewSize=
						getHost().getPreferredPreviewSizeForVideo(getDisplayOrientation(),
								width,
								height,
								camera.getParameters(),
								deviceHint);
			}

			if (previewSize == null
					|| previewSize.width * previewSize.height < 65536) {
				previewSize=
						getHost().getPreviewSize(getDisplayOrientation(), width,
								height, camera.getParameters());
			}

			if (previewSize != null) {
				
				previewSize.width = preferredVidSize.width;
				previewSize.height = preferredVidSize.height;
				//        android.util.Log.e("CameraView",
						//                           String.format("%d x %d", previewSize.width,1280
								//                                         previewSize.height));
			}
		}
	}

	// based on CameraPreview.java from ApiDemos

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		if (changed && getChildCount() > 0) {
			final View child=getChildAt(0);
			final int width=r - l;
			final int height=b - t;
			int previewWidth=width;
			int previewHeight=height;

			// handle orientation

			if (previewSize != null) {
				if (getDisplayOrientation() == 90
						|| getDisplayOrientation() == 270) {
					previewWidth=previewSize.height;
					previewHeight=previewSize.width;
				}
				else {
					previewWidth=previewSize.width;
					previewHeight=previewSize.height;
				}
			}

			// Center the child SurfaceView within the parent.
			if (width * previewHeight > height * previewWidth) {
				final int scaledChildWidth=
						previewWidth * height / previewHeight;

				if(mTempVar){
					child.layout((width - scaledChildWidth) / 2, 0,
							(width + scaledChildWidth) / 2, height);
				}
			}
			else {
				final int scaledChildHeight=
						previewHeight * width / previewWidth;
				if(mTempVar){
					child.layout(0, (height - scaledChildHeight) / 2, width,
							(height + scaledChildHeight) / 2);
				}
			}
		}
	}

	void previewCreated() {
		try {
			previewStrategy.attach(camera);
		}
		catch (Exception e) {
			getHost().handleException(e);
		}
	}

	void previewDestroyed() {
		if (camera != null) {
			previewStopped();
			camera.release();
			camera=null;
		}
	}

	public void previewReset(int width, int height) {
		previewStopped();
		initPreview(width, height);
	}

	private void previewStopped() {
		if (inPreview) {
			stopPreview();
		}
	}

	public void initPreview(int w, int h) {
		
		try{

			Camera.Parameters parameters=camera.getParameters();
			camera.getParameters().getSupportedFocusModes();
			parameters.setPreviewSize(previewSize.width, previewSize.height);
			
			//		parameters.setPreviewSize(640, 480);
			//		Size s =parameters.getSupportedPreviewSizes().get(parameters.getSupportedPreviewSizes().size()-1);
			//		parameters.setPreviewSize(s.width,s.height);
			requestLayout();
			
			camera.setParameters(getHost().adjustPreviewParameters(parameters));
			startPreview();
			setManuallyFocusMode(parameters);
		} catch (Exception e){
			e.printStackTrace();
		}
	}

	private void startPreview() {
		camera.startPreview();
		inPreview=true;
		getHost().autoFocusAvailable();
	}

	private void stopPreview() {
		inPreview=false;
		getHost().autoFocusUnavailable();
		camera.stopPreview();
	}

	// based on
	// http://developer.android.com/reference/android/hardware/Camera.html#setDisplayOrientation(int)
	// and http://stackoverflow.com/a/10383164/115145

	private void setCameraDisplayOrientation(int cameraId,
			android.hardware.Camera camera) {
		Camera.CameraInfo info=new Camera.CameraInfo();
		int rotation=
				getActivity().getWindowManager().getDefaultDisplay()
				.getRotation();
		int degrees=0;
		DisplayMetrics dm=new DisplayMetrics();

		Camera.getCameraInfo(cameraId, info);
		getActivity().getWindowManager().getDefaultDisplay().getMetrics(dm);

		switch (rotation) {
		case Surface.ROTATION_0:
			degrees=0;
			break;
		case Surface.ROTATION_90:
			degrees=90;
			break;
		case Surface.ROTATION_180:
			degrees=180;
			break;
		case Surface.ROTATION_270:
			degrees=270;
			break;
		}

		if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
			displayOrientation=(info.orientation + degrees) % 360;
			displayOrientation=(360 - displayOrientation) % 360;
			
		}
		else {
			displayOrientation=(info.orientation - degrees + 360) % 360;
		}

		boolean wasInPreview=inPreview;

		if (inPreview) {
			stopPreview();
		}

		camera.setDisplayOrientation(displayOrientation);

		if (wasInPreview) {
			startPreview();
		}

		if (getActivity().getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
			outputOrientation=
					getCameraPictureRotation(getActivity().getWindowManager()
							.getDefaultDisplay()
							.getOrientation());
		}
		else if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
			outputOrientation=(360 - displayOrientation) % 360;
			
		}
		else {
			outputOrientation=displayOrientation;
		}

		Camera.Parameters params=camera.getParameters();

		params.setRotation(outputOrientation);
		camera.setParameters(params);
	}

	// based on:
	// http://developer.android.com/reference/android/hardware/Camera.Parameters.html#setRotation(int)

	private int getCameraPictureRotation(int orientation) {
		Camera.CameraInfo info=new Camera.CameraInfo();
		Camera.getCameraInfo(cameraId, info);
		int rotation=0;

		orientation=(orientation + 45) / 90 * 90;

		if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
			rotation=(info.orientation - orientation + 360) % 360;
		}
		else { // back-facing camera
		}

		return(rotation);
	}

	Activity getActivity() {
		return((Activity)getContext());
	}

	private class OnOrientationChange extends OrientationEventListener {
		public OnOrientationChange(Context context) {
			super(context);
			disable();
		}

		@Override
		public void onOrientationChanged(int orientation) {
			
			if (camera != null) {
				int newOutputOrientation=getCameraPictureRotation(orientation);

				if (newOutputOrientation != outputOrientation) {
					outputOrientation=newOutputOrientation;
					try{
						Camera.Parameters params=camera.getParameters();
						Log.d("CameraDebug", "The params are "+params.toString());
						params.setRotation(outputOrientation);
						camera.setParameters(params);
					}catch(Exception e){
						Log.e("CameraView"," Error on orientation changed", e);
					}
					
				}
			}
		}
	}
	
	
	private Size getPreferedPicSize(){
		int MAXWIDTH = 640;
		int MAXHEIGHT = 480;
		Camera.Parameters pictureParams = camera.getParameters();
		List<Size> sizes = pictureParams.getSupportedPictureSizes();

		return getPreferedSize(sizes, MAXWIDTH, MAXHEIGHT);
	}
	
	private Size getPreferedVidSize(){
		int MAXWIDTH = 640;
		int MAXHEIGHT = 480;
		Camera.Parameters pictureParams = camera.getParameters();
		List<Size> sizes = pictureParams.getSupportedPreviewSizes();

		return getPreferedSize(sizes, MAXWIDTH, MAXHEIGHT);
	}
	
	private Size getPreferedSize(List<Size> sizes, int maxwidth, int maxheight){
		Size result = null;
		int width = 0;
		int height = 0;

		for (Camera.Size size : sizes) {
//	    	Log.d("XXX", "width:"+size.width + " height: " + size.height );
	    	if ( size.width * size.height <=  maxwidth * maxheight )
	    		if (size.width * size.height >= width * height){
	    			result = size;
	    			width = result.width;
	    			height = result.height;
	    		}
	    }
		return result;
	}
	

	

	@SuppressLint("NewApi")
	public void submitFocusAreaRect(final Rect touchRect)
	{
		try{
			
			Camera.Parameters cameraParameters = camera.getParameters();
		    //camera.cancelAutoFocus();
		    if(android.os.Build.VERSION.SDK_INT >= 14){
		    	if (cameraParameters.getMaxNumFocusAreas() == 0)
			    {
			        return;
			    }
			    ArrayList<Camera.Area> focusAreas = new ArrayList<Camera.Area>();
			    focusAreas.add(new Camera.Area(touchRect, 1000));

			    List<String> supportedFocusModes = cameraParameters.getSupportedFocusModes();
				if(supportedFocusModes.contains(Parameters.FOCUS_MODE_AUTO)){
					cameraParameters.setFocusMode(Parameters.FOCUS_MODE_AUTO);
					//camera.autoFocus(getHost());
				}
			    cameraParameters.setFocusAreas(focusAreas);
			    cameraParameters.setMeteringAreas(focusAreas);
			    camera.setParameters(cameraParameters);
			    camera.autoFocus(getHost());
		    }
		    
		    // Start the autofocus operation
		    setManuallyFocusMode(cameraParameters);
		    
		}catch (Exception e){
			return ;
		}
	    
	}
	
	public void setManuallyFocusMode(Camera.Parameters parameters){
		List<String> supportedFocusModes = parameters.getSupportedFocusModes();
		
		if(supportedFocusModes.contains(Parameters.FOCUS_MODE_AUTO)){
			parameters.setFocusMode(Parameters.FOCUS_MODE_AUTO);
			//camera.autoFocus(getHost());
		}
		
		camera.setParameters(parameters);
		if(!supportedFocusModes.contains("off"))
			camera.autoFocus(getHost());
	}
}
