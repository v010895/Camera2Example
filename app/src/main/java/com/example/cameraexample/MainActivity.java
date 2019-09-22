package com.example.cameraexample;

import android.Manifest;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends AppCompatActivity {
  private static final String TAG = "CameraDebug";
  private Button takePictureButton;
  private Button resetImg;
  private TextureView textureView;
  private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
  private int imageNumber;
  private int recordImageNumber;
  private ImageReader mImageReader;

  static {
    ORIENTATIONS.append(Surface.ROTATION_0, 90);
    ORIENTATIONS.append(Surface.ROTATION_90, 0);
    ORIENTATIONS.append(Surface.ROTATION_180, 270);
    ORIENTATIONS.append(Surface.ROTATION_270, 180);
  }

  private String cameraId;
  protected CameraDevice cameraDevice;
  protected CameraCaptureSession cameraCaptureSessions;
  protected CameraCaptureSession recordCaptureSessions;
  protected CaptureRequest captureRequest;
  protected CaptureRequest.Builder captureRequestBuilder;
  protected CaptureRequest.Builder recordRequestBuilder;
  private Size imageDimension;
  ImageView imgDealed;
  private ImageReader imageReader;
  Bitmap  currentImage;
  private byte[] imageByteArray;
  private File file;
  private static final int INIT_FINISHED=1;
  private static final int REQUEST_CAMERA_PERMISSION = 200;
  private boolean mFlashSupported;
  private Handler mBackgroundHandler;
  private HandlerThread mBackgroundThread;
  private Size mPreviewSize;
  private Image cameraImage;
  private boolean recordImage;
  private File folder;
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    imgDealed = (ImageView) findViewById(R.id.texture);
    assert textureView != null;
    takePictureButton = (Button) findViewById(R.id.btn_takepicture);
    resetImg = (Button) findViewById(R.id.btn_resetImgNumber);
    imageNumber = 0;
    recordImageNumber = 0;
    recordImage = false;
    mImageReader = ImageReader.newInstance(640, 480, ImageFormat.JPEG, 2);
    assert takePictureButton != null;
    imageByteArray = new byte[640*480];
    folder = new File(Environment.getExternalStorageDirectory() + File.separator + "calibration");
    if (!folder.exists()) {
      boolean success = folder.mkdir();
      if (!success) {
        Toast.makeText(MainActivity.this, "Cannot create folder", Toast.LENGTH_SHORT).show();
        return;
      }
    }
    takePictureButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        takePicture();
      }
    });
    resetImg.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        imageNumber = 0;
        recordImageNumber =0;
      }
    });
    openCamera(640,480);
    new Thread(new Runnable() {
      @Override
      public void run() {
        myHandler.sendEmptyMessage(INIT_FINISHED);
      }
    }).start();
  }
  private void save(byte[] bytes, File file) throws IOException {
    OutputStream output = null;
    try {
      output = new FileOutputStream(file);
      output.write(bytes);
    } finally {
      if (null != output) {
        output.close();
      }
    }
  }
  Handler myHandler = new Handler() {
    public void handleMessage(Message msg) {
      switch (msg.what) {
        case INIT_FINISHED:
          Toast.makeText(MainActivity.this,
              "init has been finished!",
              Toast.LENGTH_LONG).show();
          new Thread(new Runnable() {

            @Override
            public void run() {
              while (true) {
                do {
                  cameraImage = mImageReader.acquireLatestImage();

                } while (cameraImage == null);
                byte[] bitmapData;

                String fileName = String.format("calibration/image%07d.jpg", recordImageNumber);
                final File file = new File(Environment.getExternalStorageDirectory() + File.separator + fileName);

                if (cameraImage.getFormat() == ImageFormat.JPEG) {
                  Image.Plane[] planes = cameraImage.getPlanes();
                  if (planes != null) {
                    Image.Plane YPlane = planes[0];
                    if (YPlane != null) {
                      ByteBuffer byteBuffer = YPlane.getBuffer();
                      if (byteBuffer != null) {
                        final byte[] data = new byte[byteBuffer.capacity()];
                        byteBuffer.get(data);
                        if(recordImage) {
                          try {
                            save(data, file);
                            recordImageNumber++;
                          } catch (IOException e) {
                            e.printStackTrace();
                          }
                        }
                        cameraImage.close();
                        currentImage = BitmapFactory.decodeByteArray(data, 0, data.length);

                      }
                    }
                  }
                }

                runOnUiThread(new Runnable() {
                  @Override
                  public void run() {
                    // TODO Auto-generated method stub
                    imgDealed.setImageBitmap(currentImage);
                    //Bitmap bmp = BitmapFactory.decodeResource(getResources(),R.id.img_dealed);


                  }
                });
              }
            }

          }).start();
          break;
      }
      super.handleMessage(msg);
    }
  };

  private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
    @Override
    public void onOpened(CameraDevice camera) {
      //This is called when the camera is open
      Log.e(TAG, "onOpened");
      cameraDevice = camera;
      createRecord();
    }

    @Override
    public void onDisconnected(CameraDevice camera) {
      cameraDevice.close();
    }

    @Override
    public void onError(CameraDevice camera, int error) {
      cameraDevice.close();
      cameraDevice = null;
    }
  };

  protected void startBackgroundThread() {
    mBackgroundThread = new HandlerThread("Camera Background");
    mBackgroundThread.start();
    mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
  }

  protected void stopBackgroundThread() {
    mBackgroundThread.quitSafely();
    try {
      mBackgroundThread.join();
      mBackgroundThread = null;
      mBackgroundHandler = null;
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  private Size getPreferredPreviewSize(Size[] mapSizes, int width, int height) {
    List<Size> collectorSizes = new ArrayList<>();
    for (Size option : mapSizes) {
      if (width > height) {
        if (option.getWidth() > width &&
            option.getHeight() > height) {
          collectorSizes.add(option);
        }
      } else {
        if (option.getWidth() > height &&
            option.getHeight() > width) {
          collectorSizes.add(option);
        }
      }
    }
    if (collectorSizes.size() > 0) {
      return Collections.min(collectorSizes, new Comparator<Size>() {
        @Override
        public int compare(Size lhs, Size rhs) {
          return Long.signum(lhs.getWidth() * lhs.getHeight() - rhs.getWidth() * rhs.getHeight());
        }
      });
    }
    return mapSizes[0];
  }
  protected void createRecord(){
    if (null == cameraDevice) {
      Log.e(TAG, "cameraDevice is null");
      return;
    }
    CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
    try{
      CameraCharacteristics characteristics = manager.getCameraCharacteristics((cameraDevice.getId()));
      int sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
      recordRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
      int rotation = getWindowManager().getDefaultDisplay().getRotation();
      int surfaceRotation = ORIENTATIONS.get(rotation);
      int jpegOrientation =
          (surfaceRotation + sensorOrientation + 270) % 360;
      //recordRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation);
      recordRequestBuilder.addTarget(mImageReader.getSurface());
      recordRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);

      cameraDevice.createCaptureSession(Arrays.asList(mImageReader.getSurface()), new CameraCaptureSession.StateCallback(){
        @Override
        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
          //The camera is already closed
          if (null == cameraDevice) {
            return;
          }
          // When the session is ready, we start displaying the preview.
          recordCaptureSessions = cameraCaptureSession;
          updateRecord();
        }
        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
          Toast.makeText(MainActivity.this, "Configuration change", Toast.LENGTH_SHORT).show();
        }
      }, null);
    }
    catch(CameraAccessException e){
      e.printStackTrace();
    }

  }
  protected void takePicture() {
    if (null == cameraDevice) {
      Log.e(TAG, "cameraDevice is null");
      return;
    }
    CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
    try {
      CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraDevice.getId());
      Size[] jpegSizes = null;
      StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
      int sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
      jpegSizes = map.getOutputSizes(SurfaceTexture.class);
      int width = 640;
      int height = 480;
      if (jpegSizes != null && 0 < jpegSizes.length) {
        width = jpegSizes[0].getWidth();
        height = jpegSizes[0].getHeight();
      }
      ImageReader reader = ImageReader.newInstance(640, 480, ImageFormat.JPEG, 1);
      List<Surface> outputSurfaces = new ArrayList<Surface>(2);
      outputSurfaces.add(reader.getSurface());
      outputSurfaces.add(new Surface(textureView.getSurfaceTexture()));
      final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
      captureBuilder.addTarget(reader.getSurface());
      captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
      // Orientation
      int rotation = getWindowManager().getDefaultDisplay().getRotation();
      int surfaceRotation = ORIENTATIONS.get(rotation);
      int jpegOrientation =
          (surfaceRotation + sensorOrientation + 270) % 360;
      captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation);
      final File folder = new File(Environment.getExternalStorageDirectory() + File.separator + "calibration");
      if (!folder.exists()) {
        boolean success = folder.mkdir();
        if (!success) {
          Toast.makeText(MainActivity.this, "Cannot create folder", Toast.LENGTH_SHORT).show();
          return;
        }
      }
      String fileName = String.format("calibration/image%07d.jpg", imageNumber);
      imageNumber++;
      final File file = new File(Environment.getExternalStorageDirectory() + File.separator + fileName);
      ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
          Image image = null;
          try {
            image = reader.acquireLatestImage();
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.capacity()];
            buffer.get(bytes);
            save(bytes);
          } catch (FileNotFoundException e) {
            e.printStackTrace();
          } catch (IOException e) {
            e.printStackTrace();
          } finally {
            if (image != null) {
              image.close();
            }
          }
        }

        private void save(byte[] bytes) throws IOException {
          OutputStream output = null;
          try {
            output = new FileOutputStream(file);
            output.write(bytes);
          } finally {
            if (null != output) {
              output.close();
            }
          }
        }
      };
      reader.setOnImageAvailableListener(readerListener, mBackgroundHandler);
      final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
          super.onCaptureCompleted(session, request, result);
          Toast.makeText(MainActivity.this, "Saved:" + file, Toast.LENGTH_SHORT).show();
          createRecord();
        }
      };
      cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(CameraCaptureSession session) {
          try {
            session.capture(captureBuilder.build(), captureListener, mBackgroundHandler);
          } catch (CameraAccessException e) {
            e.printStackTrace();
          }
        }

        @Override
        public void onConfigureFailed(CameraCaptureSession session) {
        }
      }, mBackgroundHandler);
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
  }



  private void openCamera(int width, int height) {
    CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
    Log.e(TAG, "is camera open");
    try {
      cameraId = manager.getCameraIdList()[0];
      CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
      StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
      mPreviewSize = getPreferredPreviewSize(map.getOutputSizes(SurfaceTexture.class), width, height);
      //transformImage(width, height);
      // Add permission for camera and let user grant the permission
      if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CAMERA_PERMISSION);
        return;
      }
      manager.openCamera(cameraId, stateCallback, null);
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
    Log.e(TAG, "openCamera!!");
  }
  protected void updateRecord(){
    if (null == cameraDevice) {
      Log.e(TAG, "updatePreview error, return");
    }

    recordRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
    try {
      recordCaptureSessions.setRepeatingRequest(recordRequestBuilder.build(), null, mBackgroundHandler);
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
  }


  //For horizental orientation
  private void transformImage(int width, int height) {
    if (mPreviewSize == null || textureView == null) {
      return;
    }
    Matrix matrix = new Matrix();
    int rotation = getWindowManager().getDefaultDisplay().getRotation();
    RectF textureRectF = new RectF(0, 0, width, height);
    RectF previewRectF = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
    float centerX = textureRectF.centerX();
    float centerY = textureRectF.centerY();
    if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
      previewRectF.offset(centerX - previewRectF.centerX(),
          centerY - previewRectF.centerY());
      matrix.setRectToRect(textureRectF, previewRectF, Matrix.ScaleToFit.FILL);
      float scale = Math.max((float) width / mPreviewSize.getWidth(),
          (float) height / mPreviewSize.getHeight());
      matrix.postScale(scale, scale, centerX, centerY);
      matrix.postRotate(90 * (rotation - 2), centerX, centerY);
    }
    textureView.setTransform(matrix);
  }

  private void closeCamera() {
    if (null != cameraDevice) {
      cameraDevice.close();
      cameraDevice = null;
    }
    if (null != imageReader) {
      imageReader.close();
      imageReader = null;
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    if (requestCode == REQUEST_CAMERA_PERMISSION) {
      if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
        // close the app
        Toast.makeText(MainActivity.this, "Sorry!!!, you can't use this app without granting permission", Toast.LENGTH_LONG).show();
        finish();
      }
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    Log.e(TAG, "onResume");
    startBackgroundThread();

  }

  @Override
  protected void onPause() {
    Log.e(TAG, "onPause");
    //closeCamera();
    stopBackgroundThread();
    super.onPause();
  }
}

