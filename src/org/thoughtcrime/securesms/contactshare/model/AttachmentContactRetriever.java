package org.thoughtcrime.securesms.contactshare.model;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.util.Log;

import org.json.JSONException;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.util.Util;

import java.io.IOException;
import java.io.InputStream;

public class AttachmentContactRetriever implements ContactRetriever {

  private static final String TAG = AttachmentContactRetriever.class.getSimpleName();

  private final Context    context;
  private final Attachment contact;
  private final Attachment avatar;

  public AttachmentContactRetriever(@NonNull  Context    context,
                                    @NonNull  Attachment contact,
                                    @Nullable Attachment avatar)
  {
    this.context  = context.getApplicationContext();
    this.contact  = contact;
    this.avatar   = avatar;
  }

  @Override
  @WorkerThread
  public @Nullable Contact getContact() {
    if (contact.getDataUri() == null) {
      Log.w(TAG, "Provided contact attachment has no URI");
      return null;
    }

    try (InputStream jsonStream = PartAuthority.getAttachmentStream(context, contact.getDataUri())) {
      String json = Util.readFullyAsString(jsonStream);
      return Contact.fromJson(json, avatar);
    } catch (IOException e) {
      Log.w(TAG, "Failed to read the JSON blob for a contact off of disk.", e);
    } catch (JSONException e) {
      Log.w(TAG, "Failed to deserialize the JSON blob for a contact.", e);
    }
    return null;
  }
}
