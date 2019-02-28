package com.example.youtubelayout;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
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
import java.util.HashMap;

public class MainActivity extends AppCompatActivity {

    //private String VIDEO_CODE = "GdNwaa1m1Yo";
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
                //player.loadVideo(VIDEO_CODE, 0);
                youTubePlayer = player;
            }
        });
    }

    public void btnSearchClick(View view) {
        EditText etSearch = findViewById(R.id.etSearch);
        String query = etSearch.getText().toString();
        etSearch.clearFocus();
        hideKeyboard(this);

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

                    JSONObject videoInformationObject = new JSONObject(result.toString());
                    JSONObject videoStatisticsObject = getStatisticsObject(videoInformationObject);

                    processJSONObject(videoInformationObject, videoStatisticsObject);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private JSONObject getStatisticsObject(JSONObject object) {
        JSONArray results;
        JSONObject statisticsObject = null;
        StringBuilder allVideoKeys = new StringBuilder();

        try {
            //Get all videos from object
            results = object.getJSONArray("items");
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }

        for (int i = 0; i < results.length(); i++) {
            JSONObject currentJSONObject;
            final String videoKey;
            try {
                //Make one long string from videoIds for statistics request
                currentJSONObject = results.getJSONObject(i);
                videoKey = currentJSONObject.getJSONObject("id").
                        getString("videoId");
                allVideoKeys.append(videoKey).append(",");
            } catch (JSONException e) {
                e.printStackTrace();
                return null;
            }
        }

        //Get statistics
        try {
            HttpURLConnection connection;
            URL url = new URL("https://www.googleapis.com/youtube/v3/videos?part=statistics&id=" + allVideoKeys.toString() + "&key=" + API_KEY);
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
                statisticsObject = new JSONObject(result.toString());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return statisticsObject;
    }

    private void hideKeyboard(Activity activity) {
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        //Find the currently focused view, so we can grab the correct window token from it.
        View view = activity.getCurrentFocus();
        //If no view currently has focus, create a new one, just so we can grab a window token from it
        if (view == null) {
            view = new View(activity);
        }
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private void processJSONObject(JSONObject videoInformationObject, JSONObject videoStatisticsObject) {

        JSONArray results;
        HashMap<String, String> viewsMap = new HashMap<>();

        try {
            results = videoInformationObject.getJSONArray("items");

            //Load all views from object to map
            if(videoStatisticsObject != null) {
                for (int i = 0; i < videoStatisticsObject.getJSONArray("items").length(); i++) {
                    String id = videoStatisticsObject.getJSONArray("items").getJSONObject(i).getString("id");
                    String views = videoStatisticsObject.getJSONArray("items").getJSONObject(i).getJSONObject("statistics").getString("viewCount");
                    viewsMap.put(id, views);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }

        LinearLayout linearLayoutItems = findViewById(R.id.linearLayoutItems);
        runOnUiThread(linearLayoutItems::removeAllViews);

        //Load all item to linearLayout
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

            //Load video
            convertView.setOnClickListener(v -> youTubePlayer.loadVideo(videoKey, 0));

            //Set titles and views
            try {
                TextView tvTitle = convertView.findViewById(R.id.tvTitle);
                tvTitle.setText(currentJSONObject.getJSONObject("snippet").
                        getString("title"));
                TextView tvChannelTitle = convertView.findViewById(R.id.tvChannelTitle);
                tvChannelTitle.setText(currentJSONObject.getJSONObject("snippet").
                        getString("channelTitle"));
                TextView tvViews = convertView.findViewById(R.id.tvViews);

                if (viewsMap.containsKey(videoKey) && viewsMap.get(videoKey) != null) {
                    tvViews.setText(viewsMap.get(videoKey));
                }

                //Load video image
                final ImageView imageViewVideo = convertView.findViewById(R.id.imageViewVideo);
                new Thread(() -> {
                    try {
                        //Load image from URL
                        Bitmap bitmap = BitmapFactory.decodeStream(
                                (InputStream) new URL(currentJSONObject.getJSONObject("snippet").getJSONObject("thumbnails").getJSONObject("default").
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

            //Add view
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
                    runOnUiThread(() -> {
                        try {
                            tvViews.setText(String.format("%s wyświetlenia", object.getJSONArray("items").getJSONObject(0).
                                    getJSONObject("statistics").getString("viewCount")));
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    });

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
}
