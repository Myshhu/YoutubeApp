package com.example.youtubelayout;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener;

import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.net.URL;

public class MainActivity extends AppCompatActivity {

    private String VIDEO_CODE = "GdNwaa1m1Yo";
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
    }
}
