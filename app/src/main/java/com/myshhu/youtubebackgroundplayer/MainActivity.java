package com.myshhu.youtubebackgroundplayer;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.utils.YouTubePlayerTracker;

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
import java.util.Objects;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private Intent floatingPlayerServiceIntent;
    private String[] API_KEYS_ARRAY = {com.myshhu.youtubebackgroundplayer.API_KEY.KEY,
            com.myshhu.youtubebackgroundplayer.API_KEY.KEY1};
    private int currentAPI_KEY = 0;
    private String API_KEY = API_KEYS_ARRAY[0];
    private ArrayAdapter<String> autoCompleteTextViewAdapter;
    private ArrayList<String> searchHistoryList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        StaticReferences.activity = this;

        searchHistoryList = loadSearchHistory();

        initAutoCompleteTextView();
        checkOverlayPermission();

        createAndRegisterBroadcastReceiver();
    }

    public ArrayList<String> loadSearchHistory() {
        ArrayList<String> historyElements = new ArrayList<>();
        SharedPreferences sharedPreferences = getPreferences(MODE_PRIVATE);
        int historyElementsAmount = sharedPreferences.getInt("size", 0);
        for (int i = 0; i < historyElementsAmount; i++)
            historyElements.add(sharedPreferences.getString(Integer.toString(i), null));
        return historyElements;
    }

    private void initAutoCompleteTextView() {
        CustomAutoCompleteTextView autoCompleteTextView = findViewById(R.id.autoCompleteTextViewSearchBar);
        setAutoCompleteTextViewAdapter(autoCompleteTextView);
        setAutoCompleteTextViewListeners(autoCompleteTextView);
    }

    private void setAutoCompleteTextViewAdapter(CustomAutoCompleteTextView autoCompleteTextView) {
        autoCompleteTextViewAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, searchHistoryList);
        autoCompleteTextView.setAdapter(autoCompleteTextViewAdapter);
    }

    private void setAutoCompleteTextViewListeners(CustomAutoCompleteTextView autoCompleteTextView) {
        autoCompleteTextView.setOnClickListener(v -> autoCompleteTextView.showDropDown());
        //Make dropdown show on first TextView click when unfocused
        autoCompleteTextView.setOnFocusChangeListener((v, hasFocus) -> autoCompleteTextView.performClick());
        //Perform video search at history item click
        autoCompleteTextView.setOnItemClickListener((parent, view, position, id) -> {
            hideSoftKeyboard(this);
            performVideoSearch(autoCompleteTextViewAdapter.getItem(position));
        });
    }

    private void hideSoftKeyboard(Activity activity) {
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        //Find the currently focused view, so we can grab the correct window token from it.
        View view = activity.getCurrentFocus();
        //If no view currently has focus, create a new one, just so we can grab a window token from it
        if (view == null) {
            view = new View(activity);
        }
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private void checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            //If the draw over permission is not available open the settings screen
            //to grant the permission.
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, 0);
        }
    }

    public void btnClearSearchBarClick(View view) {
        AutoCompleteTextView autoCompleteTextViewSearchBar = findViewById(R.id.autoCompleteTextViewSearchBar);
        autoCompleteTextViewSearchBar.setText("");
    }

    public void saveSearchHistory(ArrayList<String> searchHistory) {
        SharedPreferences sharedPreferences = getPreferences(MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt("size", searchHistory.size());
        for (int i = 0; i < searchHistory.size(); i++)
            editor.putString(Integer.toString(i), searchHistory.get(i));
        editor.apply();
    }

    public void btnSearchVideoClick(View view) {
        EditText autoCompleteTextViewSearchBar = findViewById(R.id.autoCompleteTextViewSearchBar);
        autoCompleteTextViewSearchBar.clearFocus();
        hideSoftKeyboard(this);

        String searchQuery = autoCompleteTextViewSearchBar.getText().toString();
        addSearchQueryToHistory(searchQuery);

        performVideoSearch(searchQuery);
    }

    private void addSearchQueryToHistory(String searchQuery) {
        if (!searchQuery.equals("")) {
            searchHistoryList.add(0, searchQuery);
            //Adapter clearing and adding all again works instead of notifyItemsChanged()
            autoCompleteTextViewAdapter.clear();
            autoCompleteTextViewAdapter.addAll(searchHistoryList);
        }
    }

    protected void performVideoSearch(String searchQuery) {
        new Thread(() -> {
            JSONObject videoInformationObject = getVideoInformationObject(searchQuery);
            JSONObject videoStatisticsObject = getStatisticsObject(videoInformationObject);

            clearCurrentSearchResults();

            processJSONObject(videoInformationObject, videoStatisticsObject);
        }).start();
    }

    private JSONObject getVideoInformationObject(String query) {
        JSONObject videoInformationObject = null;
        boolean availableSpareAPI_KEYS = true;
        while (availableSpareAPI_KEYS) {
            try {
                HttpURLConnection connection;
                URL url = new URL("https://www.googleapis.com/youtube/v3/search?part=snippet&q=" +
                        query + "&maxResults=10&type=video&key=" + API_KEY);
                connection = (HttpURLConnection) url.openConnection();
                connection.connect();

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    InputStream connectionInputStream = connection.getInputStream();
                    videoInformationObject = createJSONObjectFromInputStream(connectionInputStream);
                    break;
                } else if (connection.getResponseCode() == HttpURLConnection.HTTP_FORBIDDEN) {
                    if (areAllAPI_KEYSUnavailable()) {
                        Handler handler = new Handler(Looper.getMainLooper());
                        handler.post(() -> Toast.makeText(getApplicationContext(), "API limits exceeded, try again later", Toast.LENGTH_LONG).show());

                        changeCurrentAPI_KEYToFirst();
                        availableSpareAPI_KEYS = false;
                    } else {
                        changeCurrentAPI_KEY();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return videoInformationObject;
    }

    private JSONObject createJSONObjectFromInputStream(InputStream inputStream) throws Exception {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        String line;
        StringBuilder result = new StringBuilder();
        while ((line = bufferedReader.readLine()) != null) {
            result.append(line).append("\n");
        }
        return new JSONObject(result.toString());
    }

    private boolean areAllAPI_KEYSUnavailable() {
        return currentAPI_KEY == API_KEYS_ARRAY.length - 1;
    }

    private void changeCurrentAPI_KEYToFirst() {
        currentAPI_KEY = 0;
        API_KEY = API_KEYS_ARRAY[currentAPI_KEY];
    }

    private void changeCurrentAPI_KEY() {
        API_KEY = API_KEYS_ARRAY[++currentAPI_KEY];
        Log.d("AppInfo", "Changed key to " + currentAPI_KEY);
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
        while (true) {
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

    private void clearCurrentSearchResults() {
        LinearLayout linearLayoutItems = findViewById(R.id.linearLayoutItems);
        runOnUiThread(linearLayoutItems::removeAllViews);
    }

    private void processJSONObject(JSONObject videoInformationObject, JSONObject videoStatisticsObject) {
        JSONArray results;
        HashMap<String, String> viewsMap = new HashMap<>();
        LinearLayout linearLayoutItems = findViewById(R.id.linearLayoutItems);

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
            //youTubePlayer.loadVideo(videoKey, 0);

            if (floatingPlayerServiceIntent == null) {
                launchWidget(videoKey);
            } else {
                Intent intent = new Intent("play_video");
                intent.putExtra("videoId", videoKey);
                sendBroadcast(intent);
                System.out.println("sent intent");
            }

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

    public void btnLaunchWidgetClick(View view) {
        launchWidget("0");
    }

    private void launchWidget(String videoId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {

            //If the draw over permission is not available open the settings screen
            //to grant the permission.
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, 0);
        } else {
            floatingPlayerServiceIntent = new Intent(MainActivity.this, floatingPlayerService.class);
            floatingPlayerServiceIntent.putExtra("videoId", videoId);
            startService(floatingPlayerServiceIntent);
        }
    }

    private void createAndRegisterBroadcastReceiver() {
        BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context arg0, Intent intent) {
                String action = intent.getAction();
                if (Objects.equals(action, "widget_finished")) {
                    floatingPlayerServiceIntent = null;
                }
            }
        };
        registerReceiver(broadcastReceiver, new IntentFilter("widget_finished"));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        saveSearchHistory(searchHistoryList);
        StaticReferences.floatingPlayerService.stopForeground(true);
    }
}
