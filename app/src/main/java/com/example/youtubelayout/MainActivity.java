package com.example.youtubelayout;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity {

    private String VIDEO_CODE = "GdNwaa1m1Yo";
    private String API_KEY = com.example.youtubelayout.API_KEY.KEY;
    private YouTubePlayer youTubePlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView youTubePlayerView = findViewById(R.id.youtubePlayerView);
        youTubePlayerView.enableBackgroundPlayback(true);
        youTubePlayerView.addYouTubePlayerListener(new AbstractYouTubePlayerListener() {
            @Override
            public void onReady(@NotNull YouTubePlayer player) {
                player.loadVideo(VIDEO_CODE, 0);
                youTubePlayer = player;
            }
        });
    }

    public void btnSetClick(View view) {
        EditText etSearch = findViewById(R.id.etSearch);
        String query = etSearch.getText().toString();

        new Thread(() -> {
            try {
                HttpURLConnection connection;
                URL url = new URL("https://www.googleapis.com/youtube/v3/search?part=snippet&q=" +
                        query + "&maxResults=10&type=video&key=" + API_KEY);
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

                    /*String key = object.getJSONArray("items").
                            getJSONObject(0).getJSONObject("id").
                            getString("videoId");
                    youTubePlayer.loadVideo(key, 0);*/

                    processJSONObject(object);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void processJSONObject(JSONObject object) {
        LinearLayout linearLayoutItems = findViewById(R.id.linearLayoutItems);
        runOnUiThread(linearLayoutItems::removeAllViews);
        JSONArray results;

        try {
            results = object.getJSONArray("items");
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }

        for(int i = 0; i < results.length(); i++) {
            LayoutInflater layoutInflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View convertView = layoutInflater.inflate(R.layout.scrollview_item, null);
            JSONObject currentJSONObject;
            final String videoKey;
            try {
                currentJSONObject = results.getJSONObject(i);
                videoKey = currentJSONObject.getJSONObject("id").
                        getString("videoId");
            } catch (JSONException e) {
                e.printStackTrace();
                return;
            }
            convertView.setOnClickListener(v -> youTubePlayer.loadVideo(videoKey, 0));

            try {
                TextView tvTitle = convertView.findViewById(R.id.tvTitle);
                tvTitle.setText(currentJSONObject.getJSONObject("snippet").
                        getString("title"));
                TextView tvChannelTitle = convertView.findViewById(R.id.tvChannelTitle);
                tvChannelTitle.setText(currentJSONObject.getJSONObject("snippet").
                        getString("channelTitle"));
                TextView tvViews = convertView.findViewById(R.id.tvViews);
                setVideoViews(videoKey, tvViews);

                final ImageView imageViewVideo = convertView.findViewById(R.id.imageViewVideo);
                new Thread(() -> {
                    try {
                        //Load image from URL
                        Bitmap bitmap = BitmapFactory.decodeStream(
                                (InputStream) new URL(currentJSONObject.getJSONObject("snippet").getJSONObject("thumbnails").getJSONObject("medium").
                                        getString("url")).getContent());
                        //Set image to imageView
                        runOnUiThread(() -> imageViewVideo.setImageBitmap(bitmap));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }).start();

            } catch (JSONException e) {
                e.printStackTrace();
                return;
            }

            runOnUiThread(() -> linearLayoutItems.addView(convertView));

        }
    }

    private void setVideoViews(String videoKey, TextView tvViews) {
        new Thread(() -> {
            try {
                HttpURLConnection connection;
                URL url = new URL("https://www.googleapis.com/youtube/v3/videos?part=statistics&id=" + videoKey + "&key=" + API_KEY);
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
                    tvViews.setText(object.getJSONArray("items").getJSONObject(0).
                            getJSONObject("statistics").getString("viewCount") + " wyświetlenia");

                    /*String key = object.getJSONArray("items").
                            getJSONObject(0).getJSONObject("id").
                            getString("videoId");
                    youTubePlayer.loadVideo(key, 0);*/
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    /*public void btnSetClick(View view) {
        LinearLayout linearLayout = findViewById(R.id.linearLayoutItems);

        LayoutInflater layoutInflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View convertView = layoutInflater.inflate(R.layout.scrollview_item, null);

        convertView.setOnClickListener(v -> System.out.println("you clicked me"));

        final ImageView imageViewVideo = convertView.findViewById(R.id.imageViewVideo);
        new Thread(() -> {
            try {
                //Load image from URL
                Bitmap bitmap = BitmapFactory.decodeStream(
                        (InputStream) new URL("https://i.ytimg.com/vi/8CdcCD5V-d8/mqdefault.jpg").getContent());
                //Set image to imageView
                runOnUiThread(() -> imageViewVideo.setImageBitmap(bitmap));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        TextView tvTitle = convertView.findViewById(R.id.tvTitle);
        tvTitle.setText("Title");
        TextView tvChannelTitle = convertView.findViewById(R.id.tvChannelTitle);
        tvChannelTitle.setText("Channel Title");
        TextView tvViews = convertView.findViewById(R.id.tvViews);
        tvViews.setText("12345");

        linearLayout.addView(convertView);
    }*/
}
