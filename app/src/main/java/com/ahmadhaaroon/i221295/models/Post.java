package com.ahmadhaaroon.i221295.models;


import java.util.ArrayList;
import java.util.List;

public class Post {
    public boolean pending;
    public String syncError;
    public String id;
    public String uid;
    public String text;
    public List<String> images = new ArrayList<>();
    public String bgColor;
    public long timestamp;
    public int shareCount;

    public Post() {
    }

    public int imageCount() {
        return images == null ? 0 : images.size();
    }
}
