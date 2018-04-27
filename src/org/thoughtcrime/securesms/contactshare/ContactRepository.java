package org.thoughtcrime.securesms.contactshare;

import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.text.TextUtils;
import android.util.Log;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.contacts.ContactsDatabase;
import org.thoughtcrime.securesms.contacts.avatars.ContactPhoto;
import org.thoughtcrime.securesms.contactshare.model.Contact;
import org.thoughtcrime.securesms.contactshare.model.ContactAvatar;
import org.thoughtcrime.securesms.contactshare.model.ContactRetriever;
import org.thoughtcrime.securesms.contactshare.model.Email;
import org.thoughtcrime.securesms.contactshare.model.Name;
import org.thoughtcrime.securesms.contactshare.model.Phone;
import org.thoughtcrime.securesms.contactshare.model.PostalAddress;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.RecipientDatabase.RegisteredState;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.providers.PersistentBlobProvider;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.DirectoryHelper;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.Util;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

public class ContactRepository {

  private static final String TAG = ContactRepository.class.getSimpleName();

  private final Context          context;
  private final Executor         executor;
  private final Locale           locale;
  private final ContactsDatabase contactsDatabase;
  private final ThreadDatabase   threadDatabase;

  public ContactRepository(@NonNull Context          context,
                           @NonNull Executor         executor,
                           @NonNull Locale           locale,
                           @NonNull ContactsDatabase contactsDatabase,
                           @NonNull ThreadDatabase   threadDatabase)
  {
    this.context          = context.getApplicationContext();
    this.executor         = executor;
    this.locale           = locale;
    this.contactsDatabase = contactsDatabase;
    this.threadDatabase   = threadDatabase;
  }

  void getContacts(@NonNull List<Long> contactIds, @NonNull ValueCallback<List<Contact>> callback) {
    executor.execute(() -> {
      List<Contact> contacts = new ArrayList<>(contactIds.size());
      for (long id : contactIds) {
        Contact contact = getContact(id);
        if (contact != null) {
          contacts.add(contact);
        }
      }
      callback.onComplete(contacts);
    });
  }

  void retrieveContact(@NonNull ContactRetriever retriever, @NonNull NullableValueCallback<Contact> callback) {
    executor.execute(() -> {
      callback.onComplete(retriever.getContact());
    });
  }

  void saveAsNewContact(@NonNull Contact contact, @NonNull ContactUpdateCallback callback) {
    executor.execute(() -> {
      ArrayList<ContentProviderOperation> ops = buildNewContactOperations(contact);

      try {
        ContentProviderResult[] results = context.getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
        if (results.length == 0) {
          Log.e(TAG, "Failed to insert a system contact - no successful results.");
          callback.onComplete(null);
          return;
        }

        long rawContactId = ContactUtil.getContactIdFromUri(results[0].uri);
        long contactId    = contactsDatabase.getContactIdFromRawContactId(rawContactId);

        if (contactId <= 0) {
          Log.e(TAG, "Failed to insert a system contact - invalid ID.");
          callback.onComplete(null);
          return;
        }

        Contact newContact = getContact(contactId);

        if (newContact == null) {
          Log.e(TAG, "Inserted a new contact in the system, but failed to retrieve it. Likely failed to save some necessary data, like the name. Deleting bad system contact.");
          boolean deleteSuccess = contactsDatabase.deleteContact(rawContactId);
          Log.e(TAG, "Successfully deleted bad contact? " + Boolean.toString(deleteSuccess));

          callback.onComplete(null);
          return;
        }

        callback.onComplete(buildContactInfo(newContact));

      } catch (RemoteException | OperationApplicationException e) {
        Log.e(TAG,"Failed to insert a system contact due to an exception.", e);
        callback.onComplete(null);
      }
    });
  }

  void saveDetailsToExistingContact(long contactId, @NonNull Contact contact, @NonNull ContactUpdateCallback callback) {
    executor.execute(() -> {
      Contact existing = getContact(contactId);

      if (existing == null) {
        Log.w(TAG, "Failed to find the contact we're saving to.");
        callback.onComplete(null);
        return;
      }

      long rawContactId = contactsDatabase.getRawContactIdFromContactId(contactId);
      if (rawContactId <= 0) {
        Log.w(TAG, "Failed to retrieve a raw contact ID for the contact we're saving to.");
        callback.onComplete(null);
        return;
      }

      ContactDiff contactDiff = buildContactDiff(existing, contact);

      ArrayList<ContentProviderOperation> ops = new ArrayList<>();
      for (Phone phone : contactDiff.getPhoneNumbers()) {
        ops.add(getPhoneInsertOperation(phone, rawContactId));
      }

      for (Email email : contactDiff.getEmails()) {
        ops.add(getEmailInsertOperation(email, rawContactId));
      }

      for (PostalAddress postalAddress : contactDiff.getPostalAddresses()) {
        ops.add(getPostalAddressInsertOperation(postalAddress, rawContactId));
      }

      if (contactDiff.getOrganization() != null) {
        ops.add(getOrganizationInsertOperation(contactDiff.getOrganization(), rawContactId));
      }

      if (contactDiff.getAvatar() != null && contactDiff.getAvatar().getImage().getDataUri() != null) {
        ContentProviderOperation op = getAvatarInsertOperation(contactDiff.getAvatar().getImage().getDataUri(), rawContactId);
        if (op != null) {
          ops.add(op);
        }
      }

      try {
        context.getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
      } catch (RemoteException | OperationApplicationException e) {
        Log.e(TAG, "Failed to update the existing contact with new details.", e);
        callback.onComplete(null);
        return;
      }

      Contact updated = getContact(contactId);
      callback.onComplete(updated != null ? buildContactInfo(updated) : null);
    });
  }

  void getThreadId(@NonNull Address address, @NonNull ValueCallback<Long> callback) {
    executor.execute(() -> {
      Recipient recipient = Recipient.from(context, address, false);
      callback.onComplete(threadDatabase.getThreadIdFor(recipient));
    });
  }

  void getResolvedRecipient(@NonNull Phone phoneNumber, @NonNull ValueCallback<Recipient> callback) {
    executor.execute(() -> callback.onComplete(Recipient.from(context, Address.fromExternal(context, phoneNumber.getNumber()), false)));
  }

  void getMatchingExistingContact(@NonNull Contact contact, @NonNull ContactMatchCallback callback) {
    executor.execute(() -> {
      long contactId = queryForContactId(contact);

      if (contactId <= 0) {
        callback.onComplete(null);
        return;
      }

      Contact existingContact = getContact(contactId);
      if (existingContact == null) {
        callback.onComplete(null);
        return;
      }

      callback.onComplete(isSuperSet(existingContact, contact) ? buildContactInfo(existingContact) : null);
    });
  }

  void persistContactImages(@NonNull List<Contact> contacts, ValueCallback<List<Contact>> callback) {
    executor.execute(() -> {
      List<Contact> persisted = new ArrayList<>(contacts.size());

      for (Contact contact : contacts) {
        ContactAvatar avatar = null;

        if (contact.getAvatar() != null && contact.getAvatar().getImage().getDataUri() != null) {
          try {
            Uri avatarUri = persistContactPhoto(contact.getAvatar().getImage().getDataUri());
            avatar = new ContactAvatar(avatarUri, contact.getAvatar().isProfile());
          } catch (IOException e) {
            Log.w(TAG, "Failed to persist avatar. Skipping it.", e);
          }
        }

        persisted.add(new Contact(contact.getName(),
                                  contact.getOrganization(),
                                  contact.getPhoneNumbers(),
                                  contact.getEmails(),
                                  contact.getPostalAddresses(),
                                  avatar));
      }

      callback.onComplete(persisted);
    });
  }

  private @NonNull ContactDiff buildContactDiff(@NonNull Contact existing, @NonNull Contact test) {
    ContactDiff diff = new ContactDiff();

    Set<String> numbers = new HashSet<>();
    Stream.of(existing.getPhoneNumbers()).forEach(phone -> numbers.add(phone.getNumber()));

    for (Phone phone : test.getPhoneNumbers()) {
      if (!numbers.contains(phone.getNumber())) {
        diff.addPhone(phone);
      }
    }

    Set<String> emails = new HashSet<>();
    Stream.of(existing.getEmails()).forEach(email -> emails.add(email.getEmail()));

    for (Email email : test.getEmails()) {
      if (!emails.contains(email.getEmail())) {
        diff.addEmail(email);
      }
    }

    Set<String> postalAddresses = new HashSet<>();
    Stream.of(existing.getPostalAddresses()).forEach(postalAddress ->  postalAddresses.add(postalAddress.toString()));

    for (PostalAddress postalAddress : test.getPostalAddresses()) {
      if (!postalAddresses.contains(postalAddress.toString())) {
        diff.addPostalAddress(postalAddress);
      }
    }

    if (TextUtils.isEmpty(existing.getOrganization()) && !TextUtils.isEmpty(test.getOrganization())) {
      diff.setOrganization(test.getOrganization());
    }

    boolean testHasValidAvatar = test.getAvatar() != null && test.getAvatar().getImage().getDataUri() != null && !test.getAvatar().isProfile();
    if (existing.getAvatar() == null && testHasValidAvatar) {
      diff.setAvatar(test.getAvatar());
    }

    return diff;
  }

  private ArrayList<ContentProviderOperation> buildNewContactOperations(@NonNull Contact contact) {
    ArrayList<ContentProviderOperation> ops = new ArrayList<>();

    ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
        .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
        .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
        .build());

    ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
        .withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, contact.getName().getGivenName())
        .withValue(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, contact.getName().getFamilyName())
        .withValue(ContactsContract.CommonDataKinds.StructuredName.PREFIX, contact.getName().getPrefix())
        .withValue(ContactsContract.CommonDataKinds.StructuredName.SUFFIX, contact.getName().getSuffix())
        .withValue(ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME, contact.getName().getMiddleName())
        .build());

    if (!TextUtils.isEmpty(contact.getOrganization())) {
      ops.add(getOrganizationInsertOperation(contact.getOrganization(), 0));
    }

    for (Phone phoneNumber : contact.getPhoneNumbers()) {
      ops.add(getPhoneInsertOperation(phoneNumber, 0));
    }

    for (Email email : contact.getEmails()) {
      ops.add(getEmailInsertOperation(email, 0));
    }

    for (PostalAddress postalAddress : contact.getPostalAddresses()) {
      ops.add(getPostalAddressInsertOperation(postalAddress, 0));
    }

    if (contact.getAvatar() != null && !contact.getAvatar().isProfile() && contact.getAvatar().getImage().getDataUri() != null) {
      ContentProviderOperation op = getAvatarInsertOperation(contact.getAvatar().getImage().getDataUri(), 0);
      if (op != null) {
        ops.add(op);
      }
    }

    return ops;
  }

  private @NonNull ContentProviderOperation getOrganizationInsertOperation(@NonNull String organization, long contactId) {
    ContentProviderOperation.Builder builder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                                                       .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
                                                                       .withValue(ContactsContract.CommonDataKinds.Organization.COMPANY, organization);
    if (contactId > 0) {
      builder.withValue(ContactsContract.Data.RAW_CONTACT_ID, contactId);
    } else {
      builder.withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0);
    }

    return builder.build();
  }

  private @NonNull ContentProviderOperation getPhoneInsertOperation(@NonNull Phone phoneNumber, long contactId) {
    ContentProviderOperation.Builder builder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                                                       .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                                                                       .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phoneNumber.getNumber())
                                                                       .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, getSystemType(phoneNumber.getType()))
                                                                       .withValue(ContactsContract.CommonDataKinds.Phone.LABEL, phoneNumber.getLabel());
    if (contactId > 0) {
      builder.withValue(ContactsContract.Data.RAW_CONTACT_ID, contactId);
    } else {
      builder.withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0);
    }

    return builder.build();
  }

  private @NonNull ContentProviderOperation getEmailInsertOperation(@NonNull Email email, long contactId) {
    ContentProviderOperation.Builder builder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                                                       .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                                                                       .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, email.getEmail())
                                                                       .withValue(ContactsContract.CommonDataKinds.Email.TYPE, getSystemType(email.getType()))
                                                                       .withValue(ContactsContract.CommonDataKinds.Email.LABEL, email.getLabel());

    if (contactId > 0) {
      builder.withValue(ContactsContract.Data.RAW_CONTACT_ID, contactId);
    } else {
      builder.withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0);
    }

    return builder.build();
  }

  private @NonNull ContentProviderOperation getPostalAddressInsertOperation(@NonNull PostalAddress postalAddress, long contactId) {
    ContentProviderOperation.Builder builder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                                                       .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE)
                                                                       .withValue(ContactsContract.CommonDataKinds.StructuredPostal.TYPE, getSystemType(postalAddress.getType()))
                                                                       .withValue(ContactsContract.CommonDataKinds.StructuredPostal.LABEL, postalAddress.getLabel())
                                                                       .withValue(ContactsContract.CommonDataKinds.StructuredPostal.STREET, postalAddress.getStreet())
                                                                       .withValue(ContactsContract.CommonDataKinds.StructuredPostal.POBOX, postalAddress.getPoBox())
                                                                       .withValue(ContactsContract.CommonDataKinds.StructuredPostal.NEIGHBORHOOD, postalAddress.getNeighborhood())
                                                                       .withValue(ContactsContract.CommonDataKinds.StructuredPostal.CITY, postalAddress.getCity())
                                                                       .withValue(ContactsContract.CommonDataKinds.StructuredPostal.REGION, postalAddress.getRegion())
                                                                       .withValue(ContactsContract.CommonDataKinds.StructuredPostal.POSTCODE, postalAddress.getPostalCode())
                                                                       .withValue(ContactsContract.CommonDataKinds.StructuredPostal.COUNTRY, postalAddress.getCountry());

    if (contactId > 0) {
      builder.withValue(ContactsContract.Data.RAW_CONTACT_ID, contactId);
    } else {
      builder.withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0);
    }

    return builder.build();
  }

  private @Nullable ContentProviderOperation getAvatarInsertOperation(@NonNull Uri avatarUri, long contactId) {
    try (InputStream avatarStream = PartAuthority.getAttachmentStream(context, avatarUri)) {
      byte[] avatarBytes = Util.readFully(avatarStream);

      ContentProviderOperation.Builder builder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                                                         .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                                                                         .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, avatarBytes);

      if (contactId > 0) {
        builder.withValue(ContactsContract.Data.RAW_CONTACT_ID, contactId);
      } else {
        builder.withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0);
      }

      return builder.build();
    } catch (IOException e) {
      Log.e(TAG, "Failed to read avatar bytes. Will still attempt to add the contact, but no photo will be set.", e);
    }
    return null;
  }

  @WorkerThread
  private @NonNull ContactInfo buildContactInfo(@NonNull Contact contact) {
    ContactInfo contactInfo = new ContactInfo(contact);

    for (Phone phoneNumber : contact.getPhoneNumbers()) {
      Recipient       recipient = Recipient.from(context, Address.fromExternal(context, phoneNumber.getNumber()), false);
      RegisteredState state     = recipient.getRegistered();

      if (recipient.getRegistered() != RegisteredState.UNKNOWN) {
        contactInfo.setIsPush(phoneNumber, state == RegisteredState.REGISTERED);
        continue;
      }

      try {
        state = DirectoryHelper.refreshDirectoryFor(context, recipient);
        contactInfo.setIsPush(phoneNumber, state == RegisteredState.REGISTERED);
      } catch (IOException e) {
        Log.w(TAG, "Failed to determine if a number was registered. Defaulting to false.", e);
      }
    }

    return contactInfo;
  }

  private boolean isSuperSet(@NonNull Contact existingContact, @NonNull Contact testContact) {
    ContactDiff diff = buildContactDiff(existingContact, testContact);

    return diff.getPhoneNumbers().isEmpty() && diff.getEmails().isEmpty() && diff.getPostalAddresses().isEmpty();
  }

  @WorkerThread
  private @Nullable Contact getContact(long contactId) {
    Name name = getName(contactId);
    if (name == null) {
      Log.w(TAG, "Couldn't find a name associated with the provided contact ID.");
      return null;
    }

    List<Phone> phoneNumbers = getPhoneNumbers(contactId);

    return new Contact(name,
                       null,
                       phoneNumbers,
                       getEmails(contactId),
                       getPostalAddresses(contactId),
                       getAvatar(contactId, phoneNumbers));
  }

  @WorkerThread
  private @Nullable Name getName(long contactId) {
    try (Cursor cursor = contactsDatabase.getNameDetails(contactId)) {
      if (cursor != null && cursor.moveToFirst()) {
        String cursorDisplayName = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME));
        String cursorGivenName   = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME));
        String cursorFamilyName  = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME));
        String cursorPrefix      = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredName.PREFIX));
        String cursorSuffix      = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredName.SUFFIX));
        String cursorMiddleName  = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME));

        Name name = new Name(cursorDisplayName, cursorGivenName, cursorFamilyName, cursorPrefix, cursorSuffix, cursorMiddleName);
        if (!name.isEmpty()) {
          return name;
        }
      }
    }

    String org = contactsDatabase.getOrganizationName(contactId);
    if (!TextUtils.isEmpty(org)) {
      return new Name(org, org, null, null, null, null);
    }

    return null;
  }

  @WorkerThread
  private @NonNull List<Phone> getPhoneNumbers(long contactId) {
    Map<String, Phone> numberMap = new HashMap<>();
    try (Cursor cursor = contactsDatabase.getPhoneDetails(contactId)) {
      while (cursor != null && cursor.moveToNext()) {
        String cursorNumber = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER));
        int    cursorType   = cursor.getInt(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE));
        String cursorLabel  = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.LABEL));

        String number    = ContactUtil.getNormalizedPhoneNumber(context, cursorNumber);
        Phone  existing  = numberMap.get(number);
        Phone  candidate = new Phone(number, phoneTypeFromContactType(cursorType), cursorLabel);

        if (existing == null || (existing.getType() == Phone.Type.CUSTOM && existing.getLabel() == null)) {
          numberMap.put(number, candidate);
        }
      }
    }
    List<Phone> numbers = new ArrayList<>(numberMap.size());
    numbers.addAll(numberMap.values());
    return numbers;
  }

  @WorkerThread
  private @NonNull List<Email> getEmails(long contactId) {
    List<Email> emails = new LinkedList<>();

    try (Cursor cursor = contactsDatabase.getEmailDetails(contactId)) {
      while (cursor != null && cursor.moveToNext()) {
        String cursorEmail = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.ADDRESS));
        int    cursorType  = cursor.getInt(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.TYPE));
        String cursorLabel = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.LABEL));

        emails.add(new Email(cursorEmail, emailTypeFromContactType(cursorType), cursorLabel));
      }
    }

    return emails;
  }

  @WorkerThread
  private @NonNull List<PostalAddress> getPostalAddresses(long contactId) {
    List<PostalAddress> postalAddresses = new LinkedList<>();

    try (Cursor cursor = contactsDatabase.getPostalAddressDetails(contactId)) {
      while (cursor != null && cursor.moveToNext()) {
        int    cursorType         = cursor.getInt(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredPostal.TYPE));
        String cursorLabel        = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredPostal.LABEL));
        String cursorStreet       = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredPostal.STREET));
        String cursorPoBox        = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredPostal.POBOX));
        String cursorNeighborhood = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredPostal.NEIGHBORHOOD));
        String cursorCity         = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredPostal.CITY));
        String cursorRegion       = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredPostal.REGION));
        String cursorPostal       = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredPostal.POSTCODE));
        String cursorCountry      = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredPostal.COUNTRY));

        postalAddresses.add(new PostalAddress(postalAddressTypeFromContactType(cursorType),
                                              cursorLabel,
                                              cursorStreet,
                                              cursorPoBox,
                                              cursorNeighborhood,
                                              cursorCity,
                                              cursorRegion,
                                              cursorPostal,
                                              cursorCountry));
      }
    }

    return postalAddresses;
  }

  @WorkerThread
  private @Nullable ContactAvatar getAvatar(long contactId, List<Phone> phoneNumbers) {
    ContactAvatar systemAvatar = getSystemAvatar(contactId);

    if (systemAvatar != null) {
      return systemAvatar;
    }

    for (Phone phoneNumber : phoneNumbers) {
      ContactAvatar recipientAvatar = getRecipientAvatar(Address.fromExternal(context, phoneNumber.getNumber()));
      if (recipientAvatar != null) {
        return recipientAvatar;
      }
    }
    return null;
  }

  @WorkerThread
  private @Nullable ContactAvatar getSystemAvatar(long contactId) {
    Uri uri = contactsDatabase.getAvatarUri(contactId);
    if (uri != null) {
      return new ContactAvatar(uri, false);
    }

    return null;
  }

  @WorkerThread
  private @Nullable ContactAvatar getRecipientAvatar(@NonNull Address address) {
    Recipient    recipient    = Recipient.from(context, address, false);
    ContactPhoto contactPhoto = recipient.getContactPhoto();

    if (contactPhoto != null) {
      Uri avatarUri = contactPhoto.getUri(context);
      if (avatarUri != null) {
        return new ContactAvatar(avatarUri, contactPhoto.isProfilePhoto());
      }
    }

    return null;
  }

  @WorkerThread
  private long queryForContactId(@NonNull Contact contact) {
    long contactId = -1;

    if (contact.getPhoneNumbers().size() > 0) {
      contactId = contactsDatabase.getContactIdByPhoneNumber(contact.getPhoneNumbers().get(0).getNumber());
    }

    if (contactId < 0 && contact.getPhoneNumbers().size() > 0) {
      contactId = contactsDatabase.getContactIdByPhoneNumber(ContactUtil.getLocalPhoneNumber(contact.getPhoneNumbers().get(0).getNumber(), locale));
    }

    if (contactId <= 0 && contact.getEmails().size() > 0) {
      contactId = contactsDatabase.getContactIdByEmail(contact.getEmails().get(0).getEmail());
    }

    return contactId;
  }

  @WorkerThread
  private @NonNull Uri persistContactPhoto(@NonNull Uri avatarUri) throws IOException {
    InputStream avatarStream = PartAuthority.getAttachmentStream(context, avatarUri);
    return PersistentBlobProvider.getInstance(context).create(context, avatarStream, MediaUtil.IMAGE_JPEG, null, null);
  }

  private Phone.Type phoneTypeFromContactType(int type) {
    switch (type) {
      case ContactsContract.CommonDataKinds.Phone.TYPE_HOME:
        return Phone.Type.HOME;
      case ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE:
        return Phone.Type.MOBILE;
      case ContactsContract.CommonDataKinds.Phone.TYPE_WORK:
        return Phone.Type.WORK;
    }
    return Phone.Type.CUSTOM;
  }

  private Email.Type emailTypeFromContactType(int type) {
    switch (type) {
      case ContactsContract.CommonDataKinds.Email.TYPE_HOME:
        return Email.Type.HOME;
      case ContactsContract.CommonDataKinds.Email.TYPE_MOBILE:
        return Email.Type.MOBILE;
      case ContactsContract.CommonDataKinds.Email.TYPE_WORK:
        return Email.Type.WORK;
    }
    return Email.Type.CUSTOM;
  }

  private PostalAddress.Type postalAddressTypeFromContactType(int type) {
    switch (type) {
      case ContactsContract.CommonDataKinds.StructuredPostal.TYPE_HOME:
        return PostalAddress.Type.HOME;
      case ContactsContract.CommonDataKinds.StructuredPostal.TYPE_WORK:
        return PostalAddress.Type.WORK;
    }
    return PostalAddress.Type.CUSTOM;
  }

  private int getSystemType(Phone.Type type) {
    switch (type) {
      case HOME:   return ContactsContract.CommonDataKinds.Phone.TYPE_HOME;
      case MOBILE: return ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE;
      case WORK:   return ContactsContract.CommonDataKinds.Phone.TYPE_WORK;
      default:     return ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM;
    }
  }

  private int getSystemType(Email.Type type) {
    switch (type) {
      case HOME:   return ContactsContract.CommonDataKinds.Email.TYPE_HOME;
      case MOBILE: return ContactsContract.CommonDataKinds.Email.TYPE_MOBILE;
      case WORK:   return ContactsContract.CommonDataKinds.Email.TYPE_WORK;
      default:     return ContactsContract.CommonDataKinds.Email.TYPE_CUSTOM;
    }
  }

  private int getSystemType(PostalAddress.Type type) {
    switch (type) {
      case HOME: return ContactsContract.CommonDataKinds.StructuredPostal.TYPE_HOME;
      case WORK: return ContactsContract.CommonDataKinds.StructuredPostal.TYPE_WORK;
      default:   return ContactsContract.CommonDataKinds.StructuredPostal.TYPE_CUSTOM;
    }
  }

  interface ValueCallback<T> {
    void onComplete(@NonNull T value);
  }

  interface NullableValueCallback<T> {
    void onComplete(@Nullable T value);
  }

  interface ContactUpdateCallback {
    void onComplete(@Nullable ContactInfo newContact);
  }

  interface ContactMatchCallback {
    /** @param matchedContact A contact that is a superset of the provided contact, otherwise null.  */
    void onComplete(@Nullable ContactInfo matchedContact);
  }

  public static class ContactInfo {

    private final Contact             contact;
    private final Map<Phone, Boolean> isPushMap;

    ContactInfo(@NonNull Contact contact) {
      this.contact   = contact;
      this.isPushMap = new HashMap<>();
    }

    public @NonNull Contact getContact() {
      return contact;
    }

    private void setIsPush(@NonNull Phone phoneNumber, boolean isPush) {
      isPushMap.put(phoneNumber, isPush);
    }

    public boolean isPush(Phone phoneNumber) {
      return isPushMap.containsKey(phoneNumber) ? isPushMap.get(phoneNumber) : false;
    }
  }

  private static class ContactDiff {

    private final List<Phone>         phoneNumbers    = new LinkedList<>();
    private final List<Email>         emails          = new LinkedList<>();
    private final List<PostalAddress> postalAddresses = new LinkedList<>();

    private String        organization;
    private ContactAvatar avatar;

    private void addPhone(@NonNull Phone phoneNumber) {
      phoneNumbers.add(phoneNumber);
    }

    private @NonNull List<Phone> getPhoneNumbers() {
      return phoneNumbers;
    }

    private void addEmail(@NonNull Email email) {
      emails.add(email);
    }

    private @NonNull List<Email> getEmails() {
      return emails;
    }

    private void addPostalAddress(@NonNull PostalAddress postalAddress) {
      postalAddresses.add(postalAddress);
    }

    private @NonNull List<PostalAddress> getPostalAddresses() {
      return postalAddresses;
    }

    private void setOrganization(@Nullable String organization) {
      this.organization = organization;
    }

    private @Nullable String getOrganization() {
      return organization;
    }

    private void setAvatar(@NonNull ContactAvatar avatar) {
      this.avatar = avatar;
    }

    private @Nullable ContactAvatar getAvatar() {
      return avatar;
    }
  }
}
