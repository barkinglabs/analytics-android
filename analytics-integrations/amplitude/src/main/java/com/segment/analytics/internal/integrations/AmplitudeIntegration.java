package com.segment.analytics.internal.integrations;

import com.amplitude.api.AmplitudeClient;
import com.segment.analytics.Analytics;
import com.segment.analytics.Properties;
import com.segment.analytics.ValueMap;
import com.segment.analytics.internal.AbstractIntegration;
import com.segment.analytics.internal.Log;
import com.segment.analytics.internal.model.payloads.IdentifyPayload;
import com.segment.analytics.internal.model.payloads.ScreenPayload;
import com.segment.analytics.internal.model.payloads.TrackPayload;
import org.json.JSONObject;

import static com.segment.analytics.internal.Utils.isNullOrEmpty;

/**
 * Amplitude is an event tracking and segmentation tool for your mobile apps. By analyzing the
 * actions your users perform you can gain a better understanding of how they use your app.
 *
 * @see <a href="https://amplitude.com">Amplitude</a>
 * @see <a href="https://segment.com/docs/integrations/amplitude/">Amplitude Integration</a>
 * @see <a href="https://github.com/amplitude/Amplitude-Android">Amplitude Android SDK</a>
 */
public class AmplitudeIntegration extends AbstractIntegration<Void> {

  static final String AMPLITUDE_KEY = "Amplitude";
  boolean trackAllPages;
  boolean trackCategorizedPages;
  boolean trackNamedPages;
  final Provider provider;
  AmplitudeClient amplitude;
  Log log;

  // Using PowerMockito fails with https://cloudup.com/c5JPuvmTCaH. So we introduce a provider
  // abstraction to mock what AmplitudeClient.getInstance() returns.
  interface Provider {
    AmplitudeClient get();

    Provider REAL = new Provider() {
      @Override public AmplitudeClient get() {
        return AmplitudeClient.getInstance();
      }
    };
  }

  // Used by reflection.
  @SuppressWarnings("unused") AmplitudeIntegration() {
    this(Provider.REAL);
  }

  AmplitudeIntegration(Provider provider) {
    this.provider = provider;
  }

  @Override public void initialize(Analytics analytics, ValueMap settings)
      throws IllegalStateException {
    log = analytics.getLogger().newLogger(AMPLITUDE_KEY);

    trackAllPages = settings.getBoolean("trackAllPages", false);
    trackCategorizedPages = settings.getBoolean("trackCategorizedPages", false);
    trackNamedPages = settings.getBoolean("trackNamedPages", false);
    boolean trackSessionEvents = settings.getBoolean("trackSessionEvents", false);
    String apiKey = settings.getString("apiKey");

    amplitude = provider.get();
    amplitude.initialize(analytics.getApplication(), apiKey);
    log.verbose("AmplitudeClient.getInstance().initialize(context, %s);", apiKey);
    amplitude.enableForegroundTracking(analytics.getApplication());
    log.verbose("AmplitudeClient.getInstance().enableForegroundTracking(context);");
    amplitude.trackSessionEvents(trackSessionEvents);
    log.verbose("AmplitudeClient.getInstance().trackSessionEvents(%s);", trackSessionEvents);
  }

  @Override public Void getUnderlyingInstance() {
    return null;
  }

  @Override public String key() {
    return AMPLITUDE_KEY;
  }

  @Override public void identify(IdentifyPayload identify) {
    super.identify(identify);

    String userId = identify.userId();
    amplitude.setUserId(userId);
    log.verbose("AmplitudeClient.getInstance().setUserId(%s);", userId);

    JSONObject traits = identify.traits().toJsonObject();
    amplitude.setUserProperties(traits);
    log.verbose("AmplitudeClient.getInstance().setUserProperties(%s);", traits);
  }

  @Override public void screen(ScreenPayload screen) {
    super.screen(screen);
    if (trackAllPages) {
      event(String.format(VIEWED_EVENT_FORMAT, screen.event()), screen.properties());
    } else if (trackCategorizedPages && !isNullOrEmpty(screen.category())) {
      event(String.format(VIEWED_EVENT_FORMAT, screen.category()), screen.properties());
    } else if (trackNamedPages && !isNullOrEmpty(screen.name())) {
      event(String.format(VIEWED_EVENT_FORMAT, screen.name()), screen.properties());
    }
  }

  @Override public void track(TrackPayload track) {
    super.track(track);
    event(track.event(), track.properties());
  }

  private void event(String name, Properties properties) {
    JSONObject propertiesJSON = properties.toJsonObject();
    amplitude.logEvent(name, propertiesJSON);
    log.verbose("AmplitudeClient.getInstance().logEvent(%s, %s);", name, propertiesJSON);

    double revenue = properties.getDouble("revenue", -1);
    if (revenue == -1) {
      return;
    }
    String productId = properties.getString("productId");
    int quantity = properties.getInt("quantity", 0);
    String receipt = properties.getString("receipt");
    String receiptSignature = properties.getString("receiptSignature");
    amplitude.logRevenue(productId, quantity, revenue, receipt, receiptSignature);
    log.verbose("AmplitudeClient.getInstance().logRevenue(%s, %s, %s, %s, %s);", productId,
        quantity, revenue, receipt, receiptSignature);
  }

  @Override public void flush() {
    super.flush();

    amplitude.uploadEvents();
    log.verbose("AmplitudeClient.getInstance().uploadEvents();");
  }
}
