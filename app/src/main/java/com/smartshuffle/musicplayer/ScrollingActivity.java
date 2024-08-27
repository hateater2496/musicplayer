package com.smartshuffle.musicplayer;

import android.Manifest;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import com.google.android.material.appbar.CollapsingToolbarLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;

import android.os.IBinder;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ExpandableListView;
import android.widget.ListView;
import android.widget.MediaController;
import android.widget.TextView;

import com.smartshuffle.musicplayer.databinding.ActivityScrollingBinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;


public class ScrollingActivity extends AppCompatActivity implements MediaController.MediaPlayerControl
{

    private ActivityScrollingBinding binding;
    private ArrayList<Song> songList;
    private ListView songView;
    private TextView artistView;
    private TextView titleView;
    private MusicService musicSrv;
    private Intent playIntent;
    private boolean musicBound=false;
    private MusicController controller;
    private boolean paused = false;
    private boolean playbackPaused = false;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        binding = ActivityScrollingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        Toolbar toolbar = binding.toolbar;
        setSupportActionBar(toolbar);
        CollapsingToolbarLayout toolBarLayout = binding.toolbarLayout;
        toolBarLayout.setTitle(getTitle());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        {
            boolean restart = false;
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED)
            {

                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        1);
                restart = true;
            }
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED)
            {
                requestPermissions(
                        new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 1);
                restart = true;
            }
            if (restart)
            {
                return;
            }
        }

        setController();

        // Create the song ListView and TextView for the UI ListView and TextView
        this.songView = (ListView)findViewById(R.id.song_list);
        this.artistView = (TextView)findViewById(R.id.song_artist);
        this.titleView = (TextView)findViewById(R.id.song_title);
        // Create the song ArrayList before getting songs on the device to populate the list
        this.songList = new ArrayList<Song>();
        updateSongList();
    }

    // connect to the service
    private ServiceConnection musicConnection = new ServiceConnection()
    {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service)
        {
            MusicService.MusicBinder binder = (MusicService.MusicBinder)service;
            // get service
            musicSrv = binder.getService();
            // pass list
            if (musicSrv != null)
            {
                musicSrv.setList(songList);
                musicBound = true;
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name)
        {
            musicBound = false;
        }
    };

    public void songPicked(View view)
    {
        if (this.musicSrv != null)
        {
            this.musicSrv.setSong(Integer.parseInt(view.getTag().toString()));
            this.musicSrv.playSong();
            artistView.setText(songList.get(this.musicSrv.getSongPosn()).getArtist());
            titleView.setText(songList.get(this.musicSrv.getSongPosn()).getTitle());
            if(this.playbackPaused)
            {
                setController();
                this.playbackPaused=false;
            }
            controller.show(0);
        }
        else
        {
            Log.e("NULLPTR, musicSrv does not exist", "");
        }
    }

    @Override
    protected void onStart()
    {
        super.onStart();
        if (this.playIntent == null)
        {
            this.playIntent = new Intent(this, MusicService.class);
            bindService(this.playIntent, this.musicConnection, Context.BIND_AUTO_CREATE);
            startService(this.playIntent);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_scrolling, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        // menu item selected
        switch (item.getItemId())
        {
            case R.id.action_shuffle:
                if (this.musicSrv != null)
                {
                    musicSrv.setShuffle();
                    playNext();
                }
                break;
            case R.id.action_end:
                onDestroy();
                System.exit(0);
                break;

        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy()
    {
        stopService(this.playIntent);
        this.musicSrv=null;
        super.onDestroy();
    }

    private void addToSongList(Cursor musicCursor)
    {
        // get columns
        int sourceColumn = musicCursor.getColumnIndex
                (MediaStore.Audio.Media.DATA);
        Log.i("SongPath","Source: " + musicCursor.getString(
                sourceColumn));
        int titleColumn = musicCursor.getColumnIndex
                (android.provider.MediaStore.Audio.Media.TITLE);
        int idColumn = musicCursor.getColumnIndex
                (android.provider.MediaStore.Audio.Media._ID);
        int artistColumn = musicCursor.getColumnIndex
                (android.provider.MediaStore.Audio.Media.ARTIST);
        // add songs to list
        long thisId = musicCursor.getLong(idColumn);
        String thisTitle = musicCursor.getString(titleColumn);
        String thisArtist = musicCursor.getString(artistColumn);
        Log.i("SongInfo", "Artist: " + thisArtist +
                "\n   Title: " + thisTitle);
        this.songList.add(new Song(thisId, thisTitle, thisArtist));
    }

    private void addMusicFiles(Uri musicUri)
    {
        // retrieve song info
        if (musicUri == null)
        {
            musicUri = MediaStore.Audio.Media.getContentUri("external");
            Log.i("EXTERNAL_CONTENT_URI", "-> musicUri=" + musicUri);
        }
        Log.i("StorageType", "-> musicUri=" + musicUri);
        ContentResolver musicResolver = getContentResolver();
        Cursor musicCursor = musicResolver.query(musicUri, null, null,
                null, null);

        Log.i("Count", "Number of songs found: " + musicCursor.getCount());

        // Iterate over the results after first validating data
        if (musicCursor!=null && musicCursor.moveToFirst())
        {
            addToSongList(musicCursor);
        }
        int count = 1;
        while (musicCursor.moveToNext())
        {
            Log.i("Progress", "Progressing through cursor: " + count);
            addToSongList(musicCursor);
            ++count;
        }

        Collections.sort(this.songList, new Comparator<Song>()
        {
            public int compare(Song a, Song b)
            {
                return a.getTitle().compareTo(b.getTitle());
            }
        });
        SongAdapter songAdt = new SongAdapter(this, this.songList);
        this.songView.setAdapter(songAdt);
        Log.i("UISongs", "Songs added to UI list: " + songView.getCount());
    }

    public void updateSongList()
    {
        addMusicFiles(null);
    }

    @Override
    public void start()
    {
        if (this.musicSrv != null)
        {
            musicSrv.go();
        }
        else
        {
            Log.e("NULLPTR, musicSrv does not exist", "");
        }
    }

    @Override
    public void pause()
    {
        if (this.musicSrv != null)
        {
            playbackPaused = true;
            musicSrv.pausePlayer();
        }
        else
        {
            Log.e("NULLPTR, musicSrv does not exist", "");
        }
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        this.paused=true;
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        if(this.paused)
        {
            setController();
            this.paused=false;
        }
    }

    @Override
    protected void onStop()
    {
        this.controller.hide();
        super.onStop();
    }

    @Override
    public int getDuration()
    {
        if (this.musicSrv != null && this.musicBound && this.musicSrv.isPng())
        {
            return this.musicSrv.getDur();
        }
        else
        {
            return 0;
        }
    }

    @Override
    public int getCurrentPosition()
    {
        if (this.musicSrv != null && this.musicBound && this.musicSrv.isPng())
        {
            return this.musicSrv.getPosn();
        }
        else
        {
            return 0;
        }
    }

    @Override
    public void seekTo(int pos)
    {
        if (this.musicSrv != null)
        {
            musicSrv.seek(pos);
        }
        else
        {
            Log.e("NULLPTR, musicSrv does not exist", "");
        }
    }

    @Override
    public boolean isPlaying()
    {
        if (this.musicSrv != null && this.musicBound)
        {
            artistView.setText(songList.get(this.musicSrv.getSongPosn()).getArtist());
            titleView.setText(songList.get(this.musicSrv.getSongPosn()).getTitle());
            return musicSrv.isPng();
        }
        return false;
    }

    @Override
    public int getBufferPercentage()
    {
        return 0;
    }

    @Override
    public boolean canPause()
    {
        return true;
    }

    @Override
    public boolean canSeekBackward()
    {
        return true;
    }

    @Override
    public boolean canSeekForward()
    {
        return true;
    }

    @Override
    public int getAudioSessionId()
    {
        return 0;
    }

    //play next
    private void playNext()
    {
        if (this.musicSrv != null)
        {
            this.musicSrv.playNext();
            artistView.setText(songList.get(this.musicSrv.getSongPosn()).getArtist());
            titleView.setText(songList.get(this.musicSrv.getSongPosn()).getTitle());
            if(this.playbackPaused)
            {
                setController();
                this.playbackPaused=false;
            }
            controller.show(0);
        }
        else
        {
            Log.e("NULLPTR, musicSrv does not exist", "");
        }
    }

    //play previous
    private void playPrev()
    {
        if (this.musicSrv != null)
        {
            this.musicSrv.playPrev();
            artistView.setText(songList.get(this.musicSrv.getSongPosn()).getArtist());
            titleView.setText(songList.get(this.musicSrv.getSongPosn()).getTitle());
            if(this.playbackPaused)
            {
                setController();
                this.playbackPaused=false;
            }
            this.controller.show(0);
        }
        else
        {
            Log.e("NULLPTR, musicSrv does not exist", "");
        }
    }

    private void setController(){
        // set the controller up
        this.controller = new MusicController(this);
        this.controller.setPrevNextListeners(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                playNext();
            }
        }, new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                playPrev();
            }
        });
        this.controller.setMediaPlayer(this);
        this.controller.setAnchorView(findViewById(R.id.song_list));
        this.controller.setEnabled(true);
    }
}