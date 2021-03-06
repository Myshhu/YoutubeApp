package com.myshhu.youtubebackgroundplayer;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

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
import java.util.ArrayList;
import java.util.HashMap;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    //private String VIDEO_CODE = "GdNwaa1m1Yo";
    private String[] API_KEYS_ARRAY = {com.myshhu.youtubebackgroundplayer.API_KEY.KEY,
            com.myshhu.youtubebackgroundplayer.API_KEY.KEY1};
    private int currentAPI_KEY = 0;
    private String API_KEY = API_KEYS_ARRAY[0];    private YouTubePlayer youTubePlayer;
    private ArrayAdapter<String> adapter;
    private ArrayList<String> searchHistory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        StaticActivity.activity = this;

        startService(new Intent(MainActivity.this, FloatingViewService.class));

        searchHistory = getArrayPrefs();
        setACTextView();

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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        StaticActivity.service.stopForeground(true);
    }

    public void btnClearTVClick(View view) {
        EditText etSearch = findViewById(R.id.etSearch);
        etSearch.setText("");
    }

    private void setACTextView() {
        CustomAutoCompleteTextView customACTextView = findViewById(R.id.etSearch);
        /*searchHistory = new ArrayList<>();
        searchHistory.add("Poland");
        searchHistory.add("France");
        searchHistory.add("Italy");
        searchHistory.add("Germany");*/
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, searchHistory);
        customACTextView.setAdapter(adapter);
        customACTextView.setThreshold(0);

        customACTextView.setOnClickListener(v -> customACTextView.showDropDown());
        //Make dropdown show on first click
        customACTextView.setOnFocusChangeListener((v, hasFocus) -> customACTextView.performClick());
        //Perform search at item click
        customACTextView.setOnItemClickListener((parent, view, position, id) -> {
            hideKeyboard(this);
            performSearch(adapter.getItem(position));
        });
    }

    public void setArrayPrefs(ArrayList<String> array) {
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt("size", array.size());
        for (int i = 0; i < array.size(); i++)
            editor.putString(Integer.toString(i), array.get(i));
        editor.apply();
    }

    public ArrayList<String> getArrayPrefs() {
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        int size = prefs.getInt("size", 0);
        ArrayList<String> array = new ArrayList<>();
        for (int i = 0; i < size; i++)
            array.add(prefs.getString(Integer.toString(i), null));
        return array;
    }

    public void btnSearchClick(View view) {
        EditText etSearch = findViewById(R.id.etSearch);
        String query = etSearch.getText().toString();
        etSearch.clearFocus();
        hideKeyboard(this);

        System.out.println(adapter.getCount());
        if(!query.equals("")) {
            searchHistory.add(0, query);
            //This works instead of notify
            adapter.clear();
            adapter.addAll(searchHistory);
            setArrayPrefs(searchHistory);
        }
        performSearch(query);
    }

    protected void performSearch(String query) {
        new Thread(() -> {
            JSONObject videoInformationObject = getVideoInformationObject(query);
            JSONObject videoStatisticsObject = getStatisticsObject(videoInformationObject);

            //Clear current searching results
            LinearLayout linearLayoutItems = findViewById(R.id.linearLayoutItems);
            runOnUiThread(linearLayoutItems::removeAllViews);

            processJSONObject(videoInformationObject, videoStatisticsObject, linearLayoutItems);
        }).start();
    }

    private JSONObject getVideoInformationObject(String query) {
        JSONObject videoInformationObject = null;
        while(true) {
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

                    videoInformationObject = new JSONObject(result.toString());
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
        return videoInformationObject;
    }

    private JSONObject getStatisticsObject(JSONObject object) {
        JSONArray results;
        JSONObject statisticsObject = null;
        StringBuilder allVideoKeys = new StringBuilder();

        try {
            //Get all videos from object
            results = object.getJSONArray("items");
        } catch (NullPointerException e) {
            e.printStackTrace();
            return null;
        } catch (Exception e) {
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
        while(true) {
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

    private void processJSONObject(JSONObject videoInformationObject, JSONObject videoStatisticsObject, LinearLayout linearLayoutItems) {
        JSONArray results;
        HashMap<String, String> viewsMap = new HashMap<>();

        try {
            results = videoInformationObject.getJSONArray("items");
            //Load all views from object to map
            if (videoStatisticsObject != null) {
                for (int i = 0; i < videoStatisticsObject.getJSONArray("items").length(); i++) {
                    String id = videoStatisticsObject.getJSONArray("items").getJSONObject(i).getString("id");
                    String views = videoStatisticsObject.getJSONArray("items").getJSONObject(i).getJSONObject("statistics").getString("viewCount");
                    viewsMap.put(id, views);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        //Load all items to linearLayout
        for (int i = 0; i < results.length(); i++) {
            JSONObject currentJSONObject;
            try {
                currentJSONObject = results.getJSONObject(i);
            } catch (JSONException e) {
                e.printStackTrace();
                return;
            }
            View convertView = createScrollviewItem(currentJSONObject, viewsMap);
            runOnUiThread(() -> linearLayoutItems.addView(convertView));
        }
    }

    private View createScrollviewItem(JSONObject videoObject, HashMap<String, String> viewsMap) {
        LayoutInflater layoutInflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View convertView = layoutInflater.inflate(R.layout.scrollview_item, findViewById(R.id.linearLayoutItems), false);
        String videoKey;
        String videoTitle;
        try {
            videoKey = videoObject.getJSONObject("id").
                    getString("videoId");
            videoTitle = videoObject.getJSONObject("snippet").
                    getString("title");
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }

        //Load video
        convertView.setOnClickListener(v -> {
            youTubePlayer.loadVideo(videoKey, 0);
            TextView tvCurrentVideoTitle = findViewById(R.id.tvCurrentVideoTitle);
            TextView tvCurrentVideoViews = findViewById(R.id.tvCurrentVideoViews);
            tvCurrentVideoTitle.setText(videoTitle);
            if (viewsMap.containsKey(videoKey) && viewsMap.get(videoKey) != null) {
                tvCurrentVideoViews.setText(String.format("%s %s", viewsMap.get(videoKey), getString(R.string.views)));
            }
            System.out.println("my height is " + convertView.getHeight());
        });
        //Set titles and views
        try {
            TextView tvTitle = convertView.findViewById(R.id.tvTitle);
            tvTitle.setText(videoObject.getJSONObject("snippet").
                    getString("title"));
            TextView tvChannelTitle = convertView.findViewById(R.id.tvChannelTitle);
            tvChannelTitle.setText(videoObject.getJSONObject("snippet").
                    getString("channelTitle"));
            TextView tvViews = convertView.findViewById(R.id.tvViews);

            if (viewsMap.containsKey(videoKey) && viewsMap.get(videoKey) != null) {
                tvViews.setText(String.format("%s %s", viewsMap.get(videoKey), getString(R.string.views)));
            }

            //Load video image
            final ImageView imageViewVideo = convertView.findViewById(R.id.imageViewVideo);
            new Thread(() -> {
                try {
                    //Load image from URL
                    Bitmap bitmap = BitmapFactory.decodeStream(
                            (InputStream) new URL(videoObject.getJSONObject("snippet").getJSONObject("thumbnails").getJSONObject("medium").
                                    getString("url")).getContent());
                    //Set image to imageView
                    runOnUiThread(() -> imageViewVideo.setImageBitmap(bitmap));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();

        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
        return convertView;
    }
}
