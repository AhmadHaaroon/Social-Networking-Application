package com.ahmadhaaroon.i221295.models;

import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;

import java.util.ArrayList;
import java.util.List;

@IgnoreExtraProperties
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

    @Exclude
    public int imageCount() {
        return images == null ? 0 : images.size();
    }
}
