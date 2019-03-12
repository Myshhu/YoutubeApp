package com.myshhu.youtubebackgroundplayer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView;

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Objects;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;


public class floatingPlayerService extends Service {

    private WindowManager mWindowManager;
    private View floatingView;
    private EditText etSearch;
    private YouTubePlayerView playerView;
    private String[] API_KEYS_ARRAY = {com.myshhu.youtubebackgroundplayer.API_KEY.KEY,
            com.myshhu.youtubebackgroundplayer.API_KEY.KEY1};
    private int currentAPI_KEY = 0;
    private String API_KEY = API_KEYS_ARRAY[0];

    private String VIDEO_CODE;
    private float VIDEO_SECOND;
    private YouTubePlayer youTubePlayer;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void startMyOwnForeground() {
        String NOTIFICATION_CHANNEL_ID = "com.example.youtubelayout";
        String channelName = "YoutubeApp";
        NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_NONE);
        chan.setLightColor(Color.RED);
        chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        assert manager != null;
        manager.createNotificationChannel(chan);

        //Create intent for app resume
        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, StaticReferences.activity.getIntent(), PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
        Notification notification = notificationBuilder.setOngoing(true)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("Youtube app is running in background")
                .setPriority(NotificationManager.IMPORTANCE_MIN)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setContentIntent(pendingIntent)
                .build();

        startForeground(2, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Bundle extras = intent.getExtras();
        if (extras != null) {
            VIDEO_CODE = extras.getString("videoId", "");
            VIDEO_SECOND = extras.getFloat("second", 0) + 3.8f;
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startMyOwnForeground();
        } else {
            startForeground(1, new Notification());
        }
        StaticReferences.floatingPlayerService = this;

        //Use different flag for older Android versions
        int LAYOUT_FLAG;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            LAYOUT_FLAG = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            LAYOUT_FLAG = WindowManager.LayoutParams.TYPE_PHONE;
        }

        //Create view params
        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                LAYOUT_FLAG, 0,//WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;// | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;

        //Specify the view position
        //Initially view will be added to top-left corner
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 100;

        //Inflate the floating view layout we created
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_player, null);

        //Add the view to the window
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mWindowManager.addView(floatingView, params);

        com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView youTubePlayerView = floatingView.findViewById(R.id.ytPlayerView);
        youTubePlayerView.addYouTubePlayerListener(new AbstractYouTubePlayerListener() {
            @Override
            public void onReady(@NotNull YouTubePlayer player) {
                youTubePlayer = player;
                player.loadVideo(VIDEO_CODE, VIDEO_SECOND);
            }
        });

        BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context arg0, Intent intent) {
                String action = intent.getAction();
                Log.d("intents", "received intent " + action);
                System.out.println("received intent");
                if (Objects.equals(action, "finish_activity")) {
                    unregisterReceiver(this);
                    stopSelf();
                    // DO WHATEVER YOU WANT.
                } else if (Objects.equals(action, "play_video")) {
                    System.out.println("playing video");
                    youTubePlayer.loadVideo(intent.getExtras().getString("videoId", ""), 0);
                }
            }
        };

        registerReceiver(broadcastReceiver, new IntentFilter("finish_activity"));
        registerReceiver(broadcastReceiver, new IntentFilter("play_video"));

        Button gotoPlayer = floatingView.findViewById(R.id.btnGoToPlayer);
        gotoPlayer.setOnClickListener(v -> startActivity(StaticReferences.activity.getIntent()));
        etSearch = floatingView.findViewById(R.id.autoCompleteTextViewSearchBar);
        etSearch.setOnClickListener(v -> {
            params.flags = 0;//WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
            mWindowManager.updateViewLayout(floatingView, params);
        });

        floatingView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (etSearch.hasFocus()) {
                    params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;//| WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
                } else {
                    params.flags = 0;//WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
                }
                mWindowManager.updateViewLayout(floatingView, params);
            }
            return false;
        });

        //Perform searching
        floatingView.findViewById(R.id.btnSearch).setOnClickListener(v -> {
            EditText etSearch = floatingView.findViewById(R.id.autoCompleteTextViewSearchBar);
            String query = etSearch.getText().toString();
            etSearch.clearFocus();
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;//| WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
            mWindowManager.updateViewLayout(floatingView, params);

            new Thread(() -> {
                while (true) {
                    try {
                        HttpURLConnection connection;
                        URL url = new URL("https://www.googleapis.com/youtube/v3/search?part=snippet&q=" +
                                query + "&maxResults=1&type=video&key=" + API_KEY);
                        connection = (HttpURLConnection) url.openConnection();
                        connection.connect();

                        if (connection.getResponseCode() == 200) {
                            InputStream inputStream = connection.getInputStream();
                            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));

                            String line;
                            StringBuilder result = new StringBuilder();

                            while ((line = bufferedReader.readLine()) != null) {
                                result.append(line).append("\n");
                            }

                            JSONObject object = new JSONObject(result.toString());
                            String key = object.getJSONArray("items").
                                    getJSONObject(0).getJSONObject("id").
                                    getString("videoId");
                            youTubePlayer.loadVideo(key, 0);
                            break;
                        } else if (connection.getResponseCode() == 403) {
                            if (currentAPI_KEY == API_KEYS_ARRAY.length - 1) {
                                //Show toast when all keys are unavailable
                                Handler handler = new Handler(Looper.getMainLooper());
                                handler.post(() -> Toast.makeText(getApplicationContext(), "API limits exceeded, try again later", Toast.LENGTH_LONG).show());
                                currentAPI_KEY = 0;
                                API_KEY = API_KEYS_ARRAY[currentAPI_KEY];
                                break;
                            } else {
                                //Change API_KEY
                                API_KEY = API_KEYS_ARRAY[++currentAPI_KEY];
                                Log.d("AppInfo", "Changed key to " + currentAPI_KEY);
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        });

        //Move view around screen
        floatingView.findViewById(R.id.btnMove).setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            double deltaTime, startTime;
            boolean moved = false;
            boolean hidden = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        moved = false;

                        //Remember the initial position.
                        initialX = params.x;
                        initialY = params.y;

                        startTime = System.currentTimeMillis();

                        //Get the touch location
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_UP:
                        int XDiff = (int) (event.getRawX() - initialTouchX);
                        int YDiff = (int) (event.getRawY() - initialTouchY);

                        //The check for Xdiff <10 && YDiff< 10 because sometime elements moves a little while clicking.
                        if (XDiff < 10 && YDiff < 10) {

                            /*if (hidden) {
                                floatingView.findViewById(R.id.ytPlayerView).setVisibility(View.VISIBLE);
                            } else {
                                floatingView.findViewById(R.id.ytPlayerView).setVisibility(View.GONE);
                            }
                            hidden = !hidden;*/
                        }
                        //return false;
                    case MotionEvent.ACTION_MOVE:
                        //System.out.println("move called");

                        //Calculate the X and Y coordinates of the view.
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        mWindowManager.updateViewLayout(floatingView, params);

                        XDiff = (int) (event.getRawX() - initialTouchX);
                        YDiff = (int) (event.getRawY() - initialTouchY);
                        System.out.println("YDiff: " + YDiff);
                        if (XDiff > 10 || YDiff > 10) {
                            moved = true;
                        }
                        // return false;
                }

                deltaTime = (System.currentTimeMillis() - startTime);
                if (deltaTime > 1000 && !moved) {
                    Intent intent = new Intent("widget_finished");
                    sendBroadcast(intent);
                    stopSelf();
                }
                return false;
            }
        });

        //Resizing widget
        floatingView.findViewById(R.id.btnResize).setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            double deltaTime, startTime;

            @Override
            public boolean onTouch(View v, MotionEvent event) {

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:

                        //Remember the initial position.
                        initialX = params.x;
                        initialY = params.y;

                        //Get the touch location
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_UP:
                        int XDiff = (int) (event.getRawX() - initialTouchX);
                        int YDiff = (int) (event.getRawY() - initialTouchY);

                        //The check for Xdiff <10 && YDiff< 10 because sometime elements moves a little while clicking.
                        if (XDiff < 10 && YDiff < 10) {

                            /*Intent startMain = new Intent(Intent.ACTION_MAIN);
                            startMain.addCategory(Intent.CATEGORY_HOME);
                            startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(startMain);*/

                        }
                        //return false;
                    case MotionEvent.ACTION_MOVE:
                        XDiff = (int) (event.getRawX() - initialTouchX);
                        YDiff = (int) (event.getRawY() - initialTouchY);
                        if (Math.abs(XDiff) > 9 || Math.abs(YDiff) > 9) {
                            params.width = floatingView.getWidth() + XDiff;
                            initialTouchX = event.getRawX();
                            initialTouchY = event.getRawY();
                            mWindowManager.updateViewLayout(floatingView, params);
                        }
                }
                return false;
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopForeground(true);
        if (floatingView != null) mWindowManager.removeView(floatingView);
    }
}