package org.thoughtcrime.securesms.contactshare.model;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.database.Address;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Contact implements Parcelable, ContactRetriever {

  private final Name                name;
  private final String              organization;
  private final List<Phone>         phoneNumbers;
  private final List<Email>         emails;
  private final List<PostalAddress> postalAddresses;
  private final ContactAvatar       avatar;

  public Contact(@NonNull  Name                name,
                 @Nullable String              organization,
                 @NonNull  List<Phone>         phoneNumbers,
                 @NonNull  List<Email>         emails,
                 @NonNull  List<PostalAddress> postalAddresses,
                 @Nullable ContactAvatar       avatar)
  {
    this.name            = name;
    this.organization    = organization;
    this.phoneNumbers    = Collections.unmodifiableList(phoneNumbers);
    this.emails          = Collections.unmodifiableList(emails);
    this.postalAddresses = Collections.unmodifiableList(postalAddresses);
    this.avatar = avatar;
  }

  private Contact(Parcel in) {
    this(in.readParcelable(Name.class.getClassLoader()),
         in.readString(),
         in.createTypedArrayList(Phone.CREATOR),
         in.createTypedArrayList(Email.CREATOR),
         in.createTypedArrayList(PostalAddress.CREATOR),
         in.readParcelable(Address.class.getClassLoader()));
  }

  @Override
  public @Nullable Contact getContact() {
    return this;
  }

  public @NonNull Name getName() {
    return name;
  }

  public @Nullable String getOrganization() {
    return organization;
  }

  public @NonNull List<Phone> getPhoneNumbers() {
    return phoneNumbers;
  }

  public @NonNull List<Email> getEmails() {
    return emails;
  }

  public @NonNull List<PostalAddress> getPostalAddresses() {
    return postalAddresses;
  }

  public @Nullable ContactAvatar getAvatar() {
    return avatar;
  }

  public String toJson() throws JSONException {
    JSONObject object = new JSONObject();
    object.put("name", name.toJson());
    object.put("organization", organization);
    object.put("phoneNumbers", listToJson(phoneNumbers));
    object.put("emails", listToJson(emails));
    object.put("postalAddresses", listToJson(postalAddresses));

    if (avatar != null) {
      object.put("avatar", avatar.toJson());
    }

    return object.toString();
  }

  public static Contact fromJson(@NonNull String json, @Nullable Attachment avatar) throws JSONException {
    JSONObject object = new JSONObject(json);

    Name name  = Name.fromJson(object.getJSONObject("name"));
    String org = object.has("organization") ? object.getString("organization") : null;

    JSONArray phoneNumbersJson = object.getJSONArray("phoneNumbers");
    List<Phone> phoneNumbers = new ArrayList<>(phoneNumbersJson.length());
    for (int i = 0; i < phoneNumbersJson.length(); i++) {
      phoneNumbers.add(Phone.fromJson(phoneNumbersJson.getJSONObject(i)));
    }

    JSONArray emailsJson = object.getJSONArray("emails");
    List<Email> emails = new ArrayList<>(emailsJson.length());
    for (int i = 0; i < emailsJson.length(); i++) {
      emails.add(Email.fromJson(emailsJson.getJSONObject(i)));
    }

    JSONArray postalAddressesJson = object.getJSONArray("postalAddresses");
    List<PostalAddress> postalAddresses = new ArrayList<>(postalAddressesJson.length());
    for (int i = 0; i < postalAddressesJson.length(); i++) {
      postalAddresses.add(PostalAddress.fromJson(postalAddressesJson.getJSONObject(i)));
    }

    ContactAvatar contactAvatar = null;
    if (avatar != null && !object.isNull("avatar")) {
      contactAvatar = ContactAvatar.fromJson(object.getJSONObject("avatar"), avatar);
    }

    return new Contact(name, org, phoneNumbers, emails, postalAddresses, contactAvatar);
  }

  private JSONArray listToJson(@NonNull List<? extends Json> objects) throws JSONException {
    JSONArray array = new JSONArray();
    for (Json object : objects) {
      array.put(object.toJson());
    }
    return array;
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeParcelable(name, flags);
    dest.writeString(organization);
    dest.writeTypedList(phoneNumbers);
    dest.writeTypedList(emails);
    dest.writeTypedList(postalAddresses);
    dest.writeParcelable(avatar, flags);
  }

  public static final Creator<Contact> CREATOR = new Creator<Contact>() {
    @Override
    public Contact createFromParcel(Parcel in) {
      return new Contact(in);
    }

    @Override
    public Contact[] newArray(int size) {
      return new Contact[size];
    }
  };

}
