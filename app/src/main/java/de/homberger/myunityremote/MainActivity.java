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

import com.kircherelectronics.fsensor.observer.SensorSubject;
import com.kircherelectronics.fsensor.sensor.FSensor;
import com.kircherelectronics.fsensor.sensor.acceleration.AccelerationSensor;

import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;

public class MainActivity extends AppCompatActivity {
    private TextView accelTextView, gyroTextView, velocityTextView, positionTextView, orientationTextView;

    private FSensor testSensor;
    private float[] accelValues = new float[3];
    private SensorSubject.SensorObserver testObserver = new SensorSubject.SensorObserver() {
        @Override
        public void onSensorChanged(float[] values) {
            System.arraycopy(values, 0, accelValues, 0, accelValues.length);
            updateOrientationTextView();
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

        testSensor = new AccelerationSensor(this);
        testSensor.register(testObserver);
        testSensor.start();
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        testSensor.unregister(testObserver);
        testSensor.stop();
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
        runOnUiThread(() -> orientationTextView.setText(String.format("Accl: x=%.2f, y=%.2f, z=%.2f", accelValues[0], accelValues[1], accelValues[2])));
    }
    /*
    private void updateAccelTextView() {
        runOnUiThread(() -> accelTextView.setText(String.format("Accelerometer: x=%.2f, y=%.2f, z=%.2f", accelValues[0], accelValues[1], accelValues[2])));
    }

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