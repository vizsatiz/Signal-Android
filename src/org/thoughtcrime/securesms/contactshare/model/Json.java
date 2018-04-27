package org.thoughtcrime.securesms.contactshare.model;

import org.json.JSONException;
import org.json.JSONObject;

interface Json {
  JSONObject toJson() throws JSONException;
}
