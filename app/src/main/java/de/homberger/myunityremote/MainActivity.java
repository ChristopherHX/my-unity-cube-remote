package de.homberger.myunityremote;

import static java.lang.Math.acos;
import static java.lang.Math.atan;
import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;

import androidx.appcompat.app.AppCompatActivity;

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

import java.io.IOException;
import java.io.OutputStream;
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

    private float[] speed = new float[3];
    private float[] position = new float[3];
    private long last, glast;
    private TextView textView;
    private float[] angle = new float[3];
    private float[] aAngle = new float[3];
    private float[] olxyz;
    private float[] ospeed;
    private float[] ogyro;
    private boolean sendPos = true, sendAngle = true;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        sensorManager=(SensorManager) getSystemService(SENSOR_SERVICE);

        textView = findViewById(R.id.textView);


        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_FASTEST);
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), SensorManager.SENSOR_DELAY_FASTEST);
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

    public void reset(View view) {
        speed[0] = 0;
        speed[1] = 0;
        speed[2] = 0;
        position[0] = 0;
        position[1] = 0;
        position[2] = 0;

        angle[0] = 0;
        angle[1] = 0;
        angle[2] = 0;

        olxyz = null;
        ospeed = null;
        ogyro = null;
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
                    while(true) {
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
//                        Thread.sleep(100);
                    }
                } catch (Exception e) {
                    return;
                }
            }
        };
        thread.start();
    }


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

//            Matrix rx = new Matrix();
//            rx.setValues(new float[] { (float) cos(angle[0]), (float) -sin(angle[0]), 0, (float) sin(angle[0]), (float) cos(angle[0]), 0, 0, 0, 1 });
//            Matrix ry = new Matrix();
//            ry.setValues(new float[] { 1, 0, 0, 0, (float) cos(angle[1]), (float) -sin(angle[1]), 0, (float) sin(angle[1]), (float) cos(angle[1]) });
//
//            Matrix rz = new Matrix();
//            rz.setValues(new float[] { (float) cos(angle[2]), (float) -sin(angle[2]), 0, 1, 0, 0, (float) sin(angle[2]), (float) cos(angle[2]) });
            //m.preRotate(3.14f);

            x = sensorEvent.values[0];
            y = sensorEvent.values[1];
            z = sensorEvent.values[2];



            float gravity = (float) sqrt(x * x + y * y + z * z);
            float[] lxyz = new float[]{0,0,0};
            float[] xyz = new float[]{0,0,0};

            if(gravity >= 9.80 && gravity < 9.82) {
                aAngle[0] = (float) atan(y / z);
                aAngle[1] = (float) -atan(x / z);
                aAngle[2] = 0;

                angle[0] = aAngle[0];
                angle[1] = aAngle[1];
                angle[2] = aAngle[2];

                speed[0] = 0;
                speed[1] = 0;
                speed[2] = 0;

                olxyz = null;
                ospeed = null;
                ogyro = null;

            } else {

                //m.setValues(new float[] {  });

//                speed[0] += x * delta;
//                speed[1] += y * delta;
//                speed[2] += z * delta;
//
//                position[0] += 100 * speed[0] * delta;
//                position[1] += 100 * speed[1] * delta;
//                position[2] += 100 * speed[2] * delta;

                Matrix rotate = new Matrix();
                //rotate.preConcat(getRz(0));
                rotate.preConcat(getRx(-angle[0]));
                rotate.preConcat(getRy(-angle[1]));
                float[] in = new float[]{0, 0, 9.81f};

                //rotate.mapVectors(xyz, in);
                Matrix res = new Matrix();
                float[] mres = new float[]{0, 0, 0, 0, 0, 0, 9.81f, 0, 0};
                res.setValues(mres);
                res.postConcat(rotate);
                res.getValues(mres);
                xyz[0] = mres[0];
                xyz[1] = mres[3];
                xyz[2] = mres[6];
                lxyz[0] = x - mres[0];
                lxyz[1] = y - mres[3];
                lxyz[2] = z - mres[6];
                if(olxyz != null) {
                    float pass = 0.9f;
                    lxyz[0] = lxyz[0] * pass + (1 - pass) * olxyz[0];
                    lxyz[1] = lxyz[1] * pass + (1 - pass) * olxyz[1];
                    lxyz[2] = lxyz[2] * pass + (1 - pass) * olxyz[2];

                }
                olxyz = lxyz;

//                if(Math.sqrt(lxyz[0] * lxyz[0] + lxyz[1] * lxyz[1] + lxyz[2] * lxyz[2]) > 0.1) {
                    speed[0] += lxyz[0] * delta;
                    speed[1] += lxyz[1] * delta;
                    speed[2] += lxyz[2] * delta;
//                }
                if(ospeed != null) {
                    float pass = 0.9f;
                    speed[0] = speed[0] * pass + (1 - pass) * ospeed[0];
                    speed[1] = speed[1] * pass + (1 - pass) * ospeed[1];
                    speed[2] = speed[2] * pass + (1 - pass) * ospeed[2];
                }
                ospeed = speed;

//                if(Math.sqrt(speed[0] * speed[0] + speed[1] * speed[1] + speed[2] * speed[2]) > 0.1) {
                    position[0] += 100 * speed[0] * delta;
                    position[1] += 100 * speed[1] * delta;
                    position[2] += 100 * speed[2] * delta;
//                }
            }

            textView.setText("delta: " + delta + "\naccel:\n" + Arrays.toString(sensorEvent.values).replace(", ", ", \n" ) + "\naccel rot:\n" + Arrays.toString(xyz).replace(", ", ", \n" ) +"\naccel lin:\n" + Arrays.toString(lxyz).replace(", ", ", \n" ) + "\nspeed: \n" + Arrays.toString(speed).replace(", ", ",\n" ) + "\nposition: \n" + Arrays.toString(position).replace(", ", ",\n") + "\ngx: " + gx + "\ngy: " + gy + "\ngz: " + gz + "\nangle:\n" + Arrays.toString(angle).replace(", ", ", \n" ) + "\naAngle:\n" + Arrays.toString(aAngle).replace(", ", ", \n" ));
            //Matrix proc = new Matrix();
            //android.opengl.Matrix.multiplyMM();
        }
        if(sensorEvent.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            double delta = (cur - glast) / 1000000000.0f;

            if(glast == 0 || ogyro == null) {
                glast = cur;
                ogyro = sensorEvent.values.clone();
                return;
            }
            glast = cur;

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

            angle[0] += gx * delta;
            angle[1] += gy * delta;
            angle[2] += gz * delta;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }
}