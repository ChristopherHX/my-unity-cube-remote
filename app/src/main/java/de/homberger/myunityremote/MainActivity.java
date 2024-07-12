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

    private SensorManager sensorManager;
    private Sensor accelerometer, gyroscope, magnetometer;
    private float[] accelValues = new float[3];
    private float[] gyroValues = new float[3];
    private float[] magnetValues = new float[3];
    private float ax, ay, az;
    private float gx, gy, gz;
    private float vx = 0, vy = 0, vz = 0;
    private float px = 0, py = 0, pz = 0;
    private float[] gravity = new float[3];
    private float[] linearAcceleration = new float[3];
    private float pitch, roll, yaw;
    private long lastUpdate = 0;
    private TextView accelTextView, gyroTextView, velocityTextView, positionTextView, orientationTextView;
    private static final float ACCEL_THRESHOLD = 0.1f;
    private static final float STATIONARY_THRESHOLD = 0.02f;
    private static final float ALPHA = 0.98f;
    private float[] rotationMatrix = new float[9];
    private float[] orientation = new float[3];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_GAME);

        accelTextView = findViewById(R.id.accelTextView);
        gyroTextView = findViewById(R.id.gyroTextView);
        velocityTextView = findViewById(R.id.velocityTextView);
        positionTextView = findViewById(R.id.positionTextView);
        orientationTextView = findViewById(R.id.orientationTextView);
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
                        byte[] frame = new byte[4 * 12];
                        int off = 0;
                        off = writeFloat(frame, off, ax);
                        off = writeFloat(frame, off, ay);
                        off = writeFloat(frame, off, az);
                        off = writeFloat(frame, off, gx);
                        off = writeFloat(frame, off, gy);
                        off = writeFloat(frame, off, gz);
                        off = writeFloat(frame, off, vx);
                        off = writeFloat(frame, off, vy);
                        off = writeFloat(frame, off, vz);
                        off = writeFloat(frame, off, px);
                        off = writeFloat(frame, off, py);
                        off = writeFloat(frame, off, pz);
                        os.write(frame);
                        os.flush();
                        Thread.sleep(100);
                    }
                } catch (Exception e) {
                    return;
                }
            }
        };
        thread.start();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        long currentTime = event.timestamp;
        if (lastUpdate != 0) {
            float dt = (currentTime - lastUpdate) * 1.0f / 1000000000.0f; // Convert nanoseconds to seconds

            switch (event.sensor.getType()) {
                case Sensor.TYPE_ACCELEROMETER:
                    System.arraycopy(event.values, 0, accelValues, 0, event.values.length);

                    // Apply low-pass filter to isolate gravity
                    final float alpha = 0.8f;
                    gravity[0] = alpha * gravity[0] + (1 - alpha) * accelValues[0];
                    gravity[1] = alpha * gravity[1] + (1 - alpha) * accelValues[1];
                    gravity[2] = alpha * gravity[2] + (1 - alpha) * accelValues[2];

                    // Remove gravity from the acceleration values
                    linearAcceleration[0] = accelValues[0] - gravity[0];
                    linearAcceleration[1] = accelValues[1] - gravity[1];
                    linearAcceleration[2] = accelValues[2] - gravity[2];

                    ax = Math.abs(linearAcceleration[0]) > ACCEL_THRESHOLD ? linearAcceleration[0] : 0;
                    ay = Math.abs(linearAcceleration[1]) > ACCEL_THRESHOLD ? linearAcceleration[1] : 0;
                    az = Math.abs(linearAcceleration[2]) > ACCEL_THRESHOLD ? linearAcceleration[2] : 0;

                    // Complementary filter to fuse accelerometer and gyroscope data
                    ax = ALPHA * (ax + gx * dt) + (1 - ALPHA) * linearAcceleration[0];
                    ay = ALPHA * (ay + gy * dt) + (1 - ALPHA) * linearAcceleration[1];
                    az = ALPHA * (az + gz * dt) + (1 - ALPHA) * linearAcceleration[2];

                    // Check if the device is stationary
                    float accelerationMagnitude = (float) Math.sqrt(ax * ax + ay * ay + az * az);
                    if (accelerationMagnitude < STATIONARY_THRESHOLD) {
                        vx = 0;
                        vy = 0;
                        vz = 0;
                    } else {
                        // Update velocity by integrating acceleration
                        vx += ax * dt;
                        vy += ay * dt;
                        vz += az * dt;
                    }

                    // Update position by integrating velocity
                    px += vx * dt;
                    py += vy * dt;
                    pz += vz * dt;

                    updateAccelTextView();
                    updateVelocityTextView();
                    updatePositionTextView();
                    break;

                case Sensor.TYPE_GYROSCOPE:
                    gx = event.values[0];
                    gy = event.values[1];
                    gz = event.values[2];
                    updateGyroTextView();
                    break;

                case Sensor.TYPE_MAGNETIC_FIELD:
                    System.arraycopy(event.values, 0, magnetValues, 0, event.values.length);
                    break;
            }

            // Update rotation matrix and orientation using accelerometer and magnetometer
            SensorManager.getRotationMatrix(rotationMatrix, null, accelValues, magnetValues);
            SensorManager.getOrientation(rotationMatrix, orientation);
            pitch = (float) Math.toDegrees(orientation[1]);
            roll = (float) Math.toDegrees(orientation[2]);
            yaw = (float) Math.toDegrees(orientation[0]);
            updateOrientationTextView();
        }
        lastUpdate = currentTime;
    }

    private void updateOrientationTextView() {
        runOnUiThread(() -> orientationTextView.setText(String.format("Orientation: pitch=%.2f, roll=%.2f, yaw=%.2f", pitch, roll, yaw)));
    }

    private void updateAccelTextView() {
        runOnUiThread(() -> accelTextView.setText(String.format("Accelerometer: x=%.2f, y=%.2f, z=%.2f", ax, ay, az)));
    }

    private void updateGyroTextView() {
        runOnUiThread(() -> gyroTextView.setText(String.format("Gyroscope: x=%.2f, y=%.2f, z=%.2f", gx, gy, gz)));
    }

    private void updateVelocityTextView() {
        runOnUiThread(() -> velocityTextView.setText(String.format("Velocity: x=%.4f, y=%.4f, z=%.4f", vx, vy, vz)));
    }

    private void updatePositionTextView() {
        runOnUiThread(() -> positionTextView.setText(String.format("Position: x=%.2f, y=%.2f, z=%.2f", px, py, pz)));
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}