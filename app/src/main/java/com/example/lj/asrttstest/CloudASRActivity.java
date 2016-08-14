package com.example.lj.asrttstest;

import android.content.Intent;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.speech.RecognizerIntent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
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

import android.widget.TextView;
import android.widget.Toast;
/////////test git
import com.example.lj.asrttstest.info.AppInfo;
import com.nuance.dragon.toolkit.audio.AudioChunk;
import com.nuance.dragon.toolkit.audio.AudioType;
import com.nuance.dragon.toolkit.audio.SpeechDetectionListener;
import com.nuance.dragon.toolkit.audio.pipes.ConverterPipe;
import com.nuance.dragon.toolkit.audio.pipes.EndPointerPipe;
import com.nuance.dragon.toolkit.audio.pipes.OpusEncoderPipe;
import com.nuance.dragon.toolkit.audio.pipes.SpeexEncoderPipe;
import com.nuance.dragon.toolkit.audio.sources.BurstFileRecorderSource;
import com.nuance.dragon.toolkit.audio.sources.MicrophoneRecorderSource;
import com.nuance.dragon.toolkit.audio.sources.RecorderSource;
import com.nuance.dragon.toolkit.audio.sources.StreamingFileRecorderSource;
import com.nuance.dragon.toolkit.calllog.CalllogManager;
import com.nuance.dragon.toolkit.calllog.CalllogManager.CalllogDataListener;
import com.nuance.dragon.toolkit.calllog.CalllogSender;
import com.nuance.dragon.toolkit.calllog.CalllogSender.SenderListener;
import com.nuance.dragon.toolkit.calllog.SessionEvent;
import com.nuance.dragon.toolkit.calllog.SessionEventBuilder;
import com.nuance.dragon.toolkit.cloudservices.CloudConfig;
import com.nuance.dragon.toolkit.cloudservices.CloudServices;
import com.nuance.dragon.toolkit.cloudservices.DictionaryParam;
import com.nuance.dragon.toolkit.cloudservices.recognizer.CloudRecognitionError;
import com.nuance.dragon.toolkit.cloudservices.recognizer.CloudRecognitionResult;
import com.nuance.dragon.toolkit.cloudservices.recognizer.CloudRecognizer;
import com.nuance.dragon.toolkit.cloudservices.recognizer.RecogSpec;
import com.nuance.dragon.toolkit.data.Data;
import com.nuance.dragon.toolkit.util.Logger;


public class CloudASRActivity extends AppCompatActivity
{
    private static  final int VOICE_RECOGNITION_REQUEST_CODE = 1001;

    private CloudServices      _cloudServices;
    private CloudRecognizer    _cloudRecognizer;
    private RecorderSource<AudioChunk>recorder;
    private ConverterPipe<AudioChunk, AudioChunk> _encoder;
    private SpeexEncoderPipe speexPipe;
    private EndPointerPipe endpointerPipe;

    // call log feature
    private SessionEvent       _appSessionLeadEvent;
    private String             _appLeadSessionId;
    private CalllogSender      _calllogSender;
    private AudioType          _audioType;
    private TTSService         _ttsService;
    private TextView resultTextView;

    /* Recording audio and play */
    private int curAudioFileID = 0;
    private MediaRecorder googleRecorder;
    private File googleAudioFile;
    private File googleAudioDir;
    private boolean sdCardExist;
    private String tmpAudioName = "xxx";

    /* Connect to the server */
    private final String serverIP = "192.168.2.6";
    private final int serverPort = 8888;
    private Socket clientSocket;
    private BufferedWriter clientWriter;
    private BufferedReader clientReader;
    private Handler severMessageHandler;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_cloud_asr);

        // UI initialization
        resultTextView = (TextView)findViewById(R.id.cloudResultEditText);
        final Button startRecognitionButton = (Button) findViewById(R.id.startCloudRecognitionButton);
        startRecognitionButton.setEnabled(true);
        final Button startGoogleAsrButton = (Button) findViewById(R.id.startGoogleAsrButton);
        startGoogleAsrButton.setEnabled(false);

        final Button startRecordingButton = (Button) findViewById(R.id.startAudioRecordButton);
        final Button stopRecordingButton = (Button) findViewById(R.id.stopAudioRecordButton);

        new Thread(new ClientThread()).start();

        severMessageHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                if(msg.what == 0){
                    startGoogleASR(getCurrentFocus());
                }
                else if(msg.what == 1){
                    startRecognitionButton.performClick();
                }
            }
        };

        _audioType = AudioType.SPEEX_WB;

        reCreateCloudRecognizer();

        startRecognitionButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                startRecognitionButton.setEnabled(false);
//                initGoogleRecoder();
//                stopRecognitionButton.setEnabled(true);
                if(curAudioFileID >= 3){
                    startRecognitionButton.setEnabled(false);
                    resultTextView.setText("Finish All Files Nuance ASR");
                    return;
                }
                resultTextView.setText("");

                String resultmodeName = "No Partial Results";

                // Set-up audio chaining
                recorder = new MicrophoneRecorderSource(AudioType.PCM_16k);
//                curAudioFileID++;
//                int resourceID = (getResources().getIdentifier("audio" + String.valueOf(curAudioFileID) + "_16k_pcm", "raw", getPackageName()));
//                Log.d("sss", " resID: "+new Integer(resourceID).toString());
//                Log.d("sss", "audio" + String.valueOf(curAudioFileID) + "_16k");
//                recorder = new StreamingFileRecorderSource(
//                        AudioType.PCM_16k,
//                        400,
//                        getResources().getIdentifier("audio" + String.valueOf(curAudioFileID) + "_16k_pcm", "raw", getPackageName()),
//                        getApplicationContext());
////                        googleAudioFile.getAbsolutePath(),
////                        true);
//                Log.d("sss", " resId: " + getResources().getIdentifier("audio" + String.valueOf(curAudioFileID) + "_16k_pcm", null, getPackageName()));
//                Log.d("sss", "audio type: " + recorder.getAudioType());
//                Log.d("sss", "chunks: " + recorder.getChunksAvailable());
//                Log.d("sss", "isempty: " + recorder.getChunksAvailable());

                speexPipe = new SpeexEncoderPipe();
                endpointerPipe = new EndPointerPipe(new SpeechDetectionListener() {
                    @Override
                    public void onStartOfSpeech() {
                        resultTextView.setText("Start of Speech...");
                    }

                    @Override
                    public void onEndOfSpeech() {
                        resultTextView.setText("End of Speech...");
                        _cloudRecognizer.processResult();
                        startRecognitionButton.setEnabled(true);
                        stopRecording();
                        new Thread(new ClientSendThread()).start();
                    }
                });

                // Start recording and recognition
                recorder.startRecording();
                speexPipe.connectAudioSource(recorder);
                endpointerPipe.connectAudioSource(speexPipe);
                _cloudRecognizer.startRecognition(createNuanceRecogSpec(resultmodeName),
                        endpointerPipe,
                        new CloudRecognizer.Listener()
                        {
                            @Override
                            public void onResult(CloudRecognitionResult result) {
                                java.lang.String topResult = parseNuanceResults(result);
                                if(topResult != null) {
                                    resultTextView.setText(topResult);
                                }
                                startRecognitionButton.setEnabled(true);
//                                stopGoogleRecording();
//                                playFile(googleAudioFile);
                            }

                            @Override
                            public void onError(CloudRecognitionError error) {
//                                resultTextView.setText(error.toString());
                                String err = error.toJSON().toString();
                                resultTextView.setText("speech not recognized");
                                Log.d("sss", err);
                                startRecognitionButton.setEnabled(true);
//                                stopGoogleRecording();
//                                playFile(googleAudioFile);
                            }

                            @Override
                            public void onTransactionIdGenerated(String transactionId) {
                            }
                        });

                if (_appSessionLeadEvent != null) {
                    SessionEventBuilder eventBuilder = _appSessionLeadEvent.createChildEventBuilder("cloud recognition");
                    eventBuilder.putString("start", "recognition started");
                    eventBuilder.commit();
                }
            }
        });

        startRecordingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                initGoogleRecoder();
                startRecordingButton.setEnabled(false);
            }
        });

        stopRecordingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopGoogleRecording();
                if (googleAudioFile != null && googleAudioFile.exists()) {
                    playFile(googleAudioFile);
                }
                else{
                    Log.d("sss", "No audio file");
                }
                startRecordingButton.setEnabled(true);
            }
        });
    }

    private void initGoogleRecoder(){
        sdCardExist = Environment.getExternalStorageState().equals(
                android.os.Environment.MEDIA_MOUNTED);
        if (sdCardExist) {
            googleAudioDir = Environment.getExternalStorageDirectory();
        }
        try {
            googleAudioFile = File.createTempFile(tmpAudioName+String.valueOf(System.currentTimeMillis()), ".amr",
                    googleAudioDir);
        } catch (IOException e) {
            e.printStackTrace();
        }
        googleRecorder = new MediaRecorder();
        googleRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        googleRecorder.setOutputFormat(MediaRecorder.OutputFormat.RAW_AMR);
        googleRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        googleRecorder.setOutputFile(googleAudioFile.getAbsolutePath());
        try {
            googleRecorder.prepare();
            googleRecorder.start();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void stopGoogleRecording(){
        googleRecorder.stop();
        googleRecorder.release();
    }

    private void playFile(File f)
    {
        Intent intent = new Intent();
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setAction(android.content.Intent.ACTION_VIEW);
        String type = getFileType(f);
        intent.setDataAndType(Uri.fromFile(f), type);
        startActivity(intent);
    }

    private String getFileType(File f)
    {
        String end = f.getName().substring(
                f.getName().lastIndexOf(".") + 1, f.getName().length())
                .toLowerCase();
        String type = "";
        if (end.equals("mp3") || end.equals("aac") || end.equals("aac")
                || end.equals("amr") || end.equals("mpeg")
                || end.equals("mp4"))
        {
            type = "audio";
        } else if (end.equals("jpg") || end.equals("gif")
                || end.equals("png") || end.equals("jpeg"))
        {
            type = "image";
        } else
        {
            type = "*";
        }
        type += "/*";
        return type;
    }

    private void stopRecording() {
        if (endpointerPipe != null) {
            endpointerPipe.disconnectAudioSource();
            endpointerPipe = null;
        }

        if (speexPipe != null) {
            speexPipe.disconnectAudioSource();
            speexPipe = null;
        }

        if (recorder != null) {
            recorder.stopRecording();
            recorder = null;
        }
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();

        if (recorder != null)
            recorder.stopRecording();
        recorder = null;

        if (_encoder != null) {
            _encoder.disconnectAudioSource();
            _encoder.release();
        }
        _encoder = null;

        if (_cloudRecognizer != null)
            _cloudRecognizer.cancel();
        _cloudRecognizer = null;

        if (_cloudServices != null)
            _cloudServices.release();
        _cloudServices = null;
    }

    private RecogSpec createNuanceRecogSpec(String resultModeName) {
        // Create a sample recognition spec. based on the "NVC_ASR_CMD"
        Data.Dictionary settings = new Data.Dictionary();
        settings.put("dictation_type", "dictation");
        settings.put("dictation_language", "eng-USA");
        if (_appLeadSessionId != null)
            settings.put(CalllogManager.CALLLOG_APP_TRANSACTION_REF_EVENT, _appLeadSessionId);
        RecogSpec retRecogSpec = new RecogSpec("NVC_ASR_CMD", settings, "AUDIO_INFO");

        // Also, add necessary "REQUEST_INFO" parameter
        Data.Dictionary requestInfo = new Data.Dictionary();
        requestInfo.put("start", 0);
        requestInfo.put("end", 0);
        requestInfo.put("text", "");

        if (resultModeName.equals("Utterance Detection Default"))
            requestInfo.put("intermediate_response_mode", "UtteranceDetectionWithCompleteRecognition");
        else if (resultModeName.equals("Utterance Detection Very Aggressive")) {
            requestInfo.put("intermediate_response_mode", "UtteranceDetectionWithCompleteRecognition");
            requestInfo.put("utterance_detection_silence_duration", "VeryAggressive");
        }
        else if (resultModeName.equals("Utterance Detection Aggressive")) {
            requestInfo.put("intermediate_response_mode", "UtteranceDetectionWithCompleteRecognition");
            requestInfo.put("utterance_detection_silence_duration", "Aggressive");
        }
        else if (resultModeName.equals("Utterance Detection Average")) {
            requestInfo.put("intermediate_response_mode", "UtteranceDetectionWithCompleteRecognition");
            requestInfo.put("utterance_detection_silence_duration", "Average");
        }
        else if (resultModeName.equals("Utterance Detection Conservative")) {
            requestInfo.put("intermediate_response_mode", "UtteranceDetectionWithCompleteRecognition");
            requestInfo.put("utterance_detection_silence_duration", "Conservative");
        }
        else if (resultModeName.equals("Streaming Results")) {
            requestInfo.put("intermediate_response_mode", "NoUtteranceDetectionWithPartialRecognition");
            _cloudRecognizer.setResultCadence(500);
        }

        retRecogSpec.addParam(new DictionaryParam("REQUEST_INFO", requestInfo));

        return retRecogSpec;
    }

    private String parseNuanceResults(CloudRecognitionResult cloudResult)
    {
        Data.Dictionary processedResult = cloudResult.getDictionary();

        // Parse results based on the "NVC_ASR_CMD"
        //java.lang.String prompt = ((Data.String)results.get("prompt")).Value;
        //Data.Sequence transcriptions = (Data.Sequence) results.get("transcriptions");
        //Data.Sequence confidences = (Data.Sequence) results.get("confidences");

        // The processed result has "prompt" == "warning", "transcriptions" == "choices"
        if (processedResult == null || processedResult.getString("prompt") == null )
            return null;

        java.lang.String prompt = processedResult.getString("prompt").value;
        Data.Sequence transcriptions = processedResult.getSequence("transcriptions");
        //Data.Sequence scores = processedResult.getSequence("confidences");

        int len = transcriptions.size();
        //len = (confidences.size() == len) ? len : 0;

        ArrayList<String> sentences = new ArrayList<java.lang.String>();

        for (int idx = 0; idx < len; idx++) {
            Data.String text = transcriptions.getString(idx);
            //Data.Integer score = scores.getInt(idx);

            sentences.add(text.value);
        }

        return (len == 0 ? prompt : sentences.get(0));
    }

    private void reCreateCloudRecognizer()
    {
        if (_cloudRecognizer != null)
            _cloudRecognizer.cancel();
        _cloudRecognizer = null;

        if (_cloudServices != null)
            _cloudServices.release();
        _cloudServices = null;

        // Cloud services initialization
        _cloudServices = CloudServices.createCloudServices(CloudASRActivity.this,
                new CloudConfig(AppInfo.Host, AppInfo.Port, AppInfo.AppId, AppInfo.AppKey, _audioType, _audioType, false));

        _calllogSender = CalllogManager.createCalllogSender(new SenderListener() {

            @Override
            public void succeeded(byte[] data) {
                Logger.debug(this, "call log data: " + data + " is sent");
            }

            @Override
            public void failed(short errorCode, byte[] data) {
                Logger.debug(this, "call log data: " + data + " failed on sending error code: " + errorCode + ". Save me! ");
            }
        });

        CalllogManager.registerCalllogDataListener(new CalllogDataListener() {

            @Override
            public void callLogDataGenerated(byte[] callLogData, List<String> sessionEventIds) {
                Logger.debug(this, "call log data: " + callLogData + " is generated");
                _calllogSender.send(callLogData);
            }
        });

        SessionEventBuilder appSessionEventBuilder = CalllogManager.logAppEvent("SampleAppEvent", "2.0");

        if (appSessionEventBuilder != null) {

            _appSessionLeadEvent = appSessionEventBuilder.commit();
            _appLeadSessionId = _appSessionLeadEvent.getId();
            Logger.debug(this, "application session event lead id is: " + _appLeadSessionId);
        }

        // Cloud Recognizer initialization
        _cloudRecognizer = new CloudRecognizer(_cloudServices);
    }


    public void startGoogleASR(View view){
//        resultTextView.setText("");
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getClass().getPackage().getName());
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH);
        startActivityForResult(intent, VOICE_RECOGNITION_REQUEST_CODE);

    }

    @Override
    protected void onActivityResult(int requestCode,int resultCode,Intent data){
//        stopGoogleRecording();
//        playFile(googleAudioFile);
        if (resultCode == RESULT_OK){
            ArrayList<String> textMatchlist = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);

            if (!textMatchlist.isEmpty()){
                resultTextView.setText(textMatchlist.get(0));
//                if (textMatchlist.get(0).contains("search")){
//                    String searchQuery = textMatchlist.get(0).replace("search"," ");
//                    Intent search = new Intent(Intent.ACTION_WEB_SEARCH);
//                    search.putExtra(SearchManager.QUERY,searchQuery);
//                    startActivity(search);
//                }
//                else {
//                    mlvTextMatches.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_expandable_list_item_1,textMatchlist));
//                }
            }
        }
        else if (resultCode == RecognizerIntent.RESULT_AUDIO_ERROR){
            showToastMessage("Audio Error");

        }
        else if ((resultCode == RecognizerIntent.RESULT_CLIENT_ERROR)){
            showToastMessage("Client Error");

        }
        else if (resultCode == RecognizerIntent.RESULT_NETWORK_ERROR){
            showToastMessage("Network Error");
        }
        else if (resultCode == RecognizerIntent.RESULT_NO_MATCH){
            showToastMessage("No Match");
        }
        else if (resultCode == RecognizerIntent.RESULT_SERVER_ERROR){
            showToastMessage("Server Error");
        }
        super.onActivityResult(requestCode, resultCode, data);
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
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    private class ClientSendThread implements Runnable{
        @Override
        public void run() {
            try {
                clientWriter.write("End\n");
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
                    String msg = clientReader.readLine();
                    Message cmd = new Message();
                    if(msg.equals("Google")){
                        cmd.what = 0;
                    }
                    if(msg.equals("Nuance")){
                        cmd.what = 1;
                    }
                    severMessageHandler.sendMessage(cmd);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}