package com.example.lj.asrttstest;

import android.content.Intent;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONException;
import org.json.JSONObject;


public class CloudASRActivity extends AppCompatActivity
{
    private static  final int GOOGLE_ASR_AUDIO_PLAY_GAP = 1300;

    private static final String TAG = "sss";

    private EditText editIpView;
    private Button connectToServerButton;
    private Button startGoogleAsrButton;
    private Button startNuanceAsrButton;
    private TextView resultTextView;

    private SpeechRecognizer speechRecognizer;

    /* Connect to the server */
    private String serverIP = "10.118.116.90";
    private final int serverPort = 14459;
    private Socket clientSocket;
    private BufferedWriter clientWriter;
    private BufferedReader clientReader;
    private Handler severMessageHandler;
    private String messageFromServer = "";
    private String googleAsrResult = "";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_cloud_asr);

        editIpView = (EditText) findViewById(R.id.serverIpEditText);
        connectToServerButton = (Button) findViewById(R.id.connectToServerButton);
        startNuanceAsrButton = (Button) findViewById(R.id.startCloudRecognitionButton);
        startGoogleAsrButton = (Button) findViewById(R.id.startGoogleAsrButton);
        resultTextView = (TextView) findViewById(R.id.cloudResultEditText);

        editIpView.setInputType(InputType.TYPE_CLASS_NUMBER);
        editIpView.setText("Server IP: "+serverIP);
        startNuanceAsrButton.setEnabled(false);
        startGoogleAsrButton.setEnabled(false);

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle bundle) {
                Log.d(TAG, "onReadyForSpeech");
            }

            @Override
            public void onBeginningOfSpeech() {
                Log.d(TAG, "onBeginningOfSpeech");
                resultTextView.setText("Speech begins");
            }

            @Override
            public void onRmsChanged(float v) {
                Log.d(TAG, "onRmsChanged");
            }

            @Override
            public void onBufferReceived(byte[] bytes) {
                Log.d(TAG, "onBufferReceived");
            }

            @Override
            public void onEndOfSpeech() {
                Log.d(TAG, "onEndOfSpeech");
                resultTextView.setText("Speech ends");
            }

            @Override
            public void onError(int i) {
                Log.d("sss", "ASR error");
                resultTextView.setText("Error");
                new Thread(new ClientSendThread("Speech not recognized")).start();
            }

            @Override
            public void onResults(Bundle bundle) {
                Log.d(TAG, "onResults");
                ArrayList<String> all_results = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                resultTextView.setText("ASR result: "+all_results.get(0));
                Log.d("sss", "ASR result: "+all_results.get(0));
                googleAsrResult = all_results.get(0);
                new Thread(new ClientSendThread("result-"+googleAsrResult)).start();
            }

            @Override
            public void onPartialResults(Bundle bundle) {
                Log.d(TAG, "onPartialResults");
            }

            @Override
            public void onEvent(int i, Bundle bundle) {
                Log.d(TAG, "onEvent");
            }
        });

        connectToServerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                serverIP = editIpView.getText().toString().split(": ")[1];
                new Thread(new ClientThread()).start();
            }
        });

        startGoogleAsrButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new Thread(new ClientSendThread("start")).start();
                try {
                    Thread.sleep(GOOGLE_ASR_AUDIO_PLAY_GAP);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                startGoogleASR(getCurrentFocus());
            }
        });

        severMessageHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                if (msg.what == 0) {
                    startGoogleASR(getCurrentFocus());
                } else if (msg.what == 1) {
                    resultTextView.setText("Msg from Server: "+messageFromServer);
                    startGoogleAsrButton.performClick();
                } else if (msg.what == 2) {
                    resultTextView.setText("Successful connect to server");
                    connectToServerButton.setEnabled(false);
                    startGoogleAsrButton.setEnabled(true);
                } else if (msg.what == 3) {
                    resultTextView.setText("Fail to connect to server");
                    connectToServerButton.setEnabled(true);
                } else if (msg.what == 4){
                    resultTextView.setText("Finish all audio Files");
                    connectToServerButton.setEnabled(true);
                    startGoogleAsrButton.setEnabled(false);
                }
            }
        };

    }

    public void startGoogleASR(View view){
//        resultTextView.setText("");
        startGoogleAsrButton.setEnabled(false);
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getClass().getPackage().getName());
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH);
//        startActivityForResult(intent, VOICE_RECOGNITION_REQUEST_CODE);
        speechRecognizer.startListening(intent);
    }

    @Override
    protected void onActivityResult(int requestCode,int resultCode, Intent data){
        startGoogleAsrButton.setEnabled(true);
        if (resultCode == RESULT_OK){
            ArrayList<String> textMatchlist = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);

            if (!textMatchlist.isEmpty()){
                resultTextView.setText("ASR result: "+textMatchlist.get(0));
                Log.d("sss", "ASR result: "+textMatchlist.get(0));
                googleAsrResult = textMatchlist.get(0);
                new Thread(new ClientSendThread("result-"+googleAsrResult)).start();
            }
        }
        else{
            Log.d("sss", "ASR error");
            new Thread(new ClientSendThread("error")).start();
        }
//        super.onActivityResult(requestCode, resultCode, data);
    }

    void  showToastMessage(String message){

        Toast.makeText(this,message,Toast.LENGTH_LONG).show();
    }

    private class ClientThread implements Runnable{
        @Override
        public void run() {
            try{
                clientSocket = new Socket();
                clientSocket.connect(new InetSocketAddress(serverIP, serverPort),5000);
                clientWriter = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream(),"utf-8"));
                clientReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                new Thread(new ClientReceiveThread()).start();
                Message msg = new Message();
                msg.what = 2;
                severMessageHandler.sendMessage(msg);
            }catch (Exception e){
                Log.d("sss", "Fail to connect to the server");
                e.printStackTrace();
                Message msg = new Message();
                msg.what = 3;
                severMessageHandler.sendMessage(msg);
            }
        }
    }

    private class ClientSendThread implements Runnable{
        private String msgToSend = "";

        public ClientSendThread(String msg){
            msgToSend = msg;
        }

        @Override
        public void run() {
            try {
                clientWriter.write(msgToSend);
                clientWriter.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class ClientReceiveThread implements Runnable{
        @Override
        public void run() {
            try {
                while(clientSocket.isConnected()){
                    messageFromServer = clientReader.readLine();
                    if(messageFromServer == null) continue;
                    Log.d("sss", "from server: "+messageFromServer);
                    Message cmd = new Message();
                    if(messageFromServer.equals("rev")) {
                        cmd.what = 1;
                        severMessageHandler.sendMessage(cmd);
                    }
                    if(messageFromServer.equals("finish")) {
                        cmd.what = 4;
                        severMessageHandler.sendMessage(cmd);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}