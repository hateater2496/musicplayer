package com.smartshuffle.musicplayer;

public class Song
{
    private long id;
    private String title;
    private String artist;

    public Song(long aSongId, String aSongTitle, String aSongArtist)
    {
        this.id = aSongId;
        this.title = aSongTitle;
        this.artist = aSongArtist;
    }

    public long getId()
    {
        return this.id;
    }

    public String getTitle()
    {
        return this.title;
    }

    public String getArtist()
    {
        return this.artist;
    }

    @Override
    public String toString()
    {
        String str = getArtist() + "\n   " + getTitle();
        return str;
    }
}
