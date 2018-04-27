package org.thoughtcrime.securesms.contactshare.model;

import android.support.annotation.Nullable;

public interface ContactRetriever {
  @Nullable Contact getContact();
}
