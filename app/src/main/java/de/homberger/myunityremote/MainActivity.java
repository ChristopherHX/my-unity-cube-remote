package de.homberger.myunityremote;

import static java.lang.Math.acos;
import static java.lang.Math.atan;
import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Matrix;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.Timestamp;
import java.sql.Time;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Date;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private SensorManager sensorManager;
    private float x, y, z;
    private float gx, gy, gz;

    private float[] velocity = new float[3];
    private float[] position = new float[3];
    private long last, glast;
    private TextView textView;
    private float[] angle = new float[3];
    private float[] aAngle = new float[3];
    private float[] olxyz;
    private float[] ovelocity;
    private float[] ogyro;
    private boolean sendPos = true, sendAngle = true, autoAdjustAngle = false, writeCSV = false;
    private File csv;
    private OutputStreamWriter outputStreamWriter;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        sensorManager=(SensorManager) getSystemService(SENSOR_SERVICE);

        textView = findViewById(R.id.textView);


        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_FASTEST);
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), SensorManager.SENSOR_DELAY_FASTEST);

        try {
            File location = getApplicationContext().getExternalFilesDir(null);

            csv = new File(location, "data.csv");
            outputStreamWriter = new OutputStreamWriter(new FileOutputStream(csv));
            outputStreamWriter.write("dt;accel x;accel y;accel z;velocity x;velocity y;velocity z;position x;position y;position z;accel angle x;accel angle y;accel angle z;gyro x;gyro y;gyro z;angle x;angle y;angle z;gyro accel x;gyro accel y;gyro accel z;linear accel x;linear accel y;linear accel z;\n");
        } catch (Exception e) {
            e.toString();
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

    public void onPosChanged(View view) {
        sendPos = ((Switch)view).isChecked();
    }

    public void onAngleChanged(View view) {
        sendAngle = ((Switch)view).isChecked();
    }

    public void onAutoAdjustAngleChanged(View view) {
        autoAdjustAngle = ((Switch)view).isChecked();
    }

    public void onWriteCSVChanged(View view) {
        writeCSV = ((Switch)view).isChecked();
    }

    // Allow to reset the integrated values
    public void reset(View view) {
        velocity[0] = 0;
        velocity[1] = 0;
        velocity[2] = 0;
        position[0] = 0;
        position[1] = 0;
        position[2] = 0;

        angle[0] = aAngle[0];
        angle[1] = aAngle[1];
        angle[2] = aAngle[2];

        olxyz = null;
        ovelocity = null;
        ogyro = null;
    }

    // Connect to an unity app via tcp stream
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
                    while(true) {
                        // Just pack 12 floats together and send them to unity
                        byte[] frame = new byte[4 * 12];
                        int off = 0;
                        off = writeFloat(frame, off, x);
                        off = writeFloat(frame, off, y);
                        off = writeFloat(frame, off, z);
                        off = writeFloat(frame, off, gx);
                        off = writeFloat(frame, off, gy);
                        off = writeFloat(frame, off, gz);
                        if(sendPos) {
                            off = writeFloat(frame, off, position[0]);
                            off = writeFloat(frame, off, position[1]);
                            off = writeFloat(frame, off, position[2]);
                        } else {
                            off = writeFloat(frame, off, 0);
                            off = writeFloat(frame, off, 0);
                            off = writeFloat(frame, off, 0);
                        }
                        if(sendAngle) {
                            off = writeFloat(frame, off, angle[0]);
                            off = writeFloat(frame, off, angle[1]);
                            off = writeFloat(frame, off, angle[2]);
                        } else {
                            off = writeFloat(frame, off, 0);
                            off = writeFloat(frame, off, 0);
                            off = writeFloat(frame, off, 0);
                        }
                        os.write(frame);
                        os.flush();
                    }
                } catch (Exception e) {
                    return;
                }
            }
        };
        thread.start();
    }

    // get the rotation matrices for an angle

    Matrix getRz(float a) {
        Matrix m = new Matrix();
        m.setValues(new float[] { (float) cos(a), (float) -sin(a), 0, (float) sin(a), (float) cos(a), 0, 0, 0, 1 });
        return m;
    }

    Matrix getRx(float a) {
        Matrix m = new Matrix();
        m.setValues(new float[] { 1, 0, 0, 0, (float) cos(a), (float) -sin(a), 0, (float) sin(a), (float) cos(a) });
        return m;
    }

    Matrix getRy(float a) {
        Matrix m = new Matrix();
        m.setValues(new float[]{(float) cos(a), 0, (float) sin(a), 0, 1, 0, (float) -sin(a), 0, (float) cos(a)});
        return m;
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        long cur = System.nanoTime();
        if(sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {

            float delta = (cur - last) / 1000000000.0f;
            if(last == 0 || delta > 0.1f) {
                last = cur;
                return;
            }
            last = cur;

            x = sensorEvent.values[0];
            y = sensorEvent.values[1];
            z = sensorEvent.values[2];

            // Calculate the total amount of acceleration applied to all axis
            float gravity = (float) sqrt(x * x + y * y + z * z);
            float[] lxyz = new float[]{0,0,0};
            float[] xyz = new float[]{0,0,0};
            // Smartphone is almost stationary
            // Calculate the same angle as integrated by the gyro sensor
            if(gravity >= 9.80 && gravity < 9.82) {
                // Reference https://www.nxp.com/docs/en/application-note/AN3461.pdf
                // The technical singularity of z == 0 should be handled here
                // The precision is discussed in the reference
                aAngle[0] = (float) atan(y / z);
                aAngle[1] = (float) -atan(x / z);
                aAngle[2] = 0;
                if(autoAdjustAngle) {
                    angle[0] = aAngle[0];
                    angle[1] = aAngle[1];
                    // keep Z-Axis of gyro that cannot be calculated when the device is on a surface
                    // angle[2] = aAngle[2];
                }

                // velocity of a stationary device is 0
                velocity[0] = 0;
                velocity[1] = 0;
                velocity[2] = 0;

                // Reset all low pass deltas
                olxyz = null;
                ovelocity = null;
                ogyro = null;

            } else {
                // Apply the rotation of angle to (0, 0, 9.81f)
                // This gives us the estimated gravity of x y z axis
                Matrix rotate = new Matrix();
                rotate.preConcat(getRx(-angle[0]));
                rotate.preConcat(getRy(-angle[1]));
                Matrix res = new Matrix();
                float[] mres = new float[]{0, 0, 0, 0, 0, 0, 9.81f, 0, 0};
                res.setValues(mres);
                res.postConcat(rotate);
                res.getValues(mres);
                xyz[0] = mres[0];
                xyz[1] = mres[3];
                xyz[2] = mres[6];
                // Subtract the xyz gravity to get linear acceleration
                lxyz[0] = x - mres[0];
                lxyz[1] = y - mres[3];
                lxyz[2] = z - mres[6];
                // Apply low pass filtering
                if(olxyz != null) {
                    float pass = 0.9f;
                    lxyz[0] = lxyz[0] * pass + (1 - pass) * olxyz[0];
                    lxyz[1] = lxyz[1] * pass + (1 - pass) * olxyz[1];
                    lxyz[2] = lxyz[2] * pass + (1 - pass) * olxyz[2];

                }
                olxyz = lxyz;
                // Integrate linear acceleration to get velocity
                velocity[0] += lxyz[0] * delta;
                velocity[1] += lxyz[1] * delta;
                velocity[2] += lxyz[2] * delta;
                // lowpass filter velocity
                if(ovelocity != null) {
                    float pass = 0.9f;
                    velocity[0] = velocity[0] * pass + (1 - pass) * ovelocity[0];
                    velocity[1] = velocity[1] * pass + (1 - pass) * ovelocity[1];
                    velocity[2] = velocity[2] * pass + (1 - pass) * ovelocity[2];
                }
                ovelocity = velocity;

                // Integrate velocity to get position converted from meter to cm
                position[0] += 100 * velocity[0] * delta;
                position[1] += 100 * velocity[1] * delta;
                position[2] += 100 * velocity[2] * delta;
            }

            runOnUiThread(() -> {
                textView.setText("delta: " + delta + "\naccel:\n" + Arrays.toString(sensorEvent.values).replace(", ", ", \n") + "\naccel rot:\n" + Arrays.toString(xyz).replace(", ", ", \n") + "\naccel lin:\n" + Arrays.toString(lxyz).replace(", ", ", \n") + "\nvelocity: \n" + Arrays.toString(velocity).replace(", ", ",\n") + "\nposition: \n" + Arrays.toString(position).replace(", ", ",\n") + "\ngx: " + gx + "\ngy: " + gy + "\ngz: " + gz + "\nangle:\n" + Arrays.toString(angle).replace(", ", ", \n") + "\naAngle:\n" + Arrays.toString(aAngle).replace(", ", ", \n"));
            });
            // Capture all data in a csv for analysis
            if(writeCSV && outputStreamWriter != null) {
                try {
                    outputStreamWriter.write(String.format("%.15f;%.15f;%.15f;%.15f;%.15f;%.15f;%.15f;%.15f;%.15f;%.15f;%.15f;%.15f;%.15f;%.15f;%.15f;%.15f;%.15f;%.15f;%.15f;%.15f;%.15f;%.15f;%.15f;%.15f;%.15f;\n", delta, sensorEvent.values[0], sensorEvent.values[1], sensorEvent.values[2], velocity[0], velocity[1], velocity[2], position[0], position[1], position[2], aAngle[0], aAngle[1], aAngle[2], gx, gy, gz, angle[0], angle[1], angle[2], xyz[0], xyz[1], xyz[2], lxyz[0], lxyz[1], lxyz[2]));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        if(sensorEvent.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            double delta = (cur - glast) / 1000000000.0f;

            if(glast == 0 || ogyro == null) {
                glast = cur;
                ogyro = sensorEvent.values.clone();
                return;
            }
            glast = cur;

            // apply low pass filtering
            if(ogyro != null) {
                float pass = 0.9f;
                sensorEvent.values[0] = sensorEvent.values[0] * pass + (1 - pass) * ogyro[0];
                sensorEvent.values[1] = sensorEvent.values[1] * pass + (1 - pass) * ogyro[1];
                sensorEvent.values[2] = sensorEvent.values[2] * pass + (1 - pass) * ogyro[2];
            }
            gx = sensorEvent.values[0];
            gy = sensorEvent.values[1];
            gz = sensorEvent.values[2];
            ogyro = sensorEvent.values.clone();

            // Integrate the gyro to get the angle in radians
            angle[0] += gx * delta;
            angle[1] += gy * delta;
            angle[2] += gz * delta;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }
}