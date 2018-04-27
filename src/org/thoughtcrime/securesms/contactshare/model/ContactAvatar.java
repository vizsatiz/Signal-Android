package org.thoughtcrime.securesms.contactshare.model;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.attachments.UriAttachment;
import org.thoughtcrime.securesms.database.AttachmentDatabase;
import org.thoughtcrime.securesms.util.JsonUtils;
import org.thoughtcrime.securesms.util.MediaUtil;

public class ContactAvatar implements Parcelable, Json {

  private final Attachment image;
  private final boolean    isProfile;

  public ContactAvatar(@NonNull Attachment image, boolean isProfile) {
    this.image     = image;
    this.isProfile = isProfile;
  }

  public ContactAvatar(@NonNull Uri imageUri, boolean isProfile) {
    this(buildAttachment(imageUri), isProfile);
  }

  private ContactAvatar(Parcel in) {
    this((Uri) in.readParcelable(Uri.class.getClassLoader()), in.readByte() != 0);
  }

  private static @NonNull Attachment buildAttachment(@NonNull Uri uri) {
    return new UriAttachment(uri, MediaUtil.IMAGE_JPEG, AttachmentDatabase.TRANSFER_PROGRESS_DONE, 0, null, false, false);
  }

  public @NonNull Attachment getImage() {
    return image;
  }

  public boolean isProfile() {
    return isProfile;
  }

  @Override
  public JSONObject toJson() throws JSONException {
    JSONObject object = new JSONObject();
    object.put("isProfile", isProfile);
    return object;
  }

  public static ContactAvatar fromJson(@NonNull JSONObject original, @NonNull Attachment avatar) throws JSONException {
    JsonUtils.SaneJSONObject object = new JsonUtils.SaneJSONObject(original);
    return new ContactAvatar(avatar, object.getBoolean("isProfile"));
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeParcelable(image.getDataUri(), flags);
    dest.writeByte((byte) (isProfile ? 1 : 0));
  }

  public static final Creator<ContactAvatar> CREATOR = new Creator<ContactAvatar>() {
    @Override
    public ContactAvatar createFromParcel(Parcel in) {
      return new ContactAvatar(in);
    }

    @Override
    public ContactAvatar[] newArray(int size) {
      return new ContactAvatar[size];
    }
  };
}
