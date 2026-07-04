package com.helpchat.store;

import com.helpchat.model.Models.AppConfig;

/**
 * Registry of applications that can use the chat widget.
 *
 * Pluggable — pick the backend with `helpchat.storage`
 * (env var HELPCHAT_STORAGE): memory (default) | jdbc | dynamodb.
 * Every client deployment can use its own database; the rest of the
 * service never knows the difference.
 */
public interface AppConfigStore {

    /** @return the app's configuration, or null if the appKey is unknown. */
    AppConfig get(String appKey);
}
