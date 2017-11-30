package aemurill.diditmove;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;
import android.widget.Toast;

import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

public class MyServiceTask implements Runnable, SensorEventListener {
    //GENERAL VARS
    private static final String LOG_TAG = "MyServiceTask";
    private Context context;
    private boolean running = true;
    private boolean state;

    //MOVEMENT VARS
    private Date timer_start = new Date();

    //SENSOR VARS
    private long lastUpdate = 0;
    private float last_x, last_y, last_z;
    private static final int SHAKE_THRESHOLD = 100;
    private SensorManager mSensorManager;
    private Sensor mSensor;

    //CALLBACK VARS
    private Set<ResultCallback> resultCallbacks = Collections.synchronizedSet(
            new HashSet<ResultCallback>());
    private ConcurrentLinkedQueue<ServiceResult> freeResults =
            new ConcurrentLinkedQueue<ServiceResult>();


    public MyServiceTask(Context context) {
        this.context = context;
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if(mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null){
            Log.i(LOG_TAG, "ACCELEROMETER DETECTED");
            mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            mSensorManager.registerListener(this, mSensor,
                    SensorManager.SENSOR_DELAY_NORMAL);
        }
        else{
            Toast.makeText(context, "No Accelerometer\nApp won't work on your device!\nSorry!",
                    Toast.LENGTH_LONG).show();
            Log.e(LOG_TAG, "No Accelerometer.");
        }
    }

    public boolean didItMove(){
        Date d = new Date();
        boolean moved = false;
        if (d.getTime() - timer_start.getTime() > 30000){
            moved = true;
        }
        return moved;

    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent){
        Sensor mySensor = sensorEvent.sensor;
        if (mySensor.getType() == Sensor.TYPE_ACCELEROMETER){
            float x = sensorEvent.values[0];
            float y = sensorEvent.values[0];
            float z = sensorEvent.values[0];

            long curTime = System.currentTimeMillis();

            if ((curTime - lastUpdate)> 100){
                long diffTime  = (curTime - lastUpdate);
                lastUpdate = curTime;

                float speed = Math.abs(x + y + z - last_x - last_y - last_z) / diffTime * 10000;
                Log.i(LOG_TAG, "Speed :  " + speed);
                if(speed > SHAKE_THRESHOLD){
                    Log.i(LOG_TAG, "ACCELERATION CHANGE");
                    //DO SOMETHING
                    state = didItMove();
                }
                last_x = x;
                last_y = y;
                last_z = z;
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy){

    }

    @Override
    public void run() {
        while (running) {
            if(running) {
                try {
                    Thread.sleep(100);
                } catch (Exception e) {
                    e.getLocalizedMessage();
                }
                // Sends it to the UI thread in MainActivity
                // (if MainActivity is running).
                Log.i(LOG_TAG, "Sending random number: " + state);
                notifyResultCallback(state);
            }else{
                Log.i(LOG_TAG, "Shutting down service");
            }
        }
    }

    public void addResultCallback(ResultCallback resultCallback) {
        Log.i(LOG_TAG, "Adding result callback");
        resultCallbacks.add(resultCallback);
    }

    public void removeResultCallback(ResultCallback resultCallback) {
        Log.i(LOG_TAG, "Removing result callback");
        // We remove the callback...
        resultCallbacks.remove(resultCallback);
        // ...and we clear the list of results.
        // Note that this works because, even though mResultCallbacks is a synchronized set,
        // its cardinality should always be 0 or 1 -- never more than that.
        // We have one viewer only.
        // We clear the buffer, because some result may never be returned to the
        // free buffer, so using a new set upon reattachment is important to avoid
        // leaks.
        freeResults.clear();
    }

    // Creates result bitmaps if they are needed.
    private void createResultsBuffer() {
        // I create some results to talk to the callback, so we can reuse these instead of creating new ones.
        // The list is synchronized, because integers are filled in the service thread,
        // and returned to the free pool from the UI thread.
        freeResults.clear();
        for (int i = 0; i < 10; i++) {
            freeResults.offer(new ServiceResult());
        }
    }

    // This is called by the UI thread to return a result to the free pool.
    public void releaseResult(ServiceResult r) {
        Log.i(LOG_TAG, "Freeing result holder for " + r.status);
        freeResults.offer(r);
    }

    public void stopProcessing() {
        Log.i(LOG_TAG, "Unregister Accelerometer Listener");
        mSensorManager.unregisterListener(this);
        Log.i(LOG_TAG, "Unregistered Accelerometer Listener");
        Log.i(LOG_TAG, "Stop Running");
        running = false;
        Log.i(LOG_TAG, "Stopped.");
    }

    /*
     * Call this function to return the string s to the activity.
     */
    private void notifyResultCallback(boolean b) {
        if (!resultCallbacks.isEmpty()) {
            // If we have no free result holders in the buffer, then we need to create them.
            if (freeResults.isEmpty()) {
                createResultsBuffer();
            }
            ServiceResult result = freeResults.poll();
            // If we got a null result, we have no more space in the buffer,
            // and we simply drop the integer, rather than sending it back.
            if (result != null) {
                result.status = b;
                for (ResultCallback resultCallback : resultCallbacks) {
                    Log.i(LOG_TAG, "calling resultCallback for " + result.status);
                    resultCallback.onResultReady(result);
                }
            }
        }
    }

    public interface ResultCallback {
        void onResultReady(ServiceResult result);
    }

    public void clearStatus(){
        state = false;
        timer_start = new Date();
    }
}
