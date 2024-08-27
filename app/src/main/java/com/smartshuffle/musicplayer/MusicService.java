package com.smartshuffle.musicplayer;


import static android.provider.Settings.Secure.ANDROID_ID;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class MusicService extends Service implements
        MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener,
        MediaPlayer.OnCompletionListener {
    // media player
    private MediaPlayer player;
    // song list
    private ArrayList<Song> songs;
    // current position
    private int songPosn;
    private final IBinder musicBind = new MusicBinder();
    // Prepared variables
    private Song song = null;
    private static final int NOTIFY_ID = 1;
    // Shuffle handling
    private boolean shuffle = false;
    private Random rand;
    private HashMap<String, Integer> artistCountMap;
    // Bluetooth
    private BluetoothServerSocket mmServerSocket = null;
    private BluetoothSocket mmSocket = null;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return this.musicBind;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        this.player.stop();
        this.player.release();
        return false;
    }

    @Override
    public void onCompletion(MediaPlayer mediaPlayer) {
        if (this.player.getCurrentPosition() > 0) {
            mediaPlayer.reset();
            playNext();
        }
    }

    @Override
    public boolean onError(MediaPlayer mediaPlayer, int i, int i1) {
        mediaPlayer.reset();
        return false;
    }

    private void addNotification() {
        Intent notIntent = new Intent(this, ScrollingActivity.class);
        notIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendInt = PendingIntent.getActivity(this, 0,
                notIntent, PendingIntent.FLAG_IMMUTABLE);

        NotificationChannel chan = new NotificationChannel(
                "MyChannelId",
                "My Foreground Service",
                NotificationManager.IMPORTANCE_LOW);
        chan.setLightColor(Color.BLUE);
        chan.setLockscreenVisibility(Notification.VISIBILITY_SECRET);

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        assert manager != null;
        manager.createNotificationChannel(chan);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                this, "MyChannelId");
        Notification not = builder.setOngoing(true)
                .setContentTitle("Playing")
                .setContentText(this.song.toString())
                .setPriority(NotificationManager.IMPORTANCE_LOW)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setChannelId("MyChannelId")
                .setContentIntent(pendInt)
                .setSmallIcon(R.drawable.play)
                .setTicker(this.song.toString())
                .setOngoing(true)
                .build();

        CharSequence songTitle;

        Log.i("NotificationExistence", (not != null ? "true" : "false"));

        startForeground(NOTIFY_ID, not);
    }

    @Override
    public void onPrepared(MediaPlayer mediaPlayer)
    {
        // start playback
        mediaPlayer.start();
        addNotification();
    }

    @Override
    public void onDestroy()
    {
        stopForeground(true);
        try
        {
            mmSocket.close();
        }
        catch (IOException e)
        {
            Log.e("Socket", "Socket's close() method failed", e);
        }
    }

    public void onCreate()
    {
        this.song = new Song(0,"", "");
        AudioManager manager = (AudioManager)getSystemService(AUDIO_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.requestAudioFocus(new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                            new AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_GAME)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                    .build()
                    )
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener(new AudioManager.OnAudioFocusChangeListener() {
                        @Override
                        public void onAudioFocusChange(int focusChange) {
                            //Handle Focus Change
                        }
                    }).build()
            );
        } else {

            manager.requestAudioFocus(focusChange -> {

                        //Handle Focus Change for earliest version of oreo
                    },
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN);
        }
        // create the service
        super.onCreate();
        // initialize position
        this.songPosn=0;
        // Instantiate random variable for shuffle
        this.rand = new Random();
        // create player
        this.player = new MediaPlayer();
        initMusicPlayer();
    }

    public void setShuffle()
    {
        this.shuffle = !this.shuffle;
        if (this.shuffle)
        {
            artistCountMap = new HashMap<>();
        }
    }

    private void addBluetooth() {
        BluetoothManager bluetoothManager = getSystemService(BluetoothManager.class);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null) {
            // Device doesn't support Bluetooth
            Log.e("Bluetooth", "Device does not support Bluetooth");
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED)
        {
            Log.w("Bluetooth", "Bluetooth connection failed.");
            return;
        }

        // Use a temporary object that is later assigned to mmServerSocket
        // because mmServerSocket is final.
        BluetoothServerSocket tmp = null;
        try
        {
            // MY_UUID is the app's UUID string, also used by the client code.
            tmp = bluetoothAdapter.listenUsingRfcommWithServiceRecord(
                    getApplication().getPackageName(),
                    UUID.fromString("a70aec2c-19e4-4804-8e3c-557de4e3f558"));
        }
        catch (IOException e)
        {
            Log.e("ServerSocket", "Server socket's listen() method failed", e);
        }
        Log.i("ServerSocket", "Server socket is " + (tmp != null ? "not null" : "null"));
        mmServerSocket = tmp;
    }

    private void manageMyConnectedSocket(BluetoothSocket socket)
    {
        this.mmSocket = socket;
    }

    private void connectBluetooth()
    {
        Runnable bluetoothAcceptThread = () ->
        {
            BluetoothSocket socket = null;
            // Keep listening until exception occurs or a socket is returned.
            while (true)
            {
                try
                {
                    socket = mmServerSocket.accept();
                }
                catch (IOException e)
                {
                    Log.e("ServerSocket", "Server socket's accept() method failed", e);
                    break;
                }

                if (socket != null) {
                    // A connection was accepted. Perform work associated with
                    // the connection in a separate thread.
                    manageMyConnectedSocket(socket);
                    try
                    {
                        mmServerSocket.close();
                    }
                    catch (IOException e)
                    {
                        Log.e("Socket", "Socket's close() method failed", e);
                    }
                    break;
                }
            }
        };
        Thread run = new Thread(bluetoothAcceptThread);
        run.start();
    }

    public void initMusicPlayer()
    {
        // set player properties
        this.player.setWakeMode(getApplicationContext(),
                PowerManager.PARTIAL_WAKE_LOCK);
        this.player.setAudioAttributes(new AudioAttributes
                .Builder().setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build());
        this.player.setOnPreparedListener(this);
        this.player.setOnCompletionListener(this);
        this.player.setOnErrorListener(this);
        addBluetooth();
        connectBluetooth();
    }

    public void setList(ArrayList<Song> theSongs)
    {
        this.songs=theSongs;
    }

    public class MusicBinder extends Binder
    {
        MusicService getService()
        {
            return MusicService.this;
        }
    }

    public void playSong()
    {
        // play a song
        this.player.reset();
        // get song
        Song playSong = this.songs.get(this.songPosn);
        this.song = playSong;
        // get id
        long currSong = playSong.getId();
        // set uri
        Uri trackUri = ContentUris.withAppendedId(
                android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                currSong);
        try
        {
            player.setDataSource(getApplicationContext(), trackUri);
            if (mmSocket != null)
            {
                mmSocket.getOutputStream().write(
                        player.getTrackInfo().toString().getBytes(StandardCharsets.UTF_8));
            }
        }
        catch(Exception e)
        {
            Log.e("MUSIC SERVICE", "Error setting data source", e);
        }
        this.player.prepareAsync();
    }

    public void setSong(int songIndex)
    {
        this.songPosn=songIndex;
    }

    public int getSongPosn() { return this.songPosn; }

    public int getPosn()
    {
        return this.player.getCurrentPosition();
    }

    public int getDur()
    {
        return this.player.getDuration();
    }

    public boolean isPng()
    {
        return this.player.isPlaying();
    }

    public void pausePlayer()
    {
        this.player.pause();
    }

    public void seek(int posn)
    {
        this.player.seekTo(posn);
    }

    public void go()
    {
        this.player.start();
    }

    public void playPrev(){
        this.songPosn--;
        if(this.songPosn < 0)
        {
            this.songPosn=this.songs.size()-1;
        }
        playSong();
    }

    // skip to next
    public void playNext(){
        if(this.shuffle)
        {
            int newSong = this.songPosn;
            int count = 0;
            Song playSong = null;
            while(newSong == this.songPosn && count < this.songs.size())
            {
                newSong = this.rand.nextInt(this.songs.size());
                playSong = this.songs.get(newSong);
                ++count;
                if (artistCountMap.get(playSong.getArtist()) != null && count < this.songs.size())
                {
                    newSong = this.songPosn;
                    Log.i("Skipping", playSong.toString());
                }
                else
                {
                    artistCountMap.put(playSong.getArtist(), 1);
                }
                if (count == this.songs.size())
                {
                    if (newSong == this.songPosn)
                    {
                        --count;
                    }
                    artistCountMap.clear();
                    Log.i("Clear", "Cleared artist to count map.");
                }
            }
            if (playSong != null)
            {
                Log.i("Playing", playSong.toString());
            }
            this.songPosn = newSong;
        }
        else
        {
            this.songPosn++;
            if(this.songPosn >= this.songs.size()) this.songPosn=0;
        }
        playSong();
    }
}
