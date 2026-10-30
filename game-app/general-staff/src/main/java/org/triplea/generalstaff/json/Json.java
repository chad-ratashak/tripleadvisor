package org.triplea.generalstaff.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.time.Instant;

/** The one Gson configuration the module's public surfaces use; java.time values are text. */
public final class Json {
  public static final Gson GSON =
      new GsonBuilder()
          .setPrettyPrinting()
          .disableHtmlEscaping()
          .serializeNulls()
          .registerTypeAdapter(Instant.class, new InstantAdapter().nullSafe())
          .create();

  private Json() {}

  private static final class InstantAdapter extends TypeAdapter<Instant> {
    @Override
    public void write(final JsonWriter out, final Instant value) throws IOException {
      out.value(value.toString());
    }

    @Override
    public Instant read(final JsonReader in) throws IOException {
      if (in.peek() == JsonToken.NULL) {
        in.nextNull();
        return null;
      }
      return Instant.parse(in.nextString());
    }
  }
}
