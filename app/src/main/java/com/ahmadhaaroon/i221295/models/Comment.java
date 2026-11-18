package com.ahmadhaaroon.i221295.models;

import com.google.firebase.database.IgnoreExtraProperties;

@IgnoreExtraProperties
public class Comment {
    public boolean pending;
    public String syncError;
    public String id;
    public String postId;
    public String uid;
    public String text;
    public String imageUrl;
    /** Id of the top-level comment this is a reply to, or null for a top-level comment. */
    public String parentId;
    public String replyToUid;
    public long timestamp;

    public Comment() {
    }
}
