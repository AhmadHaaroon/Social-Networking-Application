package com.ahmadhaaroon.i221295.util;

import com.ahmadhaaroon.i221295.models.CallInfo;
import org.json.JSONObject;

public final class Calls {
    private Calls() {}

    public static CallInfo get(String id) {
        for (JSONObject r : LocalDb.get().content("calls"))
            if (id.equals(r.optString("callId"))) return ContentJson.read(r, CallInfo.class);
        return null;
    }

    public static void action(
            String id,
            String operation,
            String other,
            boolean video,
            Runnable done,
            Callback<Exception> failed) {
        try {
            Realtime.request(
                    "calls/action.php",
                    new JSONObject()
                            .put("id", id)
                            .put("operation", operation)
                            .put("other", other)
                            .put("video", video),
                    r -> {
                        Profiles.refresh(Realtime.context());
                        if (done != null) done.run();
                    },
                    failed);
        } catch (Exception e) {
            if (failed != null) failed.onResult(e);
        }
    }
}
