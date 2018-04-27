package org.thoughtcrime.securesms.contactshare;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.ViewModel;
import android.arch.lifecycle.ViewModelProvider;
import android.support.annotation.NonNull;

import org.thoughtcrime.securesms.contactshare.ContactRepository.ContactInfo;
import org.thoughtcrime.securesms.contactshare.model.Contact;
import org.thoughtcrime.securesms.contactshare.model.ContactRetriever;
import org.thoughtcrime.securesms.contactshare.model.Phone;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.SingleLiveEvent;

public class SharedContactViewModel extends ViewModel {

  private final ContactRepository                   repo;
  private final SingleLiveEvent<Event>              event;
  private final MutableLiveData<ContactViewDetails> contactDetails;

  private SharedContactViewModel(@NonNull Contact contact, @NonNull ContactRepository repo) {
    this.repo           = repo;
    this.event          = new SingleLiveEvent<>();
    this.contactDetails = new MutableLiveData<>();

    loadInitialContact(contact);
  }

  private SharedContactViewModel(@NonNull ContactRetriever retriever, @NonNull ContactRepository repo) {
    this.repo           = repo;
    this.event          = new SingleLiveEvent<>();
    this.contactDetails = new MutableLiveData<>();

    repo.retrieveContact(retriever, contact -> {
      if (contact != null) {
        loadInitialContact(contact);
      } else {
        event.postValue(Event.INITIALIZATION_ERROR);
      }
    });
  }

  private void loadInitialContact(@NonNull Contact contact) {
    contactDetails.postValue(new ContactViewDetails(new ContactInfo(contact), ContactState.LOADING));

    repo.getMatchingExistingContact(contact, contactInfo -> {
      if (contactInfo != null) {
        contactDetails.postValue(new ContactViewDetails(contactInfo, ContactState.ADDED));
      } else {
        contactDetails.postValue(new ContactViewDetails(new ContactInfo(contact), ContactState.NEW));
      }
    });
  }

  public void refresh(ContactRetriever retriever) {
    repo.retrieveContact(retriever, contact -> {
      if (contact != null) {
        loadInitialContact(contact);
      } else {
        event.postValue(Event.INITIALIZATION_ERROR);
      }
    });
  }

  public LiveData<Event> getEvent() {
    return event;
  }

  public LiveData<ContactViewDetails> getContactDetails() {
    return contactDetails;
  }

  public void saveAsNewContact() {
    repo.saveAsNewContact(getContactOrThrow(), contactInfo -> {
      if (contactInfo != null) {
        contactDetails.postValue(new ContactViewDetails(contactInfo, ContactState.ADDED));
        event.postValue(Event.NEW_CONTACT_SUCCESS);
      } else {
        event.postValue(Event.NEW_CONTACT_ERROR);
      }
    });
  }

  public void saveDetailsToExistingContact(long contactId) {
    repo.saveDetailsToExistingContact(contactId, getContactOrThrow(), contactInfo -> {
      if (contactInfo != null) {
        contactDetails.postValue(new ContactViewDetails(contactInfo, ContactState.ADDED));
        event.postValue(Event.EDIT_CONTACT_SUCCESS);
      } else {
        event.postValue(Event.EDIT_CONTACT_ERROR);
      }
    });
  }

  public LiveData<Long> getThreadId(@NonNull Address address) {
    SingleLiveEvent<Long> threadId = new SingleLiveEvent<>();
    repo.getThreadId(address, threadId::postValue);
    return threadId;
  }

  public LiveData<Recipient> getResolvedRecipient(@NonNull Phone phoneNumber) {
    SingleLiveEvent<Recipient> recipient = new SingleLiveEvent<>();
    repo.getResolvedRecipient(phoneNumber, recipient::postValue);
    return recipient;
  }

  private @NonNull Contact getContactOrThrow() {
    ContactViewDetails contactDetails = this.contactDetails.getValue();
    if (contactDetails == null) {
      throw new IllegalStateException("No contact available. This should never happen.");
    }
    return contactDetails.getContactInfo().getContact();
  }

  public static class ContactViewDetails {

    private final ContactInfo  contactInfo;
    private final ContactState state;

    ContactViewDetails(@NonNull ContactInfo contact, @NonNull ContactState state) {
      this.contactInfo = contact;
      this.state       = state;
    }

    public @NonNull ContactInfo getContactInfo() {
      return contactInfo;
    }

    public @NonNull ContactState getState() {
      return state;
    }
  }

  public enum ContactState {
    NEW, ADDED, LOADING
  }

  public enum Event {
    NEW_CONTACT_SUCCESS,
    NEW_CONTACT_ERROR,
    EDIT_CONTACT_SUCCESS,
    EDIT_CONTACT_ERROR,
    INITIALIZATION_ERROR
  }

  public static class ContactFactory extends ViewModelProvider.NewInstanceFactory {

    private final Contact           contact;
    private final ContactRepository repo;

    public ContactFactory(@NonNull Contact contact, @NonNull ContactRepository repo) {
      this.contact = contact;
      this.repo    = repo;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      return modelClass.cast(new SharedContactViewModel(contact, repo));
    }
  }

  public static class RetrieverFactory extends ViewModelProvider.NewInstanceFactory {

    private final ContactRetriever  retriever;
    private final ContactRepository repo;

    public RetrieverFactory(@NonNull ContactRetriever retriever, @NonNull ContactRepository repo) {
      this.retriever = retriever;
      this.repo      = repo;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      return modelClass.cast(new SharedContactViewModel(retriever, repo));
    }
  }
}
