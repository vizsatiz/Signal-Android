package org.thoughtcrime.securesms.contactshare.model;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;
import org.thoughtcrime.securesms.util.JsonUtils;

public class PostalAddress implements Selectable, Parcelable, Json {

  private final Type   type;
  private final String label;
  private final String street;
  private final String poBox;
  private final String neighborhood;
  private final String city;
  private final String region;
  private final String postalCode;
  private final String country;

  private boolean selected;

  public PostalAddress(@NonNull  Type   type,
                       @Nullable String label,
                       @Nullable String street,
                       @Nullable String poBox,
                       @Nullable String neighborhood,
                       @Nullable String city,
                       @Nullable String region,
                       @Nullable String postalCode,
                       @Nullable String country)
  {
    this.type         = type;
    this.label        = label;
    this.street       = street;
    this.poBox        = poBox;
    this.neighborhood = neighborhood;
    this.city         = city;
    this.region       = region;
    this.postalCode   = postalCode;
    this.country      = country;
    this.selected     = true;
  }

  private PostalAddress(Parcel in) {
    this(Type.valueOf(in.readString()),
         in.readString(),
         in.readString(),
         in.readString(),
         in.readString(),
         in.readString(),
         in.readString(),
         in.readString(),
         in.readString());
  }

  public Type getType() {
    return type;
  }

  public String getLabel() {
    return label;
  }

  public String getStreet() {
    return street;
  }

  public String getPoBox() {
    return poBox;
  }

  public String getNeighborhood() {
    return neighborhood;
  }

  public String getCity() {
    return city;
  }

  public String getRegion() {
    return region;
  }

  public String getPostalCode() {
    return postalCode;
  }

  public String getCountry() {
    return country;
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
    object.put("type", type.name());
    object.put("label", label);
    object.put("street", street);
    object.put("poBox", poBox);
    object.put("neighborhood", neighborhood);
    object.put("city", city);
    object.put("region", region);
    object.put("postalCode", postalCode);
    object.put("country", country);
    return object;
  }

  public static PostalAddress fromJson(@NonNull JSONObject original) throws JSONException {
    JsonUtils.SaneJSONObject object = new JsonUtils.SaneJSONObject(original);
    return new PostalAddress(Type.valueOf(object.getString("type")),
                             object.getString("label"),
                             object.getString("street"),
                             object.getString("poBox"),
                             object.getString("neighborhood"),
                             object.getString("city"),
                             object.getString("region"),
                             object.getString("postalCode"),
                             object.getString("country"));
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(type.name());
    dest.writeString(label);
    dest.writeString(street);
    dest.writeString(poBox);
    dest.writeString(neighborhood);
    dest.writeString(city);
    dest.writeString(region);
    dest.writeString(postalCode);
    dest.writeString(country);
  }

  public static final Creator<PostalAddress> CREATOR = new Creator<PostalAddress>() {
    @Override
    public PostalAddress createFromParcel(Parcel in) {
      return new PostalAddress(in);
    }

    @Override
    public PostalAddress[] newArray(int size) {
      return new PostalAddress[size];
    }
  };

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();

    if (!TextUtils.isEmpty(street)) {
      builder.append(street).append('\n');
    }

    if (!TextUtils.isEmpty(poBox)) {
      builder.append(poBox).append('\n');
    }

    if (!TextUtils.isEmpty(neighborhood)) {
      builder.append(neighborhood).append('\n');
    }

    if (!TextUtils.isEmpty(city) && !TextUtils.isEmpty(region)) {
      builder.append(city).append(", ").append(region);
    } else if (!TextUtils.isEmpty(city)) {
      builder.append(city).append(' ');
    } else if (!TextUtils.isEmpty(region)) {
      builder.append(region).append(' ');
    }

    if (!TextUtils.isEmpty(postalCode)) {
      builder.append(postalCode);
    }

    if (!TextUtils.isEmpty(country)) {
      builder.append('\n').append(country);
    }

    return builder.toString().trim();
  }

  public enum Type {
    HOME, WORK, CUSTOM
  }
}
