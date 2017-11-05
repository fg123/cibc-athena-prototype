package me.felixguo.cibcmobilebanking;

import android.Manifest;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.support.design.widget.NavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Date;

import ai.api.AIServiceException;
import ai.api.android.AIConfiguration;
import ai.api.android.AIDataService;
import ai.api.model.AIError;
import ai.api.model.AIRequest;
import ai.api.model.AIResponse;
import ai.api.ui.AIButton;
import co.intentservice.chatui.ChatView;
import co.intentservice.chatui.models.ChatMessage;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    TextView mAthenaTextView;
    FrameLayout mAthenaTextViewWrapper;
    LinearLayout mAthenaRecentlyPicker;
    ChatView chatView;
    LinearLayout mMainContent;
    LinearLayout mAthenaContent;
    AIButton aiButton;
    TextToSpeech mTextToSpeech;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        checkPermissions();
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open,
                R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        mAthenaTextView = (TextView) findViewById(R.id.athenaText);
        mAthenaTextViewWrapper = (FrameLayout) findViewById(R.id.athenaTextWrapper);
        mAthenaRecentlyPicker = (LinearLayout) findViewById(R.id.recentlyUsedWrapper);
        chatView = (ChatView) findViewById(R.id.chat_view);
        mMainContent = (LinearLayout) findViewById(R.id.main_content);
        mAthenaContent = (LinearLayout) findViewById(R.id.athena_content);

        final AIConfiguration config = new AIConfiguration("c3c0504247e5443281077a1c55aeff41 ",
                AIConfiguration.SupportedLanguages.English,
                AIConfiguration.RecognitionEngine.System);
        defaultt();
        chatView.setOnSentMessageListener(new ChatView.OnSentMessageListener() {
            @Override
            public boolean sendMessage(ChatMessage chatMessage) {
                // perform actual message sending
                final AIDataService aiDataService = new AIDataService(getApplicationContext(),
                        config);

                final AIRequest aiRequest = new AIRequest();
                aiRequest.setQuery(chatMessage.getMessage());
                new AsyncTask<AIRequest, Void, AIResponse>() {
                    @Override
                    protected AIResponse doInBackground(AIRequest... requests) {
                        final AIRequest request = requests[0];
                        try {
                            final AIResponse response = aiDataService.request(request);
                            return response;
                        } catch (AIServiceException e) {
                            Log.e("CIBC", "Athena Exception: " + e.getMessage());
                        }
                        return null;
                    }

                    @Override
                    protected void onPostExecute(AIResponse aiResponse) {
                        Log.v("CIBC", "Athena " + aiResponse);
                        if (aiResponse != null) {
                            responseFromAI(aiResponse, false);
                        }
                    }
                }.execute(aiRequest);
                return true;
            }

        });
        mTextToSpeech = new TextToSpeech(getApplicationContext(),
                new TextToSpeech.OnInitListener() {
                    @Override
                    public void onInit(int status) {
                        mTextToSpeech.setSpeechRate(1.5F);
                    }
                });

        aiButton = (AIButton) findViewById(R.id.micButton);

        aiButton.initialize(config);
        aiButton.setResultsListener(new AIButton.AIButtonListener() {
            @Override
            public void onResult(final AIResponse result) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        responseFromAI(result, true);
                    }
                });
            }

            @Override
            public void onError(final AIError error) {

            }

            @Override
            public void onCancelled() {

            }
        });
        Date d = Calendar.getInstance().getTime();
        chatView.addMessage(new ChatMessage(
                "Hi! I'm Athena! I'm your personal banking assistant. Ask me anything!",
                d.getTime(), ChatMessage.Type.RECEIVED));

    }

    void responseFromAI(AIResponse aiResponse, boolean showResolved) {
        Date d = Calendar.getInstance().getTime();
        if (showResolved) {
            chatView.addMessage(
                    new ChatMessage(aiResponse.getResult().getResolvedQuery(), d.getTime(),
                            ChatMessage.Type.SENT));
        }
        String result = aiResponse.getResult().getFulfillment().getSpeech();
        chatView.addMessage(new ChatMessage(
                result, d.getTime(),
                ChatMessage.Type.RECEIVED));
        int resID = getResources().getIdentifier("sure", "raw", getPackageName());

        if (aiResponse.getResult().getAction().equals("touch_id")) {
            resID = getResources().getIdentifier("touch_id", "raw", getPackageName());
        } else if (aiResponse.getResult().getAction().equals("bank_transfer")) {
            showTransfer();
        } else if (aiResponse.getResult().getAction().equals("confirm")) {
            resID = getResources().getIdentifier("ok_will_call", "raw", getPackageName());
        }
        MediaPlayer mediaPlayer = MediaPlayer.create(this, resID);
        mediaPlayer.start();
        //mTextToSpeech.speak(result, QUEUE_ADD, null, result);
    }

    private void showTransfer() {
        final Date d = Calendar.getInstance().getTime();
        TransferDialog td = new TransferDialog();
        td.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                int resID = getResources().getIdentifier("call_tomorrow_confirm", "raw",
                        getPackageName());
                MediaPlayer mediaPlayer = MediaPlayer.create(MainActivity.this, resID);
                mediaPlayer.start();
                chatView.addMessage(new ChatMessage(
                        "Would you like me to give you a call tomorrow to confirm the transaction?",
                        d.getTime(),
                        ChatMessage.Type.RECEIVED));
                aiButton.startListening();
            }
        });
        td.show(getFragmentManager(), "dialog");

    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    0);

        }
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_camera) {
            // Handle the camera action
            defaultt();
            //mAthenaTextViewWrapper.setVisibility(View.VISIBLE);
        } else if (id == R.id.nav_gallery) {
            mMainContent.setVisibility(View.VISIBLE);
            mAthenaContent.setVisibility(View.GONE);
            mAthenaTextView.setText("Here are some quick access links!");
            mAthenaRecentlyPicker.setVisibility(View.VISIBLE);
            //mAthenaTextViewWrapper.setVisibility(View.GONE);
        } else if (id == R.id.nav_slideshow) {
            mAthenaContent.setVisibility(View.VISIBLE);
            mMainContent.setVisibility(View.GONE);
        }
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    void defaultt() {
        mMainContent.setVisibility(View.VISIBLE);
        mAthenaContent.setVisibility(View.GONE);
        mAthenaTextView.setText(
                "Hey Felix! My name is Athena. Click me for a tour of the app to see what you"
                        + " can do!");
        mAthenaRecentlyPicker.setVisibility(View.GONE);
    }
}

