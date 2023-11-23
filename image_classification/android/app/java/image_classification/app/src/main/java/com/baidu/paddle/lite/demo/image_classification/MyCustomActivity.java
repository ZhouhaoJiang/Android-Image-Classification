package com.baidu.paddle.lite.demo.image_classification;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class MyCustomActivity extends AppCompatActivity {
    private ImageAdapter imageAdapter;
    private List<Bitmap> envelopeImages = new ArrayList<>();
    private List<String[]> resultsList = new ArrayList<>();
    private static final String TAG = MainActivity.class.getSimpleName(); // 用于标记日志
    public static final int OPEN_GALLERY_REQUEST_CODE = 0; // 用于标记打开相册的请求
    public static final int TAKE_PHOTO_REQUEST_CODE = 1; // 用于标记拍照的请求

    public static final int REQUEST_LOAD_MODEL = 0; // 用于标记加载模型的请求
    public static final int REQUEST_RUN_MODEL = 1; // 用于标记运行模型的请求
    public static final int RESPONSE_LOAD_MODEL_SUCCESSED = 0; // 用于标记加载模型成功的响应
    public static final int RESPONSE_LOAD_MODEL_FAILED = 1; //  用于标记加载模型失败的响应
    public static final int RESPONSE_RUN_MODEL_SUCCESSED = 2; // 用于标记运行模型成功的响应
    public static final int RESPONSE_RUN_MODEL_FAILED = 3;  // 用于标记运行模型失败的响应

    protected ProgressDialog pbLoadModel = null; // 用于显示加载模型的进度条
    protected ProgressDialog pbRunModel = null; // 用于显示运行模型的进度条

    protected Handler receiver = null; // 用于接收来自 worker 线程的消息
    protected Handler sender = null; // 用于向 worker 线程发送消息
    protected HandlerThread worker = null; // 用于加载模型和运行模型的 worker 线程

    // UI components of image classification
    protected TextView tvInputSetting; // 用于显示模型的输入设置
    protected ImageView ivInputImage; // 用于显示模型的输入图片
    protected TextView tvTop1Result; // 用于显示模型的前三个分类结果
    protected TextView tvTop2Result; // 用于显示模型的前三个分类结果
    protected TextView tvTop3Result; // 用于显示模型的前三个分类结果
    protected TextView tvInferenceTime; // 用于显示模型的推理时间
    // protected Switch mSwitch;

    // Model settings of image classification
    protected String modelPath = ""; // 模型路径
    protected String labelPath = ""; // 标签路径
    protected String imagePath = ""; // 测试图片路径
    protected int cpuThreadNum = 1; // CPU 线程数
    protected String cpuPowerMode = ""; // CPU 功耗模式
    protected String inputColorFormat = ""; // 输入图片的颜色格式
    protected long[] inputShape = new long[]{}; // 输入图片的形状
    protected float[] inputMean = new float[]{}; // 输入图片的均值
    protected float[] inputStd = new float[]{}; // 输入图片的标准差
    protected boolean useGpu = false; // 是否使用 GPU

    protected Predictor predictor = new Predictor(); // 用于加载和运行模型的 Predictor

    private static final int MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 1; // 用于标记读取外部存储的请求

    private static final int BATCH_SIZE = 10; // 每批处理的图片数量


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_custom);
        checkStoragePermission();

        Button btnStartScanning = findViewById(R.id.btn_start_scanning);
        btnStartScanning.setOnClickListener(v -> startScanning());

        ListView listView = findViewById(android.R.id.list);
        imageAdapter = new ImageAdapter(MyCustomActivity.this, new ArrayList<>(), resultsList);
        listView.setAdapter(imageAdapter);

        // 为模式加载和推理准备工作线程
        receiver = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case RESPONSE_LOAD_MODEL_SUCCESSED:
                        pbLoadModel.dismiss(); // 关闭加载模型的进度条
//                        onLoadModelSuccessed(); //  加载模型成功后的处理
                        break;
                    case RESPONSE_LOAD_MODEL_FAILED:
                        pbLoadModel.dismiss(); // 关闭加载模型的进度条
//                        Toast.makeText(MainActivity.this, "Load model failed!", Toast.LENGTH_SHORT).show();
                        onLoadModelFailed(); // 加载模型失败后的处理
                        break;
                    case RESPONSE_RUN_MODEL_SUCCESSED:
                        pbRunModel.dismiss(); // 关闭运行模型的进度条
//                        onRunModelSuccessed(); // 运行模型成功后的处理
                        break;
                    case RESPONSE_RUN_MODEL_FAILED:
                        pbRunModel.dismiss(); // 关闭运行模型的进度条
//                        Toast.makeText(MainActivity.this, "Run model failed!", Toast.LENGTH_SHORT).show();
                        onRunModelFailed(); // 运行模型失败后的处理
                        break;
                    default:
                        break;
                }
            }
        };

        worker = new HandlerThread("Predictor Worker"); // 创建工作线程
        worker.start(); // 启动工作线程

        sender = new Handler(worker.getLooper()) {
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case REQUEST_LOAD_MODEL:
                        // 加载模型并重新加载测试图片
                        if (onLoadModel()) {
                            receiver.sendEmptyMessage(RESPONSE_LOAD_MODEL_SUCCESSED);
                        } else {
                            receiver.sendEmptyMessage(RESPONSE_LOAD_MODEL_FAILED);
                        }
                        break;
                    case REQUEST_RUN_MODEL:
                        // Run model if model is loaded
                        if (onRunModel()) {
                            receiver.sendEmptyMessage(RESPONSE_RUN_MODEL_SUCCESSED);
                        } else {
                            receiver.sendEmptyMessage(RESPONSE_RUN_MODEL_FAILED);
                        }
                        break;
                    default:
                        break;
                }
            }
        };

        // 设定参数
//        modelPath = "models/mobilenet_v1_for_cpu/model.nb";
//        labelPath = "labels/synset_words.txt";
//        cpuThreadNum = 1;
//        cpuPowerMode = "LITE_POWER_HIGH";
//        inputColorFormat = "RGB";
//        inputShape = new long[]{1, 3, 224, 224};
//        inputMean = new float[]{0.485f, 0.456f, 0.406f};
//        inputStd = new float[]{0.229f, 0.224f, 0.225f};
//        loadModel();
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean settingsChanged = false;
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String model_path = sharedPreferences.getString(getString(R.string.MODEL_PATH_KEY),
              getString(R.string.MODEL_PATH_DEFAULT));
        String label_path = sharedPreferences.getString(getString(R.string.LABEL_PATH_KEY),
              getString(R.string.LABEL_PATH_DEFAULT));
        String image_path = sharedPreferences.getString(getString(R.string.IMAGE_PATH_KEY),
              getString(R.string.IMAGE_PATH_DEFAULT));
        settingsChanged |= !model_path.equalsIgnoreCase(modelPath);
        settingsChanged |= !label_path.equalsIgnoreCase(labelPath);
        settingsChanged |= !image_path.equalsIgnoreCase(imagePath);
        int cpu_thread_num = Integer.parseInt(sharedPreferences.getString(getString(R.string.CPU_THREAD_NUM_KEY),
              getString(R.string.CPU_THREAD_NUM_DEFAULT)));
        settingsChanged |= cpu_thread_num != cpuThreadNum;
        String cpu_power_mode =
              sharedPreferences.getString(getString(R.string.CPU_POWER_MODE_KEY),
                    getString(R.string.CPU_POWER_MODE_DEFAULT));
        settingsChanged |= !cpu_power_mode.equalsIgnoreCase(cpuPowerMode);
        String input_color_format =
              sharedPreferences.getString(getString(R.string.INPUT_COLOR_FORMAT_KEY),
                    getString(R.string.INPUT_COLOR_FORMAT_DEFAULT));
        settingsChanged |= !input_color_format.equalsIgnoreCase(inputColorFormat);
        long[] input_shape =
              Utils.parseLongsFromString(sharedPreferences.getString(getString(R.string.INPUT_SHAPE_KEY),
                    getString(R.string.INPUT_SHAPE_DEFAULT)), ",");
        float[] input_mean =
              Utils.parseFloatsFromString(sharedPreferences.getString(getString(R.string.INPUT_MEAN_KEY),
                    getString(R.string.INPUT_MEAN_DEFAULT)), ",");
        float[] input_std =
              Utils.parseFloatsFromString(sharedPreferences.getString(getString(R.string.INPUT_STD_KEY)
                    , getString(R.string.INPUT_STD_DEFAULT)), ",");
        settingsChanged |= input_shape.length != inputShape.length; // 判断输入图片的形状是否改变
        settingsChanged |= input_mean.length != inputMean.length; //    判断输入图片的均值是否改变
        settingsChanged |= input_std.length != inputStd.length;
        if (!settingsChanged) {
            for (int i = 0; i < input_shape.length; i++) {
                settingsChanged |= input_shape[i] != inputShape[i];
            }
            for (int i = 0; i < input_mean.length; i++) {
                settingsChanged |= input_mean[i] != inputMean[i];
            }
            for (int i = 0; i < input_std.length; i++) {
                settingsChanged |= input_std[i] != inputStd[i];
            }
        }
        if (settingsChanged) {
            modelPath = model_path;
            labelPath = label_path;
            imagePath = image_path;
            cpuThreadNum = cpu_thread_num;
            cpuPowerMode = cpu_power_mode;
            inputColorFormat = input_color_format;
            inputShape = input_shape;
            inputMean = input_mean;
            inputStd = input_std;
            if (useGpu) {
                modelPath = modelPath.split("/")[0] + "/mobilenet_v1_for_gpu";
            } else {
                modelPath = modelPath.split("/")[0] + "/mobilenet_v1_for_cpu";
            }
            // Reload model if configure has been changed
            loadModel();
        }
    }

    public void loadModel() {
        pbLoadModel = ProgressDialog.show(MyCustomActivity.this, "", "Loading model...", false, false);
        sender.sendEmptyMessage(REQUEST_LOAD_MODEL);
    }

    public boolean onLoadModel() {
        return predictor.init(this, modelPath, labelPath, cpuThreadNum,
              cpuPowerMode,
              inputColorFormat,
              inputShape, inputMean,
              inputStd);
    }

    public boolean onRunModel() {
        return predictor.isLoaded() && predictor.runModel();
    }


    public void runModel() {
        pbRunModel = ProgressDialog.show(this, "", "Running model...", false, false);
        sender.sendEmptyMessage(REQUEST_RUN_MODEL);
    }

    public void onLoadModelFailed() {
    }

    public void onRunModelFailed() {
    }

    public void onRunModelSuccessed() {
        // Obtain results and update UI
        tvInferenceTime.setText("Inference time: " + predictor.inferenceTime() + " ms");
        Bitmap inputImage = predictor.inputImage();
        if (inputImage != null) {
            ivInputImage.setImageBitmap(inputImage);
        }
    }


    public void onLoadModelSuccessed() {
        // Load test image from path and run model
        try {
            if (imagePath.isEmpty()) {
                return;
            }
            Bitmap image = null;
            // 读取测试图片文件，如果模式路径的第一个字符是'/'，则从自定义路径读取测试图片文件，否则从 assets 读取测试图片文件
            // 从 assets 读取测试图片文件
            if (!imagePath.substring(0, 1).equals("/")) {
                InputStream imageStream = getAssets().open(imagePath);
                image = BitmapFactory.decodeStream(imageStream);
            } else {
                if (!new File(imagePath).exists()) {
                    return;
                }
                image = BitmapFactory.decodeFile(imagePath);
            }
            if (image != null && predictor.isLoaded()) {
                predictor.setInputImage(image);
                runModel();
            }
        } catch (IOException e) {
//            Toast.makeText(MainActivity.this, "Load image failed!", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    // 从相册中选择图片
    public void onImageChanged(Bitmap image) {
        // Rerun model if users pick test image from gallery or camera
        if (image != null && predictor.isLoaded()) {
            predictor.setInputImage(image);
            runModel();
        }
    }

    private void checkStoragePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
              != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                  new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                  MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScanning();
            } else {
                Toast.makeText(this, "需要存储权限来访问相册", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void startScanning() {
        displayTotalImageCount();
        displayCurrentImageCount(0);
        int totalImages = getImageCountFromGallery();
        processImagesAsync(0, totalImages);
    }


    private void processImagesAsync(int startIndex, int totalImages) {
        new Thread(() -> {
            List<Bitmap> batchImages = getImagesFromGallery(startIndex, BATCH_SIZE);
            List<Bitmap> envelopeImages = new ArrayList<>();
            List<String[]> resultsList = new ArrayList<>();


            for (Bitmap image : batchImages) {
                predictor.setInputImage(image);
                if (predictor.runModel()) {
                    String top1Result = predictor.top1Result();
                    String top2Result = predictor.top2Result();
                    String top3Result = predictor.top3Result();
                    Log.d(TAG, "==========================扫描top1Result: " + top1Result);
                    Log.d(TAG, "==========================扫描top2Result: " + top2Result);
                    Log.d(TAG, "==========================扫描top3Result: " + top3Result);
                    // 包含envelope并且置信度大于0.5
                    try {
                        // 假设 topResult 的格式是 "envelope - 0.583"
//                        if (top1Result.contains("website") || top1Result.contains("envelope") || top1Result.contains("web site")) {
                        if (top1Result.contains("envelope")) {
                            String[] parts = top1Result.split("-");
                            if (parts.length > 1) {
                                float confidence = Float.parseFloat(parts[1].trim());
                                if (confidence > 0.5) {
                                    // 添加每张图片的 top1, top2, top3 结果到 resultsList
                                    resultsList.add(new String[]{top1Result, top2Result, top3Result});
                                    envelopeImages.add(image);
                                    Log.d(TAG, "==========================包含envelopeImages: " + envelopeImages.size());
                                }
                            }
                        }
                    } catch (NumberFormatException e) {
                        // 处理异常
                        Log.e(TAG, "解析置信度时出错: " + e.getMessage());
                    }
                }
            }

            runOnUiThread(() -> {
                imageAdapter.addItems(envelopeImages, resultsList);
                imageAdapter.notifyDataSetChanged();
                displayCurrentImageCount(startIndex + batchImages.size()); // 更新当前图片计数
            });

            if (startIndex + BATCH_SIZE < totalImages) {
                Log.d(TAG, "==========================继续处理下一批图片processImagesAsync: " + (startIndex + BATCH_SIZE));
                // 继续处理下一批图片
                processImagesAsync(startIndex + BATCH_SIZE, totalImages);
            }
        }).start();
    }

    // 显示相册中图片的总数
    private void displayTotalImageCount() {
        int totalImages = getImageCountFromGallery();

        TextView totalSizeTextView = findViewById(R.id.total_size);
        totalSizeTextView.setText(String.valueOf(totalImages) + " 张图片");
    }

    // 显示当前扫描到的图片的总数
    private void displayCurrentImageCount(int currentImageCount) {
        runOnUiThread(() -> {
            TextView currentSizeTextView = findViewById(R.id.current_size);
            currentSizeTextView.setText("正在扫描第" + String.valueOf(currentImageCount) + " 张图片");
        });
    }


    private int getImageCountFromGallery() {
        int count = 0;
        String[] projection = {MediaStore.Images.Media.DATA};

        try (Cursor cursor = getContentResolver().query(
              MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
              projection,
              null,
              null,
              null)) {
            if (cursor != null) {
                count = cursor.getCount();
            }
        }
        Log.d(TAG, "==========================getImageCountFromGallery: " + count);
        return count;
    }


    // 从相册中获取图片
    private List<Bitmap> getImagesFromGallery(int startIndex, int batchSize) {
        List<Bitmap> images = new ArrayList<>();
        String[] projection = {MediaStore.Images.Media.DATA};

        try (Cursor cursor = getContentResolver().query(
              MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
              projection,
              null,
              null,
              null)) {
            if (cursor != null && cursor.moveToPosition(startIndex)) {
                int count = 0;
                while (cursor.moveToNext() && count < batchSize) {
                    int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                    String imagePath = cursor.getString(columnIndex);
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = 2; // 图片的缩放比例
                    Bitmap bitmap = BitmapFactory.decodeFile(imagePath, options);
                    images.add(bitmap);
                    count++;
                }
            }
        }
        return images;
    }
}
