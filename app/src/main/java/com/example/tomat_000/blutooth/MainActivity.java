package com.example.tomat_000.blutooth;

import android.Manifest;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.util.Date;
import java.util.Set;
import java.util.UUID;



public class MainActivity extends AppCompatActivity implements LocationListener {

    TextView myLabel;
    EditText myTextBox;
    BluetoothAdapter mBluetoothAdapter;
    BluetoothSocket mmSocket;
    BluetoothDevice mmDevice;
    OutputStream mmOutPutStream;
    InputStream mmInputStream;
    Thread workerThread;
    byte[] readBuffer;
    int readBufferPostion = 0 ;
    int counter;
    volatile boolean stopWorker;
    private LocationManager locationManager;
    private Location location;
    private final int REQUEST_LOCATION = 200;
    String lattitude;
    String longitude;




    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final Button sendEmailButton = (Button) findViewById(R.id.sendEmail);
        final Button openButton = (Button) findViewById(R.id.open);
        Button sendButton = (Button) findViewById(R.id.send);
        final Button closeButton = (Button) findViewById(R.id.close);
        myLabel = (TextView) findViewById(R.id.label);
        myTextBox = (EditText) findViewById(R.id.entry);
        locationManager = (LocationManager) getSystemService(Service.LOCATION_SERVICE);
        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)!= PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION},REQUEST_LOCATION);
        } else{
            locationManager.requestLocationUpdates(locationManager.GPS_PROVIDER,100,2, (LocationListener) this);
            if (locationManager != null){
                location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
        }
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)){
            if(location != null){
                longitude = String.valueOf(location.getLongitude());
                lattitude = String.valueOf(location.getLatitude());
               // myLabel.setText(lattitude);


            }
            else{
                showGPSDisabledAlertToUser();
            }

        }










        //open button
        openButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try{
                    findBT();
                    openBT();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        });

        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try{
                    sendData();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try{
                    closeBT();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

    }




    private void showGPSDisabledAlertToUser() {
        final AlertDialog.Builder alertDialogueBuilder = new AlertDialog.Builder(this);
        alertDialogueBuilder.setMessage("GPS is disabled would you like to enable it?")
                .setCancelable(false)
                .setPositiveButton("Go to settings and enable gps ", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent callGPSSettingIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        startActivity(callGPSSettingIntent);
                    }
                });
        alertDialogueBuilder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        AlertDialog alert = alertDialogueBuilder.create();
        alert.show();

    }

    public void sendEmail(View view){
       String[] to_person  =  new String[]{"tom.atkinson12@btinternet.com"};
       // email address
       String subject = ("Device readings");
       String currentDateTimeString = DateFormat.getDateTimeInstance().format(new Date());
       // date and time
       String time = currentDateTimeString;
       String messageTxt =  myLabel.getText().toString();


       // gets blue tooth value
       Intent emailIntent = new Intent(Intent.ACTION_SEND);
       // sets up intent
       emailIntent.setType("text/html");
       emailIntent.putExtra(Intent.EXTRA_EMAIL, to_person);
       emailIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
       emailIntent.putExtra(Intent.EXTRA_TEXT, messageTxt + "\n" +  "Lattitude: " + lattitude +  "\n" + "Longitude:" + longitude + "\n"  + time  );
       // concatination of info and time
       //emailIntent.putExtra(Intent.EXTRA_TEXT, time);
       emailIntent.setType("text/plain");
       startActivity(Intent.createChooser(emailIntent,"Email"));


    }





    void findBT(){
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(mBluetoothAdapter == null){
            myLabel.setText("No bluetooth found");
            // error handler
        }
        if(!mBluetoothAdapter.isEnabled()){
            Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBluetooth, 0);
            // enables bluetooth
        }

        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if(pairedDevices.size() > 0){
            for (BluetoothDevice device : pairedDevices){
                if(device.getName().equals("HC-05")){
                    // device name
                    mmDevice = device;
                    break;
                }
            }
        }
        myLabel.setText("device found");

    }


    void openBT() throws IOException {
        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
        mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);
        mmSocket.connect();
        mmOutPutStream = mmSocket.getOutputStream();
        mmInputStream = mmSocket.getInputStream();

        beginListenForData();

        myLabel.setText("BlueTooth Opened");


    }

    void beginListenForData()
    {

        final byte delimiter = 10; //This is the ASCII code for a newline character


        stopWorker = false;
        readBufferPostion = 0;
        readBuffer = new byte[1024];
        // buffer reader
        final Handler handler = new Handler();
        workerThread = new Thread(new Runnable()
        {
            public void run()
            {
                while(!Thread.currentThread().isInterrupted() && !stopWorker)
                {
                    try
                    {
                        int bytesAvailable = mmInputStream.available();
                        if(bytesAvailable > 0)
                        {
                            byte[] packetBytes = new byte[bytesAvailable];
                            mmInputStream.read(packetBytes);
                            for(int i=0;i<bytesAvailable;i++)
                            {
                                byte b = packetBytes[i];
                                if(b == delimiter)
                                {

                                    byte[] encodedBytes = new byte[readBufferPostion];
                                    System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                    final String data = new String(encodedBytes, "US-ASCII");
                                    readBufferPostion = 0;

                                    handler.post(new Runnable()
                                    {
                                        public void run()
                                        {
                                            myLabel.setText(data);
                                        }
                                    });
                                }
                                else
                                {
                                    readBuffer[readBufferPostion++] = b;
                                }
                            }
                        }
                    }
                    catch (IOException ex)
                    {
                        stopWorker = true;
                    }
                }
            }
        });

        workerThread.start();
    }


    void sendData() throws IOException
    {
        String msg = myTextBox.getText().toString();
        msg += "\n";
        mmOutPutStream.write(msg.getBytes());
        myLabel.setText("Data Sent");
    }

    void  closeBT () throws IOException
    {
        stopWorker = true;
        mmOutPutStream.close();
        mmInputStream.close();
        mmSocket.close();
        myLabel.setText("Bluetooth closed");
        // closes bluetooth
    }


    @Override
    public void onLocationChanged(Location location) {
        lattitude = String.valueOf(location.getLatitude());
        longitude = String.valueOf(location.getLongitude());
       // myLabel.setText(lattitude);



    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {
        if (provider.equals(LocationManager.GPS_PROVIDER)) {
            showGPSDisabledAlertToUser();
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_LOCATION) {
            if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            }
        }
    }
}









