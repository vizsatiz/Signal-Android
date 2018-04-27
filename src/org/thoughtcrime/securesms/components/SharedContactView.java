package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.load.engine.DiskCacheStrategy;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.contactshare.ContactRepository.ContactInfo;
import org.thoughtcrime.securesms.contactshare.ContactUtil;
import org.thoughtcrime.securesms.contactshare.SharedContactViewModel.ContactViewDetails;
import org.thoughtcrime.securesms.contactshare.model.Contact;
import org.thoughtcrime.securesms.contactshare.model.Phone;
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader.DecryptableUri;
import org.thoughtcrime.securesms.mms.GlideRequests;

import java.util.Locale;

public class SharedContactView extends LinearLayout {

  private ImageView avatarView;
  private TextView  nameView;
  private TextView  numberView;
  private TextView  actionButtonView;
  private ViewGroup actionButtonContainerView;

  private EventListener eventListener;

  public SharedContactView(Context context) {
    super(context);
    initialize();
  }

  public SharedContactView(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

  public SharedContactView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    initialize();
  }

  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  public SharedContactView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    initialize();
  }

  private void initialize() {
    inflate(getContext(), R.layout.shared_contact_view, this);

    avatarView                = findViewById(R.id.contact_avatar);
    nameView                  = findViewById(R.id.contact_name);
    numberView                = findViewById(R.id.contact_number);
    actionButtonView          = findViewById(R.id.contact_action_button);
    actionButtonContainerView = findViewById(R.id.contact_action_button_container);
  }

  public void setContact(@NonNull ContactViewDetails contactDetails, @NonNull GlideRequests glideRequests, @NonNull Locale locale) {
    Contact contact       = contactDetails.getContactInfo().getContact();
    Phone   displayNumber = ContactUtil.getDisplayNumber(contactDetails.getContactInfo());

    presentHeader(contact, displayNumber, glideRequests, locale);

    switch (contactDetails.getState()) {
      case NEW:
        presentNewContactState(contact);
        break;
      case ADDED:
        presentAddedContactState(contactDetails.getContactInfo(), displayNumber);
        break;
    }
  }

  private void presentHeader(@NonNull Contact contact, @Nullable Phone displayNumber, @NonNull GlideRequests glideRequests, @NonNull Locale locale) {
    if (contact.getAvatar() != null && contact.getAvatar().getImage().getDataUri() != null) {
      glideRequests.load(new DecryptableUri(contact.getAvatar().getImage().getDataUri()))
                   .fallback(R.drawable.ic_contact_picture)
                   .circleCrop()
                   .diskCacheStrategy(DiskCacheStrategy.ALL)
                   .into(avatarView);
    } else {
      glideRequests.load(R.drawable.ic_contact_picture)
                   .circleCrop()
                   .diskCacheStrategy(DiskCacheStrategy.ALL)
                   .into(avatarView);
    }

    nameView.setText(ContactUtil.getDisplayName(contact));

    if (displayNumber != null) {
      numberView.setText(ContactUtil.getPrettyPhoneNumber(displayNumber, locale));
    } else if (contact.getEmails().size() > 0) {
      numberView.setText(contact.getEmails().get(0).getEmail());
    } else {
      numberView.setText("");
    }
  }

  private void presentNewContactState(@NonNull Contact contact) {
    actionButtonView.setText(R.string.SharedContactView_add_to_contacts);
    actionButtonView.setOnClickListener(v -> {
      if (eventListener != null) {
        eventListener.onAddToContactsClicked(contact);
      }
    });
  }

  private void presentAddedContactState(@NonNull ContactInfo contactInfo, @Nullable Phone displayNumber) {
    if (displayNumber == null) {
      actionButtonContainerView.setVisibility(GONE);
      return;
    }

    actionButtonContainerView.setVisibility(VISIBLE);

    if (contactInfo.isPush(displayNumber)) {
      actionButtonView.setText(R.string.SharedContactView_message);

      actionButtonView.setOnClickListener(v -> {
        if (eventListener != null) {
          eventListener.onMessageClicked(displayNumber);
        }
      });
    } else {
      actionButtonView.setText(R.string.SharedContactView_invite_to_signal);

      actionButtonView.setOnClickListener(v -> {
        if (eventListener != null) {
          eventListener.onInviteClicked(displayNumber);
        }
      });
    }
  }

  public View getAvatarView() {
    return avatarView;
  }

  public void setEventListener(EventListener listener) {
    this.eventListener = listener;
  }

  public interface EventListener {
    void onAddToContactsClicked(@NonNull Contact contact);
    void onInviteClicked(@NonNull Phone phoneNumber);
    void onMessageClicked(@NonNull Phone phoneNumber);
  }
}
