package org.thoughtcrime.securesms.contactshare.model;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;
import org.thoughtcrime.securesms.util.JsonUtils;

public class Email implements Selectable, Parcelable, Json {

  private final String email;
  private final Type   type;
  private final String label;

  private boolean selected;

  public Email(@NonNull String email, @NonNull Type type, @Nullable String label) {
    this.email    = email;
    this.type     = type;
    this.label    = label;
    this.selected = true;
  }

  private Email(Parcel in) {
    this(in.readString(), Type.valueOf(in.readString()), in.readString());
  }

  public String getEmail() {
    return email;
  }

  public Type getType() {
    return type;
  }

  public String getLabel() {
    return label;
  }

  @Override
  public void setSelected(boolean selected) {
    this.selected = selected;
  }

  @Override
  public boolean isSelected() {
    return selected;
  }

  @Override
  public JSONObject toJson() throws JSONException {
    JSONObject object = new JSONObject();
    object.put("email", email);
    object.put("type", type.name());
    object.put("label", label);
    return object;
  }

  public static Email fromJson(@NonNull JSONObject original) throws JSONException {
    JsonUtils.SaneJSONObject object = new JsonUtils.SaneJSONObject(original);
    return new Email(object.getString("email"),
                     Type.valueOf(object.getString("type")),
                     object.getString("label"));
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(email);
    dest.writeString(type.name());
    dest.writeString(label);
  }

  public static final Creator<Email> CREATOR = new Creator<Email>() {
    @Override
    public Email createFromParcel(Parcel in) {
      return new Email(in);
    }

    @Override
    public Email[] newArray(int size) {
      return new Email[size];
    }
  };

  public enum Type {
    HOME, MOBILE, WORK, CUSTOM
  }
}
