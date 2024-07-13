package de.homberger.myunityremote;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.kircherelectronics.fsensor.BaseFilter;
import com.kircherelectronics.fsensor.filter.averaging.LowPassFilter;
import com.kircherelectronics.fsensor.filter.averaging.MeanFilter;
import com.kircherelectronics.fsensor.observer.SensorSubject;
import com.kircherelectronics.fsensor.sensor.FSensor;
import com.kircherelectronics.fsensor.sensor.acceleration.ComplementaryLinearAccelerationSensor;
import com.kircherelectronics.fsensor.sensor.acceleration.KalmanLinearAccelerationSensor;
import com.kircherelectronics.fsensor.sensor.acceleration.LinearAccelerationSensor;
import com.kircherelectronics.fsensor.sensor.gyroscope.KalmanGyroscopeSensor;

import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;

public class MainActivity extends AppCompatActivity {
    private TextView accelTextView, gyroTextView, velocityTextView, positionTextView, orientationTextView;

    private FSensor gyroSensor;
    private FSensor accelSensor;
    private float[] gyroValues = new float[3];
    private float[] accelValues = new float[3];
    private float[] velocity = new float[3];
    private float[] position = new float[3];
    private long lastUpdateTime;
    private LowPassFilter lowPassFilter;
    private MeanFilter meanFilter;
    private float ACCEL_THRESHOLD = 0.05f;
    private float STATIONARY_THRESHOLD = 0.1f;
    private float ALPHA = 0.98f;
    private SensorSubject.SensorObserver gyroObserver = new SensorSubject.SensorObserver() {
        @Override
        public void onSensorChanged(float[] values) {
            System.arraycopy(values, 0, gyroValues, 0, gyroValues.length);
            updateOrientationTextView();
        }
    };
    private SensorSubject.SensorObserver accelObserver2 = new SensorSubject.SensorObserver() {
        @Override
        public void onSensorChanged(float[] values) {
            long currentTime = System.nanoTime();
            if (lastUpdateTime != 0) {
                float dt = (currentTime - lastUpdateTime) / 1000000000.0f;
                for (int i = 0; i < values.length - 1; i++) {
                    values[i] = Math.abs(values[i]) > ACCEL_THRESHOLD ? values[i] : 0;
                    if (values[i] != 0)
                        values[i] = ALPHA * (values[i] + gyroValues[i]) + (1 - ALPHA) * values[i];
                }
                float accelerationMagnitude = (float) Math.sqrt(values[0] * values[0] + values[1] * values[1] + values[2] * values[2]);
                if (accelerationMagnitude < STATIONARY_THRESHOLD) {
                    velocity[0] = 0;
                    velocity[1] = 0;
                    velocity[2] = 0;
                }
                for (int i = 0; i < 3; i++) {
                    velocity[i] += values[i] * dt;
                    position[i] += velocity[i] * dt;
                }
                updateVelocityTextView();
                updatePositionTextView();
            }
            lastUpdateTime = currentTime;
            System.arraycopy(values, 0, accelValues, 0, accelValues.length);
            updateAccelTextView();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        accelTextView = findViewById(R.id.accelTextView);
        gyroTextView = findViewById(R.id.gyroTextView);
        velocityTextView = findViewById(R.id.velocityTextView);
        positionTextView = findViewById(R.id.positionTextView);
        orientationTextView = findViewById(R.id.orientationTextView);

        gyroSensor = new KalmanGyroscopeSensor(this);
        gyroSensor.register(gyroObserver);
        gyroSensor.start();

        accelSensor = new KalmanLinearAccelerationSensor(this);
        accelSensor.register(accelObserver2);
        accelSensor.start();

        lowPassFilter = new LowPassFilter(0.8f);

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        gyroSensor.unregister(gyroObserver);
        gyroSensor.stop();
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

    private void updateOrientationTextView() {
        runOnUiThread(() -> orientationTextView.setText(String.format("Gyro: x=%.2f, y=%.2f, z=%.2f", gyroValues[0], gyroValues[1], gyroValues[2])));
    }

    private void updateAccelTextView() {
        runOnUiThread(() -> accelTextView.setText(String.format("Accel2: x=%.2f, y=%.2f, z=%.2f", accelValues[0], accelValues[1], accelValues[2])));
    }

    private void updateVelocityTextView() {
        runOnUiThread(() -> velocityTextView.setText(String.format("Velocity: x=%.4f, y=%.4f, z=%.4f", velocity[0], velocity[1], velocity[2])));
    }

    private void updatePositionTextView() {
        runOnUiThread(() -> positionTextView.setText(String.format("Position: x=%.4f, y=%.4f, z=%.4f", position[0], position[1], position[2])));
    }

    /*
    private void updateGyroTextView() {
        runOnUiThread(() -> gyroTextView.setText(String.format("Gyroscope: x=%.2f, y=%.2f, z=%.2f", gyroValues[0], gyroValues[1], gyroValues[2])));
    }

    private void updateVelocityTextView() {
        runOnUiThread(() -> velocityTextView.setText(String.format("Velocity: x=%.4f, y=%.4f, z=%.4f", vx, vy, vz)));
    }

    private void updatePositionTextView() {
        runOnUiThread(() -> positionTextView.setText(String.format("Position: x=%.4f, y=%.4f, z=%.4f", px, py, pz)));
    }
    */
}