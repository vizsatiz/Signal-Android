package org.thoughtcrime.securesms.contactshare.model;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;
import org.thoughtcrime.securesms.util.JsonUtils;
import org.w3c.dom.Text;

public class Name implements Parcelable, Json {

  private final String displayName;
  private final String givenName;
  private final String familyName;
  private final String prefix;
  private final String suffix;
  private final String middleName;

  public Name(@Nullable String displayName,
              @Nullable String givenName,
              @Nullable String familyName,
              @Nullable String prefix,
              @Nullable String suffix,
              @Nullable String middleName)
  {
    this.displayName = displayName;
    this.givenName  = givenName;
    this.familyName = familyName;
    this.prefix     = prefix;
    this.suffix     = suffix;
    this.middleName = middleName;
  }

  Name(Parcel in) {
    this(in.readString(), in.readString(), in.readString(), in.readString(), in.readString(), in.readString());
  }

  public @Nullable String getDisplayName() {
    return displayName;
  }

  public @Nullable String getGivenName() {
    return givenName;
  }

  public @Nullable String getFamilyName() {
    return familyName;
  }

  public @Nullable String getPrefix() {
    return prefix;
  }

  public @Nullable String getSuffix() {
    return suffix;
  }

  public @Nullable String getMiddleName() {
    return middleName;
  }

  public boolean isEmpty() {
    return TextUtils.isEmpty(displayName) &&
           TextUtils.isEmpty(givenName)   &&
           TextUtils.isEmpty(familyName)  &&
           TextUtils.isEmpty(prefix)      &&
           TextUtils.isEmpty(suffix)      &&
           TextUtils.isEmpty(middleName);
  }

  @Override
  public JSONObject toJson() throws JSONException {
    JSONObject object = new JSONObject();
    object.put("displayName", displayName);
    object.put("givenName", givenName);
    object.put("familyName", familyName);
    object.put("prefix", prefix);
    object.put("suffix", suffix);
    object.put("middleName", middleName);
    return object;
  }

  public static Name fromJson(@NonNull JSONObject original) throws JSONException {
    JsonUtils.SaneJSONObject object = new JsonUtils.SaneJSONObject(original);
    return new Name(object.getString("displayName"),
                    object.getString("givenName"),
                    object.getString("familyName"),
                    object.getString("prefix"),
                    object.getString("suffix"),
                    object.getString("middleName"));
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(displayName);
    dest.writeString(givenName);
    dest.writeString(familyName);
    dest.writeString(prefix);
    dest.writeString(suffix);
    dest.writeString(middleName);
  }

  public static final Creator<Name> CREATOR = new Creator<Name>() {
    @Override
    public Name createFromParcel(Parcel in) {
      return new Name(in);
    }

    @Override
    public Name[] newArray(int size) {
      return new Name[size];
    }
  };
}
