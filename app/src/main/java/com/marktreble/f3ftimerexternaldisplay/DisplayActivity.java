package com.marktreble.f3ftimerexternaldisplay;

import com.marktreble.f3ftimerexternaldisplay.util.SystemUiHider;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class DisplayActivity extends Activity {

    TextView mPilotName;
    TextView mPilotTime;

    private Context mContext;
    private DisplayActivity mActivity;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothServerSocket mmServerSocket;

    private BluetoothSocket mmSocket;
    private InputStream mmInStream;
    private OutputStream mmOutStream;

    private boolean mIsListening = false;
    private boolean mRestart = true;

    public static int REQUEST_ENABLE_BT = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_display);

        mPilotName = (TextView)findViewById(R.id.pilot_name);
        mPilotTime = (TextView)findViewById(R.id.pilot_time);

        mContext = this;
        mActivity = this;

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            // Device does not support bluetooth
            Log.d("BLUETOOTH", "BT Not Supported");
            return;
        }

        if (!mBluetoothAdapter.isEnabled()) {
            Log.d("BLUETOOTH", "BT Not Enabled");
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            startListening();
        }
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        mRestart = false;
        mIsListening = false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == DisplayActivity.REQUEST_ENABLE_BT){
            if(resultCode==RESULT_OK){
                startListening();
            } else {
                mActivity.finish();
            }
        }
    }

    protected void startListening() {

        Log.i("BT", "Start Listening");
        BluetoothServerSocket tmp = null;
        UUID uuid = UUID.fromString(getResources().getString(R.string.external_display_uuid));
        try {
            // MY_UUID is the app's UUID string, also used by the client code
            tmp = mBluetoothAdapter.listenUsingRfcommWithServiceRecord("f3f bluetooth external display", uuid);
        } catch (IOException e) {
        }
        mmServerSocket = tmp;

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mPilotName.setText("Waiting for connection...");
        mPilotName.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
        mPilotTime.setText("");

        Thread acceptThread = new Thread(new Runnable()
        {
            public void run()
            {
                BluetoothSocket socket = null;

                try {
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    Log.d("BLUETOOTH", e.getMessage());
                }
                // If a connection was accepted
                if (socket != null) {
                    // Do work to manage the connection (in a separate thread)
                    manageConnectedSocket(socket);
                    try {
                        mmServerSocket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

            }
        });
        Log.i("BT", "Starting Accept Thread");
        acceptThread.start();

    }

    public void manageConnectedSocket(BluetoothSocket socket){
        mmSocket = socket;

        // member streams are final
        try {
            mmInStream = mmSocket.getInputStream();
            mmOutStream = mmSocket.getOutputStream();

            byte[] buffer = new byte[256];  // buffer store for the stream
            int bufferLength; // bytes returned from read()

            mIsListening = true;
            Log.i("BT", "LISTENING");
            runOnUiThread(new Runnable() {
                public void run() {
                    mPilotName.setText("Ready");
                }
            });

            while (mIsListening) {
                try {
                    // Read from the InputStream
                    bufferLength = mmInStream.read(buffer);

                    byte[] data = new byte[bufferLength];
                    System.arraycopy(buffer, 0, data, 0, bufferLength);
                    final String cmd = new String(data, "UTF-8");

                    try {
                        final JSONObject json = new JSONObject(cmd);
                        String type = json.getString("type");

                        if (type.equals("ping")) {
                            String response = json.getString("time");
                            mmOutStream.write(response.getBytes());
                        }

                        if (type.equals("data")) {

                            runOnUiThread(new Runnable() {
                                public void run() {
                                    try {
                                        String name = json.getString("name");
                                        String nationality = json.getString("nationality");
                                        String time = json.getString("time");

                                        int flagid = getResources().getIdentifier(nationality, "drawable", getPackageName());
                                        Drawable flag = getResources().getDrawable(flagid);
                                        mPilotName.setText(name);
                                        mPilotName.setCompoundDrawablesWithIntrinsicBounds(flag, null, null, null);
                                        int padding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics());
                                        mPilotName.setCompoundDrawablePadding(padding);
                                        mPilotTime.setText(time);
                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                    }
                                }
                            });
                        }
                    } catch (JSONException e){
                        e.printStackTrace();
                    }


                } catch (IOException e) {
                    mIsListening = false;
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        Log.i("BT", "NOT LISTENING ANYMORE!");
        closeSocket();
    }

    public void closeSocket(){
        Log.i("BT", "CLEANING UP");
        mIsListening = false;

        if (mmInStream != null) {
            try {
                mmInStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (mmSocket != null) {
            try {
                mmSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (mRestart) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mActivity.startListening();
                }
            });
        }
    }


}
