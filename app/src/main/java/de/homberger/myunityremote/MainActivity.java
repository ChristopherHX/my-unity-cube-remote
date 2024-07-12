package de.homberger.myunityremote;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.util.Log;

import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private static final int REQUEST_HIGH_SAMPLING_RATE_SENSORS = 1;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor gyroscope;
    private Sensor magnetometer;

    private float[] gravity = new float[3];
    private float[] geomagnetic = new float[3];
    private float[] linear_acceleration = new float[3];
    private float[] gyroscopeData = new float[3];
    private float[] rotationMatrix = new float[9];
    private float[] orientation = new float[3];
    private float[] fusedOrientation = new float[3];

    private float[] velocity = new float[3];
    private float[] position = new float[3];
    private long timestamp;

    private TextView orientationTextView;
    private TextView positionTextView;

    private static final String TAG = "MainActivity";

    // Alpha value for low-pass filter
    private static final float ALPHA = 0.8f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        orientationTextView = findViewById(R.id.orientationTextView);
        positionTextView = findViewById(R.id.positionTextView);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.HIGH_SAMPLING_RATE_SENSORS) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.HIGH_SAMPLING_RATE_SENSORS}, REQUEST_HIGH_SAMPLING_RATE_SENSORS);
                } else {
                    registerSensors();
                }
            } else {
                registerSensors();
            }
        } else {
            Log.e(TAG, "SensorManager not available");
        }
    }

    private void registerSensors() {
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
        } else {
            Log.e(TAG, "Accelerometer not available");
        }
        if (gyroscope != null) {
            sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_FASTEST);
        } else {
            Log.e(TAG, "Gyroscope not available");
        }
        if (magnetometer != null) {
            sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_FASTEST);
        } else {
            Log.e(TAG, "Magnetometer not available");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_HIGH_SAMPLING_RATE_SENSORS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                registerSensors();
            } else {
                Log.e(TAG, "High sampling rate sensors permission not granted");
            }
        }
    }

    private static int writeFloat(byte[] frame, int offset, float v) {
        int iv = Float.floatToIntBits(v);
        frame[offset] = (byte) (iv >> 24);
        frame[offset + 1] = (byte) (iv >> 16);
        frame[offset + 2] = (byte) (iv >> 8);
        frame[offset + 3] = (byte) (iv);
        return offset + 4;
    }

    public void onConnect(View view) {
        EditText hostTextEdit = findViewById(R.id.hostTextEdit);
        Thread thread = new Thread() {
            @Override
            public void run() {
                try {
                    String ip = hostTextEdit.getText().toString();
                    int p = ip.lastIndexOf(':');
                    InetAddress raw = InetAddress.getByName(ip.substring(0, p));
                    Socket sock = new Socket(raw, Integer.parseInt(ip.substring(p + 1)));
                    OutputStream os = sock.getOutputStream();
                    while (true) {
                        byte[] frame = new byte[4 * 6];
                        int off = 0;
                        off = writeFloat(frame, off, position[0]);
                        off = writeFloat(frame, off, position[1]);
                        off = writeFloat(frame, off, position[2]);
                        off = writeFloat(frame, off, fusedOrientation[0]);
                        off = writeFloat(frame, off, fusedOrientation[1]);
                        off = writeFloat(frame, off, fusedOrientation[2]);
                        os.write(frame);
                        os.flush();
                        Thread.sleep(100);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Connection failed", e);
                }
            }
        };
        thread.start();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                // Apply low-pass filter to isolate gravity
                gravity[0] = ALPHA * gravity[0] + (1 - ALPHA) * event.values[0];
                gravity[1] = ALPHA * gravity[1] + (1 - ALPHA) * event.values[1];
                gravity[2] = ALPHA * gravity[2] + (1 - ALPHA) * event.values[2];

                // Subtract gravity to get linear acceleration
                linear_acceleration[0] = event.values[0] - gravity[0];
                linear_acceleration[1] = event.values[1] - gravity[1];
                linear_acceleration[2] = event.values[2] - gravity[2];

                Log.d(TAG, String.format("Accelerometer: X=%.2f Y=%.2f Z=%.2f", linear_acceleration[0], linear_acceleration[1], linear_acceleration[2]));
                break;
            case Sensor.TYPE_GYROSCOPE:
                System.arraycopy(event.values, 0, gyroscopeData, 0, gyroscopeData.length);
                if (timestamp != 0) {
                    final float dT = (event.timestamp - timestamp) * 1.0f / 1000000000.0f;
                    fusedOrientation[0] += gyroscopeData[0] * dT;
                    fusedOrientation[1] += gyroscopeData[1] * dT;
                    fusedOrientation[2] += gyroscopeData[2] * dT;
                }
                timestamp = event.timestamp;
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                System.arraycopy(event.values, 0, geomagnetic, 0, geomagnetic.length);
                break;
        }

        if (gravity != null && geomagnetic != null) {
            boolean success = SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic);
            if (success) {
                SensorManager.getOrientation(rotationMatrix, orientation);
                fusedOrientation[0] = orientation[0];
                fusedOrientation[1] = orientation[1];
                fusedOrientation[2] = orientation[2];
            }
        }

        if (timestamp != 0) {
            final float dT = (event.timestamp - timestamp) * 1.0f / 1000000000.0f;

            for (int i = 0; i < 3; i++) {
                velocity[i] += linear_acceleration[i] * dT;
                position[i] += velocity[i] * dT;

                // Logging for debugging
                Log.d(TAG, String.format("Axis %d: Accel=%.2f Vel=%.2f Pos=%.2f", i, linear_acceleration[i], velocity[i], position[i]));
            }
        }

        timestamp = event.timestamp;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                orientationTextView.setText(String.format("Orientation: \nX: %.2f\nY: %.2f\nZ: %.2f",
                        fusedOrientation[0], fusedOrientation[1], fusedOrientation[2]));
                positionTextView.setText(String.format("Position: \nX: %.2f\nY: %.2f\nZ: %.2f",
                        position[0], position[1], position[2]));
            }
        });
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not used in this example
    }
}
