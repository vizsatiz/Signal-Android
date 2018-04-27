package org.thoughtcrime.securesms.contactshare.model;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;
import org.thoughtcrime.securesms.util.JsonUtils;

public class Phone implements Selectable, Parcelable, Json {

  private final String number;
  private final Type   type;
  private final String label;

  private boolean selected;

  public Phone(@NonNull String number, @NonNull Type type, @Nullable String label) {
    this.number   = number;
    this.type     = type;
    this.label    = label;
    this.selected = true;
  }

  private Phone(Parcel in) {
    this(in.readString(), Type.valueOf(in.readString()), in.readString());
  }

  public @NonNull String getNumber() {
    return number;
  }

  public @NonNull Type getType() {
    return type;
  }

  public @Nullable String getLabel() {
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
  public int describeContents() {
    return 0;
  }

  @Override
  public JSONObject toJson() throws JSONException {
    JSONObject object = new JSONObject();
    object.put("number", number);
    object.put("type", type.name());
    object.put("label", label);
    return object;
  }

  public static Phone fromJson(@NonNull JSONObject original) throws JSONException {
    JsonUtils.SaneJSONObject object = new JsonUtils.SaneJSONObject(original);
    return new Phone(object.getString("number"),
                     Type.valueOf(object.getString("type")),
                     object.getString("label"));
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(number);
    dest.writeString(type.name());
    dest.writeString(label);
  }

  public static final Creator<Phone> CREATOR = new Creator<Phone>() {
    @Override
    public Phone createFromParcel(Parcel in) {
      return new Phone(in);
    }

    @Override
    public Phone[] newArray(int size) {
      return new Phone[size];
    }
  };

  public enum Type {
    HOME, MOBILE, WORK, CUSTOM
  }
}
