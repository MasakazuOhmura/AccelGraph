package jp.ac.titech.itpro.sdl.accelgraph;

import android.app.Activity;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends Activity implements SensorEventListener {

    /**
     * 行列数
     */
    private static final int MATRIX_SIZE = 16;
    /**
     * 三次元(XYZ)
     */
    private static final int DIMENSION = 3;
    /**
     * 地磁気行列
     */
    private float[] mMagneticValues;
    /**
     * 加速度行列
     */
    private float[] mAccelerometerValues;
    /**
     * 回転角度
     */
    private int pitchX;
    private int rollY;
    private int azimuthZ;

    private final static String TAG = "MainActivity";

    private TextView rateView, accuracyView;
    private GraphView xView, yView, zView, xView2, yView2, zView2;

    private SensorManager sensorMgr;
    private Sensor accelerometer;
    private Sensor magneticField;

    private final static long GRAPH_REFRESH_WAIT_MS = 20;

    private GraphRefreshThread th = null;
    private Handler handler;

    private float vx, vy, vz;
    private float rate;
    private int accuracy;
    private long prevts;

    private final static float alpha = 0.75F;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");
        setContentView(R.layout.activity_main);

        rateView = (TextView) findViewById(R.id.rate_view);
        accuracyView = (TextView) findViewById(R.id.accuracy_view);
        xView = (GraphView) findViewById(R.id.x_view);
        yView = (GraphView) findViewById(R.id.y_view);
        zView = (GraphView) findViewById(R.id.z_view);
        xView2 = (GraphView) findViewById(R.id.x_view2);
        yView2 = (GraphView) findViewById(R.id.y_view2);
        zView2 = (GraphView) findViewById(R.id.z_view2);

        sensorMgr = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorMgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magneticField = sensorMgr.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        if (accelerometer == null) {
            Toast.makeText(this, getString(R.string.toast_no_accel_error),
                    Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        handler = new Handler();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");
        sensorMgr.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
        sensorMgr.registerListener(this, magneticField, SensorManager.SENSOR_DELAY_FASTEST);
        th = new GraphRefreshThread();
        th.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "onPause");
        th = null;
        sensorMgr.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
            mMagneticValues = event.values.clone();
        else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
            mAccelerometerValues = event.values.clone();

        if (mMagneticValues != null && mAccelerometerValues != null) {
            float[] rotationMatrix = new float[MATRIX_SIZE];
            float[] inclinationMatrix = new float[MATRIX_SIZE];
            float[] remapedMatrix = new float[MATRIX_SIZE];
            float[] orientationValues = new float[DIMENSION];
            // 加速度センサーと地磁気センサーから回転行列を取得
            SensorManager.getRotationMatrix(rotationMatrix, inclinationMatrix, mAccelerometerValues, mMagneticValues);
            SensorManager.remapCoordinateSystem(rotationMatrix, SensorManager.AXIS_X, SensorManager.AXIS_Z, remapedMatrix);
            SensorManager.getOrientation(remapedMatrix, orientationValues);
            // ラジアン値を変換し、それぞれの回転角度を取得する
            azimuthZ = radianToDegrees(orientationValues[0]);
            pitchX = radianToDegrees(orientationValues[1]);
            rollY = radianToDegrees(orientationValues[2]);
            Log.e(TAG, "X=" + pitchX + " Y=" + rollY + " Z=" + azimuthZ);
        }

        vx = alpha * vx + (1 - alpha) * event.values[0];
        vy = alpha * vy + (1 - alpha) * event.values[1];
        vz = alpha * vz + (1 - alpha) * event.values[2];
        Log.e(TAG, "X=" + vx + " Y=" + vy + " Z=" + vz);
        rate = ((float) (event.timestamp - prevts)) / (1000 * 1000);
        prevts = event.timestamp;
    }

    /**
     * ラジアンを角度に変換する
     *
     * @param angrad ラジアン
     * @return 角度
     */
    private int radianToDegrees(float angrad) {
        return (int) Math.floor(angrad >= 0 ? Math.toDegrees(angrad) : 360 + Math.toDegrees(angrad));
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.i(TAG, "onAccuracyChanged: ");
        this.accuracy = accuracy;
    }

    private class GraphRefreshThread extends Thread {
        public void run() {
            try {
                while (th != null) {
                    handler.post(new Runnable() {
                        public void run() {
                            rateView.setText(String.format(Locale.getDefault(), "%f", rate));
                            accuracyView.setText(String.format(Locale.getDefault(), "%d", accuracy));
                            xView.addData(vx, true);
                            yView.addData(vy, true);
                            zView.addData(vz, true);

                            xView2.addData(pitchX, true);
                            yView2.addData(rollY, true);
                            zView2.addData(azimuthZ, true);
                        }
                    });
                    Thread.sleep(GRAPH_REFRESH_WAIT_MS);
                }
            } catch (InterruptedException e) {
                Log.e(TAG, e.toString());
                th = null;
            }
        }
    }
}
