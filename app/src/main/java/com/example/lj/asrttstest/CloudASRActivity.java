package com.example.lj.asrttstest;

import android.content.Context;
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
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import android.widget.EditText;
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
import com.nuance.dragon.toolkit.vocon.ParamSpecs;

import org.json.JSONException;
import org.json.JSONObject;

import javax.xml.datatype.DatatypeConstants;


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

    //View
    private EditText editIpView;
    private Button connectToServerButton;
    private Button startGoogleAsrButton;
    private Button startNuanceAsrButton;
    private TextView resultTextView;

    /* Connect to the server */
    private String serverIP = "10.118.115.173";
    private final int serverPort = 13458;
    private Socket clientSocket;
    private BufferedWriter clientWriter;
    private BufferedReader clientReader;
    private Handler messageHandler;
    private String asrResult = "";
    private Context context;


    private int curResourceID = 1;
    private int[] allResourceID;
    private String[] allAudioFileNames;
    private String allAsrResult = "";
    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_cloud_asr);

        // UI initialization
        editIpView = (EditText) findViewById(R.id.serverIpEditText);
        connectToServerButton = (Button) findViewById(R.id.connectToServerButton);
        startNuanceAsrButton = (Button) findViewById(R.id.startCloudRecognitionButton);
        startGoogleAsrButton = (Button) findViewById(R.id.startGoogleAsrButton);
        resultTextView = (TextView) findViewById(R.id.cloudResultEditText);

        editIpView.setInputType(InputType.TYPE_CLASS_NUMBER);
        editIpView.setText("10.118.116.218");
//        startNuanceAsrButton.setEnabled(false);
        startGoogleAsrButton.setEnabled(false);

        connectToServerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startNuanceAsrButton.setEnabled(false);
                serverIP = editIpView.getText().toString();
            }
        });

        context = getApplicationContext();
        messageHandler = new Handler(){
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                if(msg.what == 1){
                    resultTextView.setText(asrResult);
                    curResourceID++;
                    if(curResourceID < allResourceID.length){
//                        new Thread()
                    }
                }
                if(msg.what == 2){
                    resultTextView.setText("Error");
                }
            }
        };

        _audioType = AudioType.SPEEX_WB;

        reCreateCloudRecognizer();

        processAllAudioFiles();

//        new Thread(new fileAsrThread(allResourceID[curResourceID]));

        startNuanceAsrButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startNuanceAsrButton.setEnabled(false);
                onClickStartNuanceASR(allResourceID[curResourceID]);
            }
        });
    }



    private void processAllAudioFiles(){
        Field[] fields = R.raw.class.getFields();
        allResourceID = new int[fields.length];
        allAudioFileNames = new String[fields.length];
        for(int i = 0; i < fields.length; i++){
            String file_name = fields[i].getName();
            int resource_id = getResources().getIdentifier(file_name, "raw", getPackageName());
            allResourceID[i] = resource_id;
            allAudioFileNames[i] = file_name;
//            Log.d("sss", "file name: "+file_name+"  ID: "+ String.valueOf(resource_id));
//            new Thread(new fileAsrThread(resource_id)).start();
        }
    }

    private void onClickStartNuanceASR(int resourceID) {
//        startNuanceAsrButton.setEnabled(false);
//        resultTextView.setText("");

        String resultmodeName = "No Partial Results";

        // Set-up audio chaining
//        recorder = new MicrophoneRecorderSource(AudioType.PCM_16k);
        recorder = new StreamingFileRecorderSource(
                AudioType.PCM_16k,
                400,
                resourceID,
                context);
//        Log.d("sss", "audio type: " + recorder.getAudioType());
//        Log.d("sss", "chunks: " + recorder.getChunksAvailable());
//        Log.d("sss", "isempty: " + recorder.getChunksAvailable());

        speexPipe = new SpeexEncoderPipe();
        endpointerPipe = new EndPointerPipe(new SpeechDetectionListener() {
            @Override
            public void onStartOfSpeech() {
//                resultTextView.setText("Start of Speech...");
            }

            @Override
            public void onEndOfSpeech() {
//                resultTextView.setText("End of Speech...");
//                _cloudRecognizer.processResult();
//                startNuanceAsrButton.setEnabled(true);
                stopRecording();
            }
        });

        // Start recording and recognition
        recorder.startRecording();
        speexPipe.connectAudioSource(recorder);
        endpointerPipe.connectAudioSource(speexPipe);
        _cloudRecognizer.startRecognition(createNuanceRecogSpec(resultmodeName),
                endpointerPipe,
                new CloudRecognizer.Listener() {
                    @Override
                    public void onResult(CloudRecognitionResult result) {
                        java.lang.String topResult = parseNuanceResults(result);
                        if (topResult != null) {
                            resultTextView.setText(topResult);
                            String[] tmp = allAudioFileNames[curResourceID].split("_");
                            String tmp_result = tmp[1]+"_"+tmp[2]+"\n"+topResult;
                            Log.d("sss", tmp_result);
                            allAsrResult += tmp[1]+"_"+tmp[2]+"\n"+topResult+"\n";
                            curResourceID++;
                            if(curResourceID < allResourceID.length){
                                onClickStartNuanceASR(allResourceID[curResourceID]);
                            }
                            else{
                                sendJsonToEmail(allAsrResult);
                            }
                        }
                    }

                    @Override
                    public void onError(CloudRecognitionError error) {
//                                resultTextView.setText(error.toString());
                        String err = error.toJSON().toString();
                        resultTextView.setText("speech not recognized");
                        String topResult = "Speech not recognized";
                        resultTextView.setText(topResult);
                        String[] tmp = allAudioFileNames[curResourceID].split("_");
                        allAsrResult += tmp[1]+"_"+tmp[2]+"\n"+topResult+"\n";
                        String tmp_result = tmp[1]+"_"+tmp[2]+"\n"+topResult;
                        Log.d("sss", tmp_result);
                        curResourceID++;
                        if(curResourceID < allResourceID.length){
                            onClickStartNuanceASR(allResourceID[curResourceID]);
                        }
                        else{
                            sendJsonToEmail(allAsrResult);
                        }
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

    private void sendJsonToEmail(String result) {
        Intent data = new Intent(Intent.ACTION_SENDTO);
        data.setData(Uri.parse("mailto:rpbloom@gmail.com"));
        data.putExtra(Intent.EXTRA_SUBJECT, "All Nuance ASR Result");
        data.putExtra(Intent.EXTRA_TEXT, result);
        startActivity(data);
    }

    private String getAsrResultJsonStr(int type, String result){
        JSONObject tmp = new JSONObject();
        try {
            if(type == 0) tmp.put("result", "###");
            else tmp.put("result", result);
            if (type == 1) {
                tmp.put("type", "google");
            }
            else if (type == 2){
                tmp.put("type", "nuance");
            }
            else{
                tmp.put("type", "end");
            }
        }catch (JSONException e){
            e.printStackTrace();
            return "\n";
        }
        return tmp.toString()+"\n";
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
        startGoogleAsrButton.setEnabled(false);
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getClass().getPackage().getName());
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH);
        startActivityForResult(intent, VOICE_RECOGNITION_REQUEST_CODE);
    }

    void  showToastMessage(String message){

        Toast.makeText(this,message,Toast.LENGTH_LONG).show();
    }

    private class fileAsrThread implements Runnable{

        private int resource_id = 0;

        public fileAsrThread(int _id){
            resource_id = _id;
        }

        @Override
        public void run() {
            if(resource_id > 0){
                onClickStartNuanceASR(resource_id);
            }
        }
    }

}